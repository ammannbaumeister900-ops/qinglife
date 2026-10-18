package com.yicai.web.integration;

import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.service.QlRegistrationPolicy;
import com.yicai.web.tools.DatabaseMigration;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real login/security, services and independent MySQL/Redis. Inherits the unchanged legacy receipt/pricing regressions. */
class ParticipantFinanceHttpIT extends OfflineReceiptHttpIT {
    static final String FLAG="qinglife.finance.participant_write_enabled";
    @BeforeEach void closeGate(){flag("false");}
    @AfterEach void restoreGate(){flag("false");}
    void flag(String value){db.update("UPDATE sys_config SET config_value=? WHERE config_key=?",value,FLAG);}
    Map finance(String batch){return ok(call("/life/registration/batch/"+batch+"/finance",HttpMethod.GET,null,admin(),true));}
    Map person(String batch,String id){return ((List<Map>)finance(batch).get("participants")).stream().filter(x->id.equals(x.get("id"))).findFirst().get();}
    Map fact(String batch,String id){return ((List<Map>)finance(batch).get("receipts")).stream().filter(x->id.equals(x.get("id"))).findFirst().get();}
    String participant(String session,boolean multiple){
        flag("true");String b=order(session,account(),multiple);
        assertEquals("participant",db.queryForObject("SELECT settlement_mode FROM ql_registration_batch WHERE id=?",String.class,b));return b;
    }
    Map captureBody(String amount){Map b=receipt(amount);b.put("captureOnly",true);b.put("requestId",id());return b;}
    Map captured(String batch,String amount){return ok(confirm(registration(batch),captureBody(amount)));}
    Map allocationBody(String batch,String receipt,Map... lines){
        return map("operationId",id(),"expectedRevision",finance(batch).get("revision"),"receiptId",receipt,
            "expectedReceiptRevision",fact(batch,receipt).get("revision"),"lines",Arrays.asList(lines),"reason","Synthetic allocation proof");
    }
    Map allocation(String batch,Map b){return call("/life/registration/batch/"+batch+"/receipt-allocations",HttpMethod.POST,b,admin(),true);}
    List<String> allocated(String batch,String receipt,String r,String amount){
        Map result=ok(allocation(batch,allocationBody(batch,receipt,map("registrationId",r,"amount",amount))));
        List<String> ids=new ArrayList<>();for(Map row:(List<Map>)result.get("allocations"))ids.add(row.get("allocationId").toString());return ids;
    }
    Map settlement(String batch,String r,String amount,List<String> ids){
        return map("mode","participant","settlementType","money","operationId",id(),"expectedRevision",person(batch,r).get("revision"),
            "allocationIds",ids,"finalAmount",amount,"differenceConfirmed",false,"reason","Synthetic person confirmation");
    }
    Map settle(String r,Map b){return call("/life/registration/"+r+"/settlement",HttpMethod.PUT,b,admin(),true);}
    int seats(String s){return db.queryForObject("SELECT COUNT(*) FROM ql_registration r LEFT JOIN ql_registration_batch b ON b.id=r.batch_id WHERE r.session_id=? AND "+QlRegistrationPolicy.PAID_SEAT_SQL,Integer.class,s);}
    @Test void gateIsClosedMissingMalformedOrDuplicatedAndDoesNotConvertOldOrders() {
        String s=session(),old=order(s,account(),false);assertEquals("legacy_batch",db.queryForObject("SELECT settlement_mode FROM ql_registration_batch WHERE id=?",String.class,old));
        assertEquals(423,code(confirm(registration(old),captureBody("3800.00"))));
        String b=participant(session(),false),r=registration(b);flag("broken");
        assertEquals(423,code(confirm(r,captureBody("3800.00"))));assertEquals(0,receipts(b));
        db.update("DELETE FROM sys_config WHERE config_key=?",FLAG);
        try{assertEquals(423,code(confirm(r,captureBody("3800.00"))));}
        finally{db.update("INSERT INTO sys_config(config_name,config_key,config_value,config_type) VALUES('Synthetic gate',?,'true','N')",FLAG);}
        db.update("INSERT INTO sys_config(config_name,config_key,config_value,config_type) VALUES('Synthetic duplicate',?,'true','N')",FLAG);
        try{assertEquals(423,code(confirm(r,captureBody("3800.00"))));}finally{db.update("DELETE FROM sys_config WHERE config_key=?",FLAG);db.update("INSERT INTO sys_config(config_name,config_key,config_value,config_type) VALUES('Synthetic gate',?,'true','N')",FLAG);}
        conflict(confirm(registration(old),captureBody("3800.00")));assertEquals("legacy_batch",db.queryForObject("SELECT settlement_mode FROM ql_registration_batch WHERE id=?",String.class,old));
        assertEquals(0,receipts(old));assertEquals(0,receipts(b));
    }
    @Test void newBackofficeSingleRegistrationHasExplicitModeWithoutChangingLegacyCreation() {
        flag("true");String customer=account()[1],s=session();
        success(call("/life/registration",HttpMethod.POST,map("customerId",customer,"sessionId",s,"registrationStatus","pending"),admin(),true));
        Map row=db.queryForMap("SELECT batch_id FROM ql_registration WHERE customer_id=? AND session_id=?",customer,s);
        assertNotNull(row.get("batch_id"));assertEquals("participant",finance(row.get("batch_id").toString()).get("mode"));
        flag("false");String legacy=account()[1],oldSession=session();
        success(call("/life/registration",HttpMethod.POST,map("customerId",legacy,"sessionId",oldSession,"registrationStatus","pending"),admin(),true));
        assertNull(db.queryForMap("SELECT batch_id FROM ql_registration WHERE customer_id=? AND session_id=?",legacy,oldSession).get("batch_id"));
    }
    @Test void captureIsAnIndependentPaidFactWithStableRetryAndCrossActorProtection()throws Exception {
        String b=participant(session(),true),r=registration(b);Map body=captureBody("7600.00"),first=ok(confirm(r,body));
        assertEquals(first,ok(confirm(r,body)));assertEquals(1,receipts(b));assertEquals("paid",db.queryForObject("SELECT status FROM ql_transaction WHERE id=?",String.class,first.get("receiptId")));
        assertEquals(0,seats(db.queryForObject("SELECT session_id FROM ql_registration_batch WHERE id=?",String.class,b)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_receipt_allocation WHERE receipt_id=?",Integer.class,first.get("receiptId")));
        body.put("amount","7500.00");conflict(confirm(r,body));body.put("amount","7600.00");
        String other=actor("life:registration:payment","life:registration:edit","life:registration:query");
        conflict(call("/life/registration/"+r+"/offline-receipt",HttpMethod.POST,body,other,true));assertEquals(1,receipts(b));
        Map second=captureBody("7600.00");second.put("receiptReference",body.get("receiptReference"));conflict(confirm(r,second));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction_status_log WHERE transaction_id=?",Integer.class,first.get("receiptId")));
        Map summary=(Map)finance(b).get("orderSummary");amount(7600,summary.get("grossReceived"));amount(7600,summary.get("unallocatedAmount"));
    }
    @Test void oneReceiptCanFundTwoPeopleAndOnlyConfirmedPersonTakesSeat() {
        String s=session(),b=participant(s,true);List<Map<String,Object>> people=rows(b);String a=people.get(0).get("id").toString(),z=people.get(1).get("id").toString();
        String receipt=captured(b,"7600.00").get("receiptId").toString();
        Map result=ok(allocation(b,allocationBody(b,receipt,map("registrationId",a,"amount","3800.00"),map("registrationId",z,"amount","3800.00"))));
        assertEquals(0,seats(s));List<String> aIds=new ArrayList<>(),zIds=new ArrayList<>();
        assertEquals(2,((List)finance(b).get("allocations")).size());
        for(Map row:(List<Map>)result.get("allocations"))(a.equals(row.get("registrationId"))?aIds:zIds).add(row.get("allocationId").toString());
        success(settle(a,settlement(b,a,"3800.00",aIds)));assertEquals(1,seats(s));
        assertEquals("pending",person(b,z).get("registrationStatus"));assertEquals("unpaid",person(b,z).get("paymentStatus"));
        assertEquals("unpaid",db.queryForObject("SELECT payment_status FROM ql_registration_batch WHERE id=?",String.class,b));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_batch_id=?",Integer.class,b));
        Map aRead=ok(call("/life/registration/"+a,HttpMethod.GET,null,admin(),true)),zRead=ok(call("/life/registration/"+z,HttpMethod.GET,null,admin(),true));
        amount(3800,aRead.get("finalAmount"));assertNull(zRead.get("finalAmount"));assertEquals("participant",aRead.get("settlementMode"));
        success(settle(z,settlement(b,z,"3800.00",zIds)));assertEquals(2,seats(s));assertEquals(1,receipts(b));
        flag("false");assertEquals(2,seats(s));assertEquals(false,finance(b).get("writeEnabled"));
    }
    @Test void multipleActualReceiptsFundOnePersonWithoutCopyingCash() {
        String s=session(),b=participant(s,false),r=registration(b);List<String> funds=new ArrayList<>();
        for(String amount:Arrays.asList("1000.00","2800.00")){String receipt=captured(b,amount).get("receiptId").toString();funds.addAll(allocated(b,receipt,r,amount));}
        Map body=settlement(b,r,"3800.00",funds),first=ok(settle(r,body));
        assertEquals(first,ok(settle(r,body)));assertEquals(2,receipts(b));assertEquals(1,seats(s));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_participant_settlement_funding WHERE settlement_id=?",Integer.class,first.get("settlementId")));
    }
    @Test void allocationRejectsExcessForeignTargetsDuplicatesAndStaleRevisionsAtomically() {
        String b=participant(session(),true),r=registration(b),receipt=captured(b,"100.00").get("receiptId").toString();
        Map excess=allocationBody(b,receipt,map("registrationId",r,"amount","101.00"));conflict(allocation(b,excess));
        String other=participant(session(),false);conflict(allocation(b,allocationBody(b,receipt,map("registrationId",registration(other),"amount","10.00"))));
        String foreignReceipt=captured(other,"10.00").get("receiptId").toString();Map wrongReceipt=allocationBody(b,receipt,map("registrationId",r,"amount","10.00"));wrongReceipt.put("receiptId",foreignReceipt);conflict(allocation(b,wrongReceipt));
        assertEquals(400,code(allocation(b,allocationBody(b,receipt,map("registrationId",r,"amount","10.00"),map("registrationId",r,"amount","10.00")))));
        Map stale=allocationBody(b,receipt,map("registrationId",r,"amount","10.00"));stale.put("expectedReceiptRevision",999);conflict(allocation(b,stale));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_receipt_allocation WHERE receipt_id=?",Integer.class,receipt));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_finance_operation WHERE id=? AND result_json IS NULL",Integer.class,excess.get("operationId")));
        ((Map)((List)excess.get("lines")).get(0)).put("amount","1.00");conflict(allocation(b,excess));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM ql_transaction_status_log WHERE transaction_id=? AND change_reason LIKE '%逐人分配未完成%'",Integer.class,receipt)>0);
        amount(100,fact(b,receipt).get("unallocatedAmount"));assertEquals("paid",fact(b,receipt).get("status"));
    }
    @Test void concurrentAllocationCannotOverdrawAndIdenticalRetryReturnsSameResult()throws Exception {
        String b=participant(session(),true),r=registration(b),receipt=captured(b,"100.00").get("receiptId").toString();
        Map one=allocationBody(b,receipt,map("registrationId",r,"amount","80.00")),two=new LinkedHashMap(one);two.put("operationId",id());
        List<Map> responses=parallel(Arrays.asList(()->allocation(b,one),()->allocation(b,two)));
        assertEquals(1,responses.stream().filter(x->code(x)==200).count());assertEquals(1,responses.stream().filter(x->code(x)==409).count());
        amount(80,fact(b,receipt).get("allocatedAmount"));
        String b2=participant(session(),false),r2=registration(b2),receipt2=captured(b2,"3800.00").get("receiptId").toString();Map same=allocationBody(b2,receipt2,map("registrationId",r2,"amount","3800.00"));
        responses=parallel(Arrays.asList(()->allocation(b2,same),()->allocation(b2,same)));success(responses.get(0));success(responses.get(1));assertEquals(responses.get(0).get("data"),responses.get(1).get("data"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_receipt_allocation WHERE receipt_id=?",Integer.class,receipt2));
        same.put("reason","Synthetic changed reason");conflict(allocation(b2,same));
    }
    @Test void partialFundsAndUnacknowledgedDifferenceCannotConfirmOrOverwriteDue() {
        String s=session(),b=participant(s,false),r=registration(b),receipt=captured(b,"2800.00").get("receiptId").toString();List<String> ids=allocated(b,receipt,r,"2800.00");
        conflict(settle(r,settlement(b,r,"3800.00",ids)));Map difference=settlement(b,r,"2800.00",ids);conflict(settle(r,difference));assertEquals(0,seats(s));
        amount(3800,person(b,r).get("quotedAmount"));difference.put("operationId",id());difference.put("differenceConfirmed",true);assertEquals(400,code(settle(r,difference)));
        difference.put("operationId",id());difference.put("differenceReason","Synthetic approved final difference");Map saved=ok(settle(r,difference));assertEquals(1,seats(s));amount(2800,saved.get("finalAmount"));amount(3800,person(b,r).get("quotedAmount"));
        String audit=db.queryForObject("SELECT result_json FROM ql_finance_operation WHERE id=?",String.class,difference.get("operationId"));assertTrue(audit.contains("Synthetic approved final difference"));
    }
    @Test void finalSeatRacePreservesBothReceiptAndAllocationFacts()throws Exception {
        String s=session();db.update("UPDATE ql_session SET capacity=1 WHERE id=?",s);
        String a=participant(s,false),b=participant(s,false),ar=registration(a),br=registration(b);
        List<String> af=allocated(a,captured(a,"3800.00").get("receiptId").toString(),ar,"3800.00"),bf=allocated(b,captured(b,"3800.00").get("receiptId").toString(),br,"3800.00");
        Map first=settlement(a,ar,"3800.00",af),second=settlement(b,br,"3800.00",bf);
        List<Map> results=parallel(Arrays.asList(()->settle(ar,first),()->settle(br,second)));
        assertEquals(1,results.stream().filter(x->code(x)==200).count());assertEquals(1,seats(s));assertEquals(2,receipts(a)+receipts(b));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_receipt_allocation x JOIN ql_transaction t ON t.id=x.receipt_id WHERE t.session_id=?",Integer.class,s));
    }
    @Test void settlementFailureRollsBackPersonFundingOperationAndSeatButKeepsActualMoney() {
        String s=session(),b=participant(s,false),r=registration(b),receipt=captured(b,"3800.00").get("receiptId").toString();List<String> funds=allocated(b,receipt,r,"3800.00");Map body=settlement(b,r,"3800.00",funds);
        db.execute("CREATE TRIGGER synthetic_participant_funding_failure BEFORE INSERT ON ql_participant_settlement_funding FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic funding failure'");
        try{conflict(settle(r,body));assertEquals(0,seats(s));assertEquals("pending",person(b,r).get("registrationStatus"));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_finance_operation WHERE id=? AND result_json IS NULL",Integer.class,body.get("operationId")));amount(3800,fact(b,receipt).get("allocatedAmount"));}
        finally{db.execute("DROP TRIGGER synthetic_participant_funding_failure");}
        success(settle(r,body));assertEquals(1,seats(s));assertEquals(1,receipts(b));
    }
    @Test void failedMultiPersonAllocationLeavesNoPartialLinesAndSameOperationCanRetry() {
        String b=participant(session(),true),receipt=captured(b,"7600.00").get("receiptId").toString();List<Map<String,Object>> rs=rows(b);
        Map body=allocationBody(b,receipt,map("registrationId",rs.get(0).get("id"),"amount","3800.00"),map("registrationId",rs.get(1).get("id"),"amount","3800.00"));
        db.execute("CREATE TRIGGER synthetic_allocation_failure BEFORE INSERT ON ql_receipt_allocation FOR EACH ROW BEGIN IF NEW.line_no=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic allocation failure'; END IF; END");
        try{conflict(allocation(b,body));amount(0,fact(b,receipt).get("allocatedAmount"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_finance_operation WHERE id=? AND result_json IS NULL",Integer.class,body.get("operationId")));assertEquals(1,receipts(b));}
        finally{db.execute("DROP TRIGGER synthetic_allocation_failure");}
        success(allocation(b,body));amount(7600,fact(b,receipt).get("allocatedAmount"));
    }
    @Test void simultaneousConfirmationReplaysOneRoundAndChangedRequestCannotReuseId()throws Exception {
        String s=session(),b=participant(s,false),r=registration(b);List<String> funds=allocated(b,captured(b,"3800.00").get("receiptId").toString(),r,"3800.00");Map body=settlement(b,r,"3800.00",funds);
        List<Map> out=parallel(Arrays.asList(()->settle(r,body),()->settle(r,body)));success(out.get(0));success(out.get(1));assertEquals(out.get(0).get("data"),out.get(1).get("data"));assertEquals(1,seats(s));
        body.put("finalAmount","3700.00");conflict(settle(r,body));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));
    }
    @Test void cashCannotMixWithPassAndUnsupportedFieldsAreRejected() {
        String b=participant(session(),false),r=registration(b),receipt=captured(b,"3800.00").get("receiptId").toString();
        db.update("UPDATE ql_registration SET settlement_type='pass' WHERE id=?",r);
        conflict(allocation(b,allocationBody(b,receipt,map("registrationId",r,"amount","3800.00"))));
        db.update("UPDATE ql_registration SET settlement_type=NULL WHERE id=?",r);
        List<String> funds=allocated(b,receipt,r,"3800.00");Map body=settlement(b,r,"3800.00",funds);body.put("passAccountIds",Arrays.asList(id(),id()));
        assertEquals(400,code(settle(r,body)));body.remove("passAccountIds");body.put("passUnits",0);assertEquals(400,code(settle(r,body)));
        Map bad=captureBody("10.00");bad.put("operatorId",999);assertEquals(400,code(confirm(r,bad)));assertEquals(1,receipts(b));
    }
    @Test void oldEndpointsCannotMutateParticipantOrdersEvenWithGateEnabled() {
        String b=participant(session(),false),r=registration(b);List<Map<String,Object>> before=rows(b);
        conflict(confirm(r,receipt("3800.00")));
        conflict(call("/life/registration/"+r+"/payment-confirmation",HttpMethod.PUT,map("paymentMethod","cash","amount",3800),admin(),true));
        conflict(call("/life/registration/"+r+"/payment",HttpMethod.PUT,map("paymentStatus","paid","amount",3800,"paymentMethod","cash"),admin(),true));
        conflict(call("/life/registration/batch/"+b+"/payment",HttpMethod.PUT,map("paymentStatus","paid","amount",3800),admin(),true));
        conflict(settle(r,map("settlementType","money","finalAmount",3800)));
        conflict(call("/life/registration/"+r+"/settlement/"+id()+"/revoke",HttpMethod.PUT,map("reason","Synthetic revoke"),admin(),true));
        conflict(call("/life/registration/batch/"+b+"/cancel",HttpMethod.PUT,map("reason","Synthetic cancel"),admin(),true));
        assertEquals(before,rows(b));assertEquals(0,receipts(b));
    }
    String actor(String... permissions) {
        long user=Math.abs(UUID.randomUUID().getMostSignificantBits()%100000000)+1000;String name="m2-"+id().substring(0,20),password=id();
        db.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES(?,?,?,1,'0','0')",user,name,name);
        db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,password,status,del_flag) VALUES(?,0,?,'Synthetic M2 operator','00',?,'0','0')",user,name,SecurityUtils.encryptPassword(password));
        db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",user,user);
        for(String permission:permissions)db.update("INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,menu_id FROM sys_menu WHERE perms=?",user,permission);
        String key=id();cache.setCacheObject("captcha_codes:"+key,"synthetic",2,TimeUnit.MINUTES);
        Map response=http.postForObject("/login",map("username",name,"password",password,"code","synthetic","uuid",key),Map.class);success(response);return ((Map)response.get("data")).get("token").toString();
    }
    @Test void realHttpRequiresBothWritePermissionsAndKeepsFinancePrivateToBackend() {
        String b=participant(session(),false),r=registration(b),one=actor("life:registration:payment"),queryOnly=actor("life:registration:query");
        assertEquals(403,code(call("/life/registration/"+r+"/offline-receipt",HttpMethod.POST,captureBody("3800.00"),one,true)));
        Map receipt=captured(b,"3800.00");Map allocation=allocationBody(b,receipt.get("receiptId").toString(),map("registrationId",r,"amount","3800.00"));
        assertEquals(403,code(call("/life/registration/batch/"+b+"/receipt-allocations",HttpMethod.POST,allocation,one,true)));
        Map settle=settlement(b,r,"3800.00",Collections.singletonList(id()));
        assertEquals(403,code(call("/life/registration/"+r+"/settlement",HttpMethod.PUT,settle,one,true)));
        assertEquals(403,code(call("/life/registration/batch/"+b+"/finance",HttpMethod.GET,null,queryOnly,true)));
        assertEquals(401,code(call("/life/registration/batch/"+b+"/finance",HttpMethod.GET,null,account()[0],false)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_receipt_allocation WHERE receipt_id=?",Integer.class,receipt.get("receiptId")));
    }
}
