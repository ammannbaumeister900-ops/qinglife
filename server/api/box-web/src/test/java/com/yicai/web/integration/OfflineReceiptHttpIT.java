package com.yicai.web.integration;

import com.yicai.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real isolated MySQL/Redis and authenticated HTTP; all receipts are explicitly synthetic.
 * Reuses real-login/signup/quote fixtures and the pricing/reapplication HTTP regressions. */
class OfflineReceiptHttpIT extends RegistrationPricingHttpIT {
    Map<String,Object> receipt(Object amount) {
        return map("paymentMethod","wechat_scan","amount",amount,"receiptReference","TEST-OFFLINE-"+id(),
            "evidence","隔离数据库测试收款，不代表真实到账","receivedConfirmed",true);
    }
    String order(String s,String[] a,boolean multiple) {
        Map saved=ok(post("/registrations",multiple?signup(s,self("Test self"),child("Test child")):signup(s,self("Test self")),a[0]));
        return saved.get("id").toString();
    }
    String registration(String batch){return rows(batch).get(0).get("id").toString();}
    Map confirm(String r,Map bo){return call("/life/registration/"+r+"/offline-receipt",HttpMethod.POST,bo,admin(),true);}
    int code(Map response){assertNotNull(response);return ((Number)response.get("code")).intValue();}
    void success(Map response){assertEquals(200,code(response),response.toString());}
    void conflict(Map response){assertEquals(409,code(response),response.toString());}
    int receipts(String batch){return db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_batch_id=?",Integer.class,batch);}
    int occupied(String s){return db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=? AND registration_status='confirmed' AND payment_status='paid'",Integer.class,s);}
    void pending(String batch){
        for(Map row:rows(batch)){assertEquals("pending",row.get("registration_status"));assertEquals("unpaid",row.get("payment_status"));assertNull(row.get("confirmed_at"));}
        assertEquals("unpaid",db.queryForObject("SELECT payment_status FROM ql_registration_batch WHERE id=?",String.class,batch));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_batch_id=?",Integer.class,batch));
    }
    @Test void verifiedWholeOrderRecordsOneReceiptAndConfirmsEveryParticipant(){
        String[] a=account();completed(a[1]);String s=session(),batch=order(s,a,true),r=registration(batch);Map bo=receipt(6300);
        assertEquals(0,occupied(s));success(confirm(r,bo));
        assertEquals(2,occupied(s));assertEquals(1,receipts(batch));
        for(Map row:rows(batch)){assertEquals("confirmed",row.get("registration_status"));assertEquals("paid",row.get("payment_status"));assertEquals(1L,((Number)row.get("confirmed_by")).longValue());}
        Map fact=db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch);
        assertEquals("paid",fact.get("status"));amount(6300,fact.get("amount"));assertEquals(bo.get("receiptReference"),fact.get("external_transaction_no"));assertEquals(bo.get("evidence"),fact.get("remark"));assertNotNull(fact.get("transaction_at"));
        assertEquals(1L,((Number)fact.get("operator_id")).longValue());
        Map backend=ok(call("/life/registration/"+r,HttpMethod.GET,null,admin(),true));assertEquals("confirmed",backend.get("registrationStatus"));assertEquals("paid",backend.get("batchPaymentStatus"));amount(6300,backend.get("finalAmount"));
        Map app=ok(get("/me/overview",a[0]));assertTrue(((List<Map>)app.get("registrations")).stream().anyMatch(row->r.equals(row.get("registrationId"))&&"paid".equals(row.get("paymentStatus"))));
        Map history=call("/life/registration/"+r+"/offline-receipts",HttpMethod.GET,null,admin(),true);success(history);assertEquals(1,((List)history.get("data")).size());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_transaction_status_log WHERE transaction_id=?",Integer.class,fact.get("id")));
        conflict(confirm(r,bo));assertEquals(1,receipts(batch));assertEquals(fact,db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch));
        conflict(call("/life/registration/batch/"+batch+"/payment",HttpMethod.PUT,map("paymentStatus","unpaid","changeReason","Synthetic revoke attempt"),admin(),true));
        assertEquals(fact,db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch));
    }
    @Test void unconfirmedMismatchKeepsOneCorrectableReceiptAndBlocksNewBookkeeping(){
        String[] a=account();String s=session(),batch=order(s,a,false),r=registration(batch);Map bo=receipt(3700);
        conflict(confirm(r,bo));pending(batch);assertEquals(0,occupied(s));assertEquals(1,receipts(batch));
        Map fact=db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch);assertEquals("pending_confirmation",fact.get("status"));
        conflict(confirm(r,bo));assertEquals(fact,db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch));
        bo.put("amount",3800);conflict(confirm(r,bo));assertEquals(fact,db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch));
        conflict(confirm(r,receipt(3800)));pending(batch);assertEquals(1,receipts(batch));
        assertTrue(((List<Map>)call("/life/registration/"+r+"/offline-receipts",HttpMethod.GET,null,admin(),true).get("data")).stream().anyMatch(x->String.valueOf(x.get("processingMessage")).contains("差额")));
    }
    @Test void concurrentLastSeatKeepsLosingReceiptWithoutOverbooking()throws Exception {
        String s=session();db.update("UPDATE ql_session SET capacity=1 WHERE id=?",s);
        String one=order(s,account(),false),two=order(s,account(),false);admin();
        List<Map> results=parallel(Arrays.asList(()->confirm(registration(one),receipt(3800)),()->confirm(registration(two),receipt(3800))));
        assertEquals(1,results.stream().filter(x->code(x)==200).count());assertEquals(1,results.stream().filter(x->code(x)==409).count());assertEquals(1,occupied(s));
        assertEquals(2,receipts(one)+receipts(two));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE session_id=? AND status='pending_confirmation'",Integer.class,s));
        String loser="unpaid".equals(db.queryForObject("SELECT payment_status FROM ql_registration_batch WHERE id=?",String.class,one))?one:two;pending(loser);
    }
    @Test void sameReceiptConcurrentRetryIsSingleLedgerAndSingleSettlement()throws Exception {
        String s=session(),batch=order(s,account(),true),r=registration(batch);Map bo=receipt(7600);admin();
        List<Map> results=parallel(Arrays.asList(()->confirm(r,bo),()->confirm(r,bo)));
        assertEquals(1,results.stream().filter(x->code(x)==200).count());assertEquals(1,results.stream().filter(x->code(x)==409).count());assertEquals(1,receipts(batch));assertEquals(2,occupied(s));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_batch_id=?",Integer.class,batch));
    }
    List<Map> parallel(List<Callable<Map>> actions)throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{List<Future<Map>> jobs=new ArrayList<>();for(Callable<Map> action:actions)jobs.add(pool.submit(()->{start.await();return action.call();}));start.countDown();List<Map> results=new ArrayList<>();for(Future<Map> job:jobs)results.add(job.get(30,TimeUnit.SECONDS));return results;}
        finally{pool.shutdownNow();}
    }
    @Test void insufficientWholeOrderCapacityRetriesOriginalReceiptAfterCapacityCorrection(){
        String s=session(),batch=order(s,account(),true),r=registration(batch);db.update("UPDATE ql_session SET capacity=1 WHERE id=?",s);Map bo=receipt(7600);
        conflict(confirm(r,bo));pending(batch);assertEquals(0,occupied(s));Map fact=db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch);
        db.update("UPDATE ql_session SET capacity=2 WHERE id=?",s);success(confirm(r,bo));assertEquals(2,occupied(s));assertEquals(1,receipts(batch));
        Map after=db.queryForMap("SELECT * FROM ql_transaction WHERE id=?",fact.get("id"));assertEquals(fact.get("transaction_at"),after.get("transaction_at"));assertEquals(fact.get("operator_id"),after.get("operator_id"));assertEquals(fact.get("remark"),after.get("remark"));
    }
    @Test void partialCancellationAndExistingFinancialConflictsDoNotMutateOtherParticipants(){
        for(String mode:Arrays.asList("partial-cancel","receipt","settlement","pass")){
            String[] a=account();String s=session(),batch=order(s,a,true),r=registration(batch);List<Map<String,Object>> original;
            if(mode.equals("partial-cancel"))db.update("UPDATE ql_registration SET registration_status='cancelled' WHERE id=?",r);
            if(mode.equals("receipt"))db.update("INSERT INTO ql_transaction(id,customer_id,session_id,registration_batch_id,transaction_type,amount,payment_method,status,transaction_at,record_source) VALUES(?,?,?,?,'session',100,'cash','refund_pending',NOW(),'legacy_excel')",id(),a[1],s,batch);
            if(mode.equals("settlement"))db.update("INSERT INTO ql_settlement_log(id,registration_batch_id,quoted_amount,final_amount,settlement_type,operator_id,confirmed_at) VALUES(?,?,7600,7600,'money',1,NOW())",id(),batch);
            if(mode.equals("pass")){String pass=id();db.update("INSERT INTO ql_pass_account(id,customer_id,pass_type,status) VALUES(?,?,'session_card','active')",pass,a[1]);db.update("INSERT INTO ql_pass_ledger(id,pass_account_id,session_id,registration_batch_id,entry_type,quantity_delta,reason,occurred_at) VALUES(?,?,?,?,'consume',-1,'Synthetic debit',NOW())",id(),pass,s,batch);}
            original=rows(batch);conflict(confirm(r,receipt(7600)));assertEquals(original,rows(batch),mode);assertEquals(0,occupied(s));
            assertEquals(mode.equals("receipt")?2:1,receipts(batch));
        }
    }
    @Test void databaseFailureRollsBackWholeOrderButKeepsReceiptForSafeRetry(){
        String s=session(),batch=order(s,account(),true),r=registration(batch);Map bo=receipt(7600);
        db.execute("CREATE TRIGGER synthetic_receipt_allocation_failure BEFORE UPDATE ON ql_transaction FOR EACH ROW BEGIN IF NEW.status='paid' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic rollback verification'; END IF; END");
        try{conflict(confirm(r,bo));pending(batch);assertEquals(0,occupied(s));assertEquals(1,receipts(batch));assertEquals("pending_confirmation",db.queryForObject("SELECT status FROM ql_transaction WHERE registration_batch_id=?",String.class,batch));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_registration_status_log WHERE registration_id IN (SELECT id FROM ql_registration WHERE batch_id=?) AND to_status IN ('confirmed','paid')",Integer.class,batch));
        }finally{db.execute("DROP TRIGGER synthetic_receipt_allocation_failure");}
        success(confirm(r,bo));assertEquals(1,receipts(batch));assertEquals(2,occupied(s));
    }
    @Test void cancelledSessionIsBlockedButDateClosedExistingOrderCanBeCollected(){
        String s=session(),batch=order(s,account(),false);db.update("UPDATE ql_session SET status='cancelled' WHERE id=?",s);
        conflict(confirm(registration(batch),receipt(3800)));pending(batch);assertEquals(1,receipts(batch));
        String closed=session(),existing=order(closed,account(),false);db.update("UPDATE ql_session SET registration_close_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?",closed);
        success(confirm(registration(existing),receipt(3800)));assertEquals(1,occupied(closed));
    }
    @Test void malformedUnverifiedAndUnauthorizedRequestsCannotRecordReceipt(){
        String s=session(),batch=order(s,account(),false),r=registration(batch);
        Map bo=receipt(3800);bo.put("receivedConfirmed",false);assertNotEquals(200,code(confirm(r,bo)));
        bo=receipt(3800);bo.put("evidence",String.join("",Collections.nCopies(501,"x")));assertNotEquals(200,code(confirm(r,bo)));
        bo=receipt(-1);assertNotEquals(200,code(confirm(r,bo)));
        bo=receipt(3800);bo.put("paymentMethod","pass");assertNotEquals(200,code(confirm(r,bo)));
        assertEquals(401,code(call("/life/registration/"+r+"/offline-receipt",HttpMethod.POST,receipt(3800),null,true)));
        String password=id(),username="receipt-limited-"+id().substring(0,8);
        db.update("INSERT INTO sys_user(dept_id,user_name,nick_name,user_type,password,status,del_flag) VALUES(0,?,'Synthetic limited','00',?,'0','0')",username,SecurityUtils.encryptPassword(password));
        String uuid=id();cache.setCacheObject("captcha_codes:"+uuid,"synthetic",2,TimeUnit.MINUTES);
        Map login=http.postForObject("/login",map("username",username,"password",password,"code","synthetic","uuid",uuid),Map.class);success(login);
        String token=String.valueOf(((Map)login.get("data")).get("token"));
        assertEquals(403,code(call("/life/registration/"+r+"/offline-receipt",HttpMethod.POST,receipt(3800),token,true)));
        assertEquals(0,receipts(batch));pending(batch);
        assertNotEquals(200,code(call("/life/registration",HttpMethod.PUT,map("id",r,"registrationStatus","confirmed"),admin(),true)));pending(batch);
    }
    @Test void historicalPaymentEntryCannotDuplicateAnUnallocatedReceipt(){
        String s=session(),batch=order(s,account(),false),r=registration(batch);db.update("UPDATE ql_registration SET registration_status=\'confirmed\' WHERE id=?",r);
        conflict(confirm(r,receipt(3700)));
        conflict(call("/life/registration/"+r+"/payment-confirmation",HttpMethod.PUT,map("paymentMethod","cash","amount",3800,"note","Synthetic conflicting receipt"),admin(),true));
        assertEquals(1,receipts(batch));assertEquals("unpaid",rows(batch).get(0).get("payment_status"));assertEquals(0,occupied(s));
    }
    @Test void referenceCannotBeUsedOnAnotherOrderAndLegacyConfirmedUnpaidCanBeCollected(){
        String s=session(),one=order(s,account(),false),two=order(s,account(),false);Map bo=receipt(3800);
        success(confirm(registration(one),bo));conflict(confirm(registration(two),bo));assertEquals(0,receipts(two));pending(two);
        String[] a=account();String legacySession=session(),legacy=id();
        db.update("INSERT INTO ql_registration(id,customer_id,session_id,registration_status,payment_status,unit_price,registration_source,registered_at) VALUES(?,?,?,'confirmed','unpaid',3800,'web_admin',NOW())",legacy,a[1],legacySession);
        success(confirm(legacy,receipt(3800)));assertEquals(1,occupied(legacySession));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_id=?",Integer.class,legacy));
    }

    Map<String,Object> optionalReceipt(Object amount){return map("paymentMethod","cash","amount",amount,"requestId",id(),"receivedConfirmed",true);}
    Map<String,Object> testImage()throws Exception {
        java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",data);
        return map("mime","image/png","data",Base64.getEncoder().encodeToString(data.toByteArray()));
    }
    @Test void optionalFieldsCanBeOmittedWithoutLosingDuplicateAndFinancialProtection(){
        String s=session(),batch=order(s,account(),false),r=registration(batch);Map bo=optionalReceipt(3800);
        success(confirm(r,bo));assertEquals(1,receipts(batch));assertEquals(1,occupied(s));
        Map fact=db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch);
        assertNull(fact.get("external_transaction_no"));assertNull(fact.get("remark"));assertEquals(bo.get("requestId"),fact.get("receipt_request_id"));
        conflict(confirm(r,bo));conflict(confirm(r,optionalReceipt(3800)));assertEquals(1,receipts(batch));
        conflict(call("/life/registration/batch/"+batch+"/payment",HttpMethod.PUT,map("paymentStatus","unpaid","changeReason","TEST cannot erase receipt"),admin(),true));
        assertEquals(fact,db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch));
    }
    @Test void referenceLessConcurrentRequestsCannotDoubleBookkeep()throws Exception {
        for(boolean same:Arrays.asList(true,false)){
            String s=session(),batch=order(s,account(),false),r=registration(batch);admin();Map first=optionalReceipt(3800),second=same?first:optionalReceipt(3800);
            List<Map> result=parallel(Arrays.asList(()->confirm(r,first),()->confirm(r,second)));
            assertEquals(1,result.stream().filter(x->code(x)==200).count());assertEquals(1,result.stream().filter(x->code(x)==409).count());
            assertEquals(1,receipts(batch));assertEquals(1,occupied(s));
        }
    }
    @Test void privateImagesSurviveCapacityFailureAndResumeWithoutReupload()throws Exception {
        String s=session(),batch=order(s,account(),true),r=registration(batch);Map bo=optionalReceipt(7600);Map image=testImage();
        bo.put("attachments",Arrays.asList(image,image));db.update("UPDATE ql_session SET capacity=1 WHERE id=?",s);
        conflict(confirm(r,bo));pending(batch);assertEquals(1,receipts(batch));
        List<Map> history=(List<Map>)call("/life/registration/"+r+"/offline-receipts",HttpMethod.GET,null,admin(),true).get("data");
        Map record=history.get(0);List<Map> stored=(List<Map>)record.get("attachments");assertEquals(2,stored.size());assertFalse(stored.get(0).containsKey("data"));
        String route="/life/registration/"+r+"/offline-receipt-images/"+stored.get(0).get("id");
        org.springframework.http.HttpHeaders h=new org.springframework.http.HttpHeaders();h.setBearerAuth(admin());
        org.springframework.http.ResponseEntity<byte[]> response=http.exchange(route,HttpMethod.GET,new org.springframework.http.HttpEntity<>(h),byte[].class);
        assertEquals(200,response.getStatusCodeValue());assertEquals("image/png",response.getHeaders().getContentType().toString());assertEquals("no-store",response.getHeaders().getCacheControl());
        assertArrayEquals(Base64.getDecoder().decode(image.get("data").toString()),response.getBody());
        assertEquals(401,code(call(route,HttpMethod.GET,null,null,true)));
        String another=registration(order(session(),account(),false));
        assertEquals(404,code(call(route.replace(r,another),HttpMethod.GET,null,admin(),true)));
        // Keep existing immutable attachments; optional fields remain blank during a receipt-id retry.
        Map retry=optionalReceipt(7600);retry.put("receiptId",record.get("id"));retry.put("receiptReference","");retry.put("evidence","");
        Map altered=testImage();altered.put("data",Base64.getEncoder().encodeToString("invalid".getBytes("UTF-8")));retry.put("attachments",Arrays.asList(altered));
        assertNotEquals(200,code(confirm(r,retry)));pending(batch);
        retry.remove("attachments");db.update("UPDATE ql_session SET capacity=2 WHERE id=?",s);success(confirm(r,retry));
        assertEquals(2,occupied(s));assertEquals(1,receipts(batch));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_offline_receipt_image WHERE transaction_id=?",Integer.class,record.get("id")));
        assertEquals(bo.get("requestId"),db.queryForObject("SELECT receipt_request_id FROM ql_transaction WHERE id=?",String.class,record.get("id")));
    }
    @Test void invalidOrExcessImagesCannotCreateReceiptAndLimitedAdminCannotReadImages()throws Exception {
        String s=session(),batch=order(s,account(),false),r=registration(batch);Map bo=optionalReceipt(3800),image=testImage();
        bo.put("attachments",Arrays.asList(image,image,image,image));assertNotEquals(200,code(confirm(r,bo)));assertEquals(0,receipts(batch));
        bo.put("attachments",Arrays.asList(map("mime","image/png","data",Base64.getEncoder().encodeToString("<script>bad</script>".getBytes("UTF-8")))));assertNotEquals(200,code(confirm(r,bo)));assertEquals(0,receipts(batch));
        bo.put("attachments",Arrays.asList(image));success(confirm(r,bo));
        String imageId=db.queryForObject("SELECT i.id FROM ql_offline_receipt_image i JOIN ql_transaction t ON t.id=i.transaction_id WHERE t.registration_batch_id=?",String.class,batch);
        String username="image-limited-"+id().substring(0,8),password=id();
        db.update("INSERT INTO sys_user(dept_id,user_name,nick_name,user_type,password,status,del_flag) VALUES(0,?,'TEST image limited','00',?,'0','0')",username,SecurityUtils.encryptPassword(password));
        String uuid=id();cache.setCacheObject("captcha_codes:"+uuid,"synthetic",2,TimeUnit.MINUTES);
        Map login=http.postForObject("/login",map("username",username,"password",password,"code","synthetic","uuid",uuid),Map.class);success(login);
        String token=String.valueOf(((Map)login.get("data")).get("token"));
        assertEquals(403,code(call("/life/registration/"+r+"/offline-receipt-images/"+imageId,HttpMethod.GET,null,token,true)));
        assertEquals(403,code(call("/life/registration/"+r+"/offline-receipts",HttpMethod.GET,null,token,true)));
    }

    @Test void legacyReferenceLessReceiptKeepsFinancialProtectionAfterAllocation(){
        String[] a=account();String s=session(),batch=order(s,a,false),r=registration(batch),fact=id();
        db.update("INSERT INTO ql_transaction(id,customer_id,session_id,registration_batch_id,transaction_type,amount,payment_method,status,transaction_at,record_source,operator_id) VALUES(?,?,?,?,'session',3800,'cash','pending_confirmation',NOW(),'operator_entry',1)",fact,a[1],s,batch);
        Map bo=optionalReceipt(3800);bo.put("receiptId",fact);success(confirm(r,bo));assertEquals(1,receipts(batch));
        assertNotNull(db.queryForObject("SELECT receipt_request_id FROM ql_transaction WHERE id=?",String.class,fact));
        conflict(call("/life/registration/batch/"+batch+"/payment",HttpMethod.PUT,map("paymentStatus","unpaid","changeReason","TEST legacy receipt protection"),admin(),true));
    }

    Map pendingReceipt(String batch){return db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",batch);}
    Map resume(String batch,Object amount){
        Map fact=pendingReceipt(batch);Map bo=map("paymentMethod",fact.get("payment_method"),"amount",amount,"receiptId",fact.get("id"),"expectedRevision",fact.get("revision"),"receiptReference",fact.get("external_transaction_no"),"evidence",fact.get("remark"),"receivedConfirmed",true);
        return bo;
    }
    Map finalDifference(Map bo){bo.put("finalSettlementConfirmed",true);bo.put("differenceReason","TEST 已核实本单优惠差额最终结清，无待付尾款");return bo;}
    @Test void finalDifferenceSettlesActualAndPreservesQuoteAndAudit(){
        String s=session(),batch=order(s,account(),false),r=registration(batch);Map bo=finalDifference(receipt(3700));
        success(confirm(r,bo));assertEquals(1,occupied(s));assertEquals(1,receipts(batch));
        Map fact=pendingReceipt(batch);amount(3800,fact.get("expected_amount"));amount(3700,fact.get("amount"));assertEquals("paid",fact.get("status"));
        Map settlement=db.queryForMap("SELECT * FROM ql_settlement_log WHERE registration_batch_id=?",batch);amount(3800,settlement.get("quoted_amount"));amount(3700,settlement.get("final_amount"));
        amount(3800,db.queryForObject("SELECT quoted_amount FROM ql_registration_batch WHERE id=?",Object.class,batch));
        assertEquals(bo.get("differenceReason"),fact.get("difference_reason"));assertNotNull(fact.get("operator_id"));assertNotNull(fact.get("transaction_at"));
        assertTrue(db.queryForList("SELECT change_reason FROM ql_transaction_status_log WHERE transaction_id=?",String.class,fact.get("id")).stream().anyMatch(x->x.contains("-100")));
        conflict(confirm(r,bo));assertEquals(1,receipts(batch));
    }
    @Test void pendingCorrectionChangesSameLedgerWithOriginalValuesAndAttachmentsRetained()throws Exception {
        String s=session(),batch=order(s,account(),false),r=registration(batch);Map bo=receipt(3700);bo.put("attachments",Arrays.asList(testImage()));
        conflict(confirm(r,bo));Map original=pendingReceipt(batch);
        Map retry=resume(batch,3800);assertEquals(400,code(confirm(r,retry)));assertEquals(original,pendingReceipt(batch));
        retry.put("correctionReason","TEST 原金额误填，更正已核实到账为3800");retry.put("evidence","TEST 修正备注");success(confirm(r,retry));
        Map fact=pendingReceipt(batch);assertEquals(original.get("id"),fact.get("id"));assertEquals(original.get("transaction_at"),fact.get("transaction_at"));assertEquals(original.get("operator_id"),fact.get("operator_id"));
        amount(3800,fact.get("amount"));assertEquals(1,receipts(batch));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_offline_receipt_image WHERE transaction_id=?",Integer.class,fact.get("id")));
        Map revision=db.queryForMap("SELECT * FROM ql_offline_receipt_revision WHERE transaction_id=?",fact.get("id"));
        assertTrue(revision.get("before_value").toString().contains("3700"));assertTrue(revision.get("after_value").toString().contains("3800"));assertNotNull(revision.get("operator_id"));assertNotNull(revision.get("changed_at"));
        retry.put("amount",3600);conflict(confirm(r,retry));amount(3800,pendingReceipt(batch).get("amount"));
    }
    @Test void independentOrdersRemainIndependentWhilePendingThenTotalEqualsActual(){
        String s=session(),a=order(s,account(),false),b=order(s,account(),false),ar=registration(a),br=registration(b);
        conflict(confirm(ar,receipt(3700)));pending(a);success(confirm(br,receipt(3800)));assertEquals(1,occupied(s));
        Map retry=finalDifference(resume(a,3700));success(confirm(ar,retry));assertEquals(2,occupied(s));
        assertEquals(1,receipts(a));assertEquals(1,receipts(b));amount(7500,db.queryForObject("SELECT SUM(amount) FROM ql_transaction WHERE session_id=? AND status='paid'",Object.class,s));
        Map foreign=resume(a,3700);conflict(confirm(br,foreign));
    }
    @Test void deferredAndPartialReceiptsCannotMasqueradeAsFinalAndCanContinue(){
        String s=session(),batch=order(s,account(),false),r=registration(batch);Map bo=finalDifference(receipt(3700));bo.put("partialPayment",true);
        conflict(confirm(r,bo));pending(batch);assertEquals(0,occupied(s));
        Map deferred=resume(batch,3700);deferred.put("partialPayment",true);deferred.put("finalSettlementConfirmed",true);deferred.put("differenceReason",bo.get("differenceReason"));deferred.put("defer",true);
        success(confirm(r,deferred));success(confirm(r,deferred));assertEquals(1,receipts(batch));pending(batch);
        Map retry=finalDifference(resume(batch,3700));retry.put("partialPayment",false);retry.put("correctionReason","TEST 重新核实优惠后本单最终结清，非部分付款");
        success(confirm(r,retry));assertEquals(1,receipts(batch));assertEquals(1,occupied(s));
    }
    @Test void differenceRequiresExplicitFinalAcknowledgementAndNonblankReason(){
        for(String mode:Arrays.asList("no-ack","no-reason","blank-reason")){
            String s=session(),batch=order(s,account(),false),r=registration(batch);Map bo=finalDifference(receipt(3700));
            if(mode.equals("no-ack"))bo.put("finalSettlementConfirmed",false);else bo.put("differenceReason",mode.equals("blank-reason")?"  ":null);
            conflict(confirm(r,bo));pending(batch);assertEquals(1,receipts(batch));
            success(confirm(r,finalDifference(resume(batch,3700))));assertEquals(1,receipts(batch));
        }
    }
    @Test void sameMultiPersonOrderSharesOneReceiptAndAtomicDiscountedSettlement(){
        String s=session(),batch=order(s,account(),true),r=registration(batch);Map bo=receipt(3700);conflict(confirm(r,bo));pending(batch);
        String second=rows(batch).stream().map(x->x.get("id").toString()).filter(x->!x.equals(r)).findFirst().get();
        List<Map> history=(List<Map>)call("/life/registration/"+second+"/offline-receipts",HttpMethod.GET,null,admin(),true).get("data");assertEquals(pendingReceipt(batch).get("id"),history.get(0).get("id"));
        Map correction=resume(batch,7500);correction.put("correctionReason","TEST 整单实收误填，核实两人总到账7500");finalDifference(correction);success(confirm(second,correction));
        assertEquals(2,occupied(s));assertEquals(1,receipts(batch));amount(7600,pendingReceipt(batch).get("expected_amount"));amount(7500,pendingReceipt(batch).get("amount"));
        conflict(confirm(r,correction));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_batch_id=?",Integer.class,batch));
    }
    @Test void concurrentCorrectionsAreVersionedAndOnlyOneCanConfirm()throws Exception {
        String s=session(),batch=order(s,account(),false),r=registration(batch);conflict(confirm(r,receipt(3700)));
        Map a=finalDifference(resume(batch,3600));a.put("correctionReason","TEST concurrent A");Map b=finalDifference(resume(batch,3500));b.put("correctionReason","TEST concurrent B");
        List<Map> results=parallel(Arrays.asList(()->confirm(r,a),()->confirm(r,b)));assertEquals(1,results.stream().filter(x->code(x)==200).count());assertEquals(1,results.stream().filter(x->code(x)==409).count());
        assertEquals(1,receipts(batch));assertEquals(1,occupied(s));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_offline_receipt_revision WHERE transaction_id=?",Integer.class,pendingReceipt(batch).get("id")));
    }
    @Test void legacyPendingCanBeReconciledWithoutDeletingHistoryAndStaleCorrectionIsBlocked(){
        String[] account=account();String s=session(),batch=order(s,account,false),r=registration(batch),fact=id();
        db.update("INSERT INTO ql_transaction(id,customer_id,session_id,registration_batch_id,transaction_type,amount,payment_method,status,transaction_at,record_source,operator_id) VALUES(?,?,?,?,'session',3700,'cash','pending_confirmation',NOW(),'operator_entry',1)",fact,account[1],s,batch);
        Map bo=resume(batch,3800);bo.put("correctionReason","TEST 修正历史异常到账误填");bo.put("defer",true);success(confirm(r,bo));pending(batch);assertEquals(1,receipts(batch));
        bo.put("amount",3900);conflict(confirm(r,bo));amount(3800,pendingReceipt(batch).get("amount"));
        success(confirm(r,resume(batch,3800)));assertEquals(1,occupied(s));assertEquals(fact,pendingReceipt(batch).get("id"));
    }

    @Test void correctionAuditFailureRollsBackChangesAndCanRetrySameReceipt(){
        String s=session(),batch=order(s,account(),false),r=registration(batch);conflict(confirm(r,receipt(3700)));Map original=pendingReceipt(batch);
        Map bo=resume(batch,3800);bo.put("correctionReason","TEST 更正误填金额");
        db.execute("CREATE TRIGGER synthetic_receipt_revision_failure BEFORE INSERT ON ql_offline_receipt_revision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEST audit rollback'");
        try{assertNotEquals(200,code(confirm(r,bo)));assertEquals(original,pendingReceipt(batch));pending(batch);assertEquals(0,occupied(s));}
        finally{db.execute("DROP TRIGGER synthetic_receipt_revision_failure");}
        success(confirm(r,bo));assertEquals(1,receipts(batch));assertEquals(1,occupied(s));
    }
    @Test void discountedReceiptKeepsDifferenceBasisAfterCapacityFailureAndSafeRetry(){
        String s=session(),batch=order(s,account(),true),r=registration(batch);db.update("UPDATE ql_session SET capacity=1 WHERE id=?",s);
        Map bo=finalDifference(receipt(7500));conflict(confirm(r,bo));pending(batch);assertEquals(0,occupied(s));
        Map fact=pendingReceipt(batch);amount(7600,fact.get("expected_amount"));amount(7500,fact.get("amount"));assertEquals(bo.get("differenceReason"),fact.get("difference_reason"));
        db.update("UPDATE ql_session SET capacity=2 WHERE id=?",s);success(confirm(r,bo));assertEquals(2,occupied(s));assertEquals(1,receipts(batch));
    }
}
