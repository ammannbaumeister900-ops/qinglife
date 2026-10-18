package com.yicai.web.integration;

import com.yicai.web.tools.DatabaseMigration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Build V1_4_21 in a fresh database, seed explicit legacy facts, then apply/replay only the new increment. */
class ParticipantMigrationIT {
    @Test void incrementPreservesLegacyCashSettlementAndPassLedgerWithoutBackfilling()throws Exception {
        String base=System.getenv("QINGLIFE_TEST_MYSQL_URL");
        assertNotNull(base);assertTrue(base.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_[a-z0-9_]+\\?.*"));
        String name="qinglife_it_m2_upgrade_"+UUID.randomUUID().toString().replace("-","").substring(0,20),prefix=base.substring(0,base.indexOf("/qinglife_it_")),options=base.substring(base.indexOf('?'));
        try(Connection c=DriverManager.getConnection(prefix+"/"+options,"root","");Statement s=c.createStatement()){s.execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4");}
        DriverManagerDataSource ds=new DriverManagerDataSource(prefix+"/"+name+options,"root","");JdbcTemplate db=new JdbcTemplate(ds);
        Path server=Paths.get(System.getProperty("qinglife.migrations")).getParent();
        try(Connection c=ds.getConnection()) {
            db.execute("CREATE TABLE ql_migration_run(name VARCHAR(200) PRIMARY KEY,sha256 CHAR(64) NOT NULL,state VARCHAR(16) NOT NULL,completed_at DATETIME(3)) ENGINE=InnoDB");
            for(Path f:DatabaseMigration.files(server))if(!f.getFileName().toString().startsWith("V1_4_22__")&&!f.getFileName().toString().startsWith("V1_4_23__")&&!f.getFileName().toString().startsWith("V1_4_24__")) {
                ScriptUtils.executeSqlScript(c,new EncodedResource(new FileSystemResource(f),StandardCharsets.UTF_8));
                db.update("INSERT INTO ql_migration_run VALUES(?,?,'complete',NOW(3))",f.getFileName().toString(),DatabaseMigration.checksum(f));
            }
            assertEquals(38,db.queryForObject("SELECT COUNT(*) FROM ql_migration_run",Integer.class));
            String customer=id(),session=id(),batch=id(),registration=id(),receipt=id(),settlement=id(),account=id(),ledger=id();
            db.update("INSERT INTO sys_user(user_id,user_name,nick_name) VALUES(9,'synthetic-upgrade','Synthetic')");
            db.update("INSERT INTO ql_customer(id,customer_no,nickname) VALUES(?,'M2-LEGACY','Synthetic')",customer);
            db.update("INSERT INTO ql_session(id,session_number,name,start_date,end_date,capacity,status) VALUES(?,980001,'Synthetic legacy',CURRENT_DATE(),CURRENT_DATE(),10,'open')",session);
            db.update("INSERT INTO ql_registration_batch(id,session_id,submitted_by_customer_id,source,participant_count,submitted_at,quoted_amount,payable_amount,final_amount,payment_status,settlement_status,settlement_type) VALUES(?,?,?,'manual',1,NOW(),123.45,123.45,123.45,'paid','confirmed','money')",batch,session,customer);
            db.update("INSERT INTO ql_registration(id,batch_id,customer_id,session_id,registration_source,registered_at,registration_status,payment_status) VALUES(?,?,?,?,'manual',NOW(),'confirmed','paid')",registration,batch,customer,session);
            db.update("INSERT INTO ql_transaction(id,customer_id,session_id,registration_batch_id,transaction_type,amount,payment_method,status,transaction_at,record_source,operator_id) VALUES(?,?,?,?,'session',123.45,'cash','paid',NOW(),'operator_entry',9)",receipt,customer,session,batch);
            db.update("INSERT INTO ql_settlement_log(id,registration_batch_id,final_amount,settlement_type,operator_id,confirmed_at) VALUES(?,?,123.45,'money',9,NOW())",settlement,batch);
            db.update("UPDATE ql_registration_batch SET current_settlement_id=? WHERE id=?",settlement,batch);
            db.update("INSERT INTO ql_pass_account(id,customer_id,pass_type,status) VALUES(?,?,'synthetic','active')",account,customer);
            db.update("INSERT INTO ql_pass_ledger(id,pass_account_id,entry_type,quantity_delta,balance_after,reason,occurred_at,operator_id) VALUES(?,?,'grant',5,5,'Synthetic legacy',NOW(),9)",ledger,account);
            Map<String,List<String>> columns=new LinkedHashMap<>();Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
            for(String table:Arrays.asList("ql_registration_batch","ql_registration","ql_transaction","ql_settlement_log","ql_pass_account","ql_pass_ledger")) {
                List<String> names=db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position",String.class,table);
                columns.put(table,names);before.put(table,db.queryForList("SELECT "+String.join(",",names)+" FROM "+table+" ORDER BY id"));
            }
            DatabaseMigration.run(c,server);
            for(String table:columns.keySet())assertEquals(before.get(table),db.queryForList("SELECT "+String.join(",",columns.get(table))+" FROM "+table+" ORDER BY id"),table);
            assertEquals("legacy_batch",db.queryForObject("SELECT settlement_mode FROM ql_registration_batch WHERE id=?",String.class,batch));
            assertEquals("false",db.queryForObject("SELECT config_value FROM sys_config WHERE config_key='qinglife.finance.participant_write_enabled'",String.class));
            for(String table:Arrays.asList("ql_finance_operation","ql_receipt_allocation","ql_participant_settlement_funding","ql_refund_request","ql_refund_reservation","ql_refund_payment","ql_refund_attribution"))assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
            for(String table:Arrays.asList("ql_pass_operator_verification","ql_pass_verification_revocation"))assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
            assertNull(db.queryForObject("SELECT pass_verification_id FROM ql_settlement_log WHERE id=?",String.class,settlement));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 2500 AND 2504",Integer.class));
            DatabaseMigration.run(c,server);assertEquals(41,db.queryForObject("SELECT COUNT(*) FROM ql_migration_run WHERE state='complete'",Integer.class));
        }
    }
    static String id(){return UUID.randomUUID().toString();}
    @Test void passIncrementPreservesPopulatedPhaseTwoCashAllocationFundingAndRefunds()throws Exception {
        String base=System.getenv("QINGLIFE_TEST_MYSQL_URL");
        assertNotNull(base);assertTrue(base.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_[a-z0-9_]+\\?.*"));
        String name="qinglife_it_m2_refund_"+id().replace("-","").substring(0,20),prefix=base.substring(0,base.indexOf("/qinglife_it_")),options=base.substring(base.indexOf('?'));
        try(Connection c=DriverManager.getConnection(prefix+"/"+options,"root","");Statement s=c.createStatement()){s.execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4");}
        DriverManagerDataSource ds=new DriverManagerDataSource(prefix+"/"+name+options,"root","");JdbcTemplate db=new JdbcTemplate(ds);
        Path server=Paths.get(System.getProperty("qinglife.migrations")).getParent();
        try(Connection c=ds.getConnection()) {
            db.execute("CREATE TABLE ql_migration_run(name VARCHAR(200) PRIMARY KEY,sha256 CHAR(64) NOT NULL,state VARCHAR(16) NOT NULL,completed_at DATETIME(3)) ENGINE=InnoDB");
            for(Path f:DatabaseMigration.files(server))if(!f.getFileName().toString().startsWith("V1_4_24__")) {
                ScriptUtils.executeSqlScript(c,new EncodedResource(new FileSystemResource(f),StandardCharsets.UTF_8));
                db.update("INSERT INTO ql_migration_run VALUES(?,?,'complete',NOW(3))",f.getFileName().toString(),DatabaseMigration.checksum(f));
            }
            assertEquals(40,db.queryForObject("SELECT COUNT(*) FROM ql_migration_run",Integer.class));
            String customer=id(),session=id(),batch=id(),registration=id(),receipt=id(),round=id(),operation=id(),allocation=id();
            db.update("INSERT INTO sys_user(user_id,user_name,nick_name) VALUES(9,'synthetic-refund-upgrade','Synthetic')");
            db.update("INSERT INTO ql_customer(id,customer_no,nickname) VALUES(?,'M2-REFUND','Synthetic')",customer);
            db.update("INSERT INTO ql_session(id,session_number,name,start_date,end_date,capacity,status) VALUES(?,980002,'Synthetic participant',CURRENT_DATE(),CURRENT_DATE(),10,'open')",session);
            db.update("INSERT INTO ql_registration_batch(id,session_id,submitted_by_customer_id,source,participant_count,submitted_at,quoted_amount,payable_amount,settlement_mode) VALUES(?,?,?,'manual',1,NOW(),123.45,123.45,'participant')",batch,session,customer);
            db.update("INSERT INTO ql_registration(id,batch_id,customer_id,session_id,registration_source,registered_at,registration_status,payment_status,unit_price,final_amount,settlement_type,settlement_status) VALUES(?,?,?,?,'manual',NOW(),'confirmed','paid',123.45,123.45,'money','confirmed')",registration,batch,customer,session);
            db.update("INSERT INTO ql_transaction(id,customer_id,session_id,registration_batch_id,transaction_type,amount,payment_method,status,transaction_at,record_source,operator_id) VALUES(?,?,?,?,'session',123.45,'cash','paid',NOW(),'operator_entry',9)",receipt,customer,session,batch);
            db.update("INSERT INTO ql_settlement_log(id,registration_id,final_amount,settlement_type,operator_id,confirmed_at) VALUES(?,?,123.45,'money',9,NOW())",round,registration);
            db.update("UPDATE ql_registration SET current_settlement_id=? WHERE id=?",round,registration);
            db.update("INSERT INTO ql_finance_operation(id,action,request_hash,target_id,operator_id,result_json) VALUES(?,'allocate',?, ?,9,'{}')",operation,String.join("",Collections.nCopies(64,"a")),batch);
            db.update("INSERT INTO ql_receipt_allocation(id,operation_id,line_no,receipt_id,registration_id,amount,entry_type,reason) VALUES(?,?,1,?,?,123.45,'allocate','Synthetic preserved allocation')",allocation,operation,receipt,registration);
            db.update("INSERT INTO ql_participant_settlement_funding VALUES(?,?)",round,allocation);
            String request=id(),payment=id(),requestOp=id(),paymentOp=id();
            for(String op:Arrays.asList(requestOp,paymentOp))db.update("INSERT INTO ql_finance_operation(id,action,request_hash,target_id,operator_id,result_json) VALUES(?,'refund',?,?,9,'{}')",op,String.join("",Collections.nCopies(64,"b")),registration);
            db.update("INSERT INTO ql_refund_request(id,registration_id,operation_id,requested_amount,status,reason,requested_by) VALUES(?,?,?,100,'completed','Synthetic preserved refund',9)",request,registration,requestOp);
            db.update("INSERT INTO ql_refund_reservation VALUES(?,?,100)",request,allocation);
            db.update("INSERT INTO ql_refund_payment(id,request_id,operation_id,actual_amount,payment_method,evidence,occurred_at,status) VALUES(?,?,?,100,'cash','Synthetic offline refund',NOW(),'applied')",payment,request,paymentOp);
            db.update("INSERT INTO ql_refund_attribution VALUES(?,?,100)",payment,allocation);
            List<String> tables=Arrays.asList("ql_registration_batch","ql_registration","ql_transaction","ql_settlement_log","ql_finance_operation","ql_receipt_allocation","ql_participant_settlement_funding","ql_refund_request","ql_refund_reservation","ql_refund_payment","ql_refund_attribution");
            Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
            Map<String,List<String>> columns=new LinkedHashMap<>();
            for(String table:tables){List<String> names=db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position",String.class,table);columns.put(table,names);before.put(table,db.queryForList("SELECT "+String.join(",",names)+" FROM "+table));}
            DatabaseMigration.run(c,server);DatabaseMigration.run(c,server);
            for(String table:tables)assertEquals(before.get(table),db.queryForList("SELECT "+String.join(",",columns.get(table))+" FROM "+table),table);
            for(String table:Arrays.asList("ql_pass_operator_verification","ql_pass_verification_revocation"))assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
            assertNull(db.queryForObject("SELECT pass_verification_id FROM ql_settlement_log WHERE id=?",String.class,round));
            assertEquals("false",db.queryForObject("SELECT config_value FROM sys_config WHERE config_key='qinglife.finance.participant_write_enabled'",String.class));
            assertEquals(41,db.queryForObject("SELECT COUNT(*) FROM ql_migration_run WHERE state='complete'",Integer.class));
        }
    }
}
