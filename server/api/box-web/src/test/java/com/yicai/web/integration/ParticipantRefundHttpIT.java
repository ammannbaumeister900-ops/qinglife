package com.yicai.web.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real authenticated HTTP + isolated MySQL/Redis; inherits phase-1 cash and unchanged legacy regressions. */
class ParticipantRefundHttpIT extends ParticipantFinanceHttpIT {
    class Cash {
        String s,b,r;String[] account;List<String> allocations=new ArrayList<>();Map settlement;
    }
    Cash cash(String... amounts) {
        Cash c=new Cash();c.s=session();c.account=account();flag("true");c.b=order(c.s,c.account,false);c.r=registration(c.b);
        for(String amount:amounts)c.allocations.addAll(allocated(c.b,captured(c.b,amount).get("receiptId").toString(),c.r,amount));
        c.settlement=settlement(c.b,c.r,"3800.00",c.allocations);success(settle(c.r,c.settlement));return c;
    }
    Map refundBody(Cash c,String amount) {
        return map("operationId",id(),"expectedRevision",person(c.b,c.r).get("revision"),"requestedAmount",amount,
            "allocations",Collections.singletonList(map("allocationId",c.allocations.get(0),"amount",amount)),"reason","Synthetic refund request");
    }
    Map request(String r,Map body){return call("/life/registration/"+r+"/refund-requests",HttpMethod.POST,body,admin(),true);}
    String requested(Cash c,String amount){return ok(request(c.r,refundBody(c,amount))).get("requestId").toString();}
    Map action(String request){return map("operationId",id(),"expectedRevision",db.queryForObject("SELECT revision FROM ql_refund_request WHERE id=?",Integer.class,request),"reason","Synthetic operator action");}
    Map transition(Cash c,String request,String action,Map body){return call("/life/registration/"+c.r+"/refund-requests/"+request+"/"+action,HttpMethod.PUT,body,admin(),true);}
    Map paymentBody(String request,String amount){Map out=action(request);out.putAll(map("actualAmount",amount,"paymentMethod","transfer","externalReference","SYNTHETIC-REFUND-"+id(),"evidence","Synthetic offline outflow evidence","occurredAt","2026-10-18T08:00:00+08:00"));return out;}
    Map payment(String r,String request,Map body){return call("/life/registration/"+r+"/refund-requests/"+request+"/payments",HttpMethod.POST,body,admin(),true);}
    Map cancelBody(Cash c){return map("operationId",id(),"expectedRevision",person(c.b,c.r).get("revision"),"reason","Synthetic person-only cancellation");}
    Map cancelPerson(Cash c,Map body){return call("/life/registration/"+c.r+"/cancel",HttpMethod.PUT,body,admin(),true);}
    int payments(Cash c){return db.queryForObject("SELECT COUNT(*) FROM ql_refund_payment p JOIN ql_refund_request r ON r.id=p.request_id WHERE r.registration_id=?",Integer.class,c.r);}
    Map reapply(Cash c){return post("/registrations",signup(c.s,self("Synthetic reapplication")),c.account[0]);}
    @Test void multiplePartialRefundsThenFullRefundPreserveOriginalCashRoundAndSeat() {
        Cash c=cash("3800.00");Map original=db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",c.b);
        String round=db.queryForObject("SELECT current_settlement_id FROM ql_registration WHERE id=?",String.class,c.r);
        for(String amount:Arrays.asList("500.00","800.00","2500.00")) {
            String req=requested(c,amount);Map body=paymentBody(req,amount);assertEquals("applied",ok(payment(c.r,req,body)).get("status"));
            assertEquals(1,seats(c.s));assertEquals("confirmed",person(c.b,c.r).get("registrationStatus"));assertEquals("paid",person(c.b,c.r).get("paymentStatus"));
        }
        amount(3800,person(c.b,c.r).get("refundedAmount"));amount(0,person(c.b,c.r).get("refundableAmount"));amount(0,person(c.b,c.r).get("netReceived"));
        assertEquals("full",person(c.b,c.r).get("refundState"));assertEquals(original,db.queryForMap("SELECT * FROM ql_transaction WHERE registration_batch_id=?",c.b));
        assertEquals(round,db.queryForObject("SELECT current_settlement_id FROM ql_registration WHERE id=?",String.class,c.r));
        assertEquals(3,payments(c));conflict(request(c.r,refundBody(c,"0.01")));
    }
    @Test void reservationsCountTowardLimitAndReleaseOnlyWithExplicitNoOutflowAssertion() {
        Cash c=cash("3800.00");String req=requested(c,"3000.00");amount(800,person(c.b,c.r).get("refundableAmount"));conflict(request(c.r,refundBody(c,"900.00")));
        success(transition(c,req,"processing",action(req)));Map unsure=ok(transition(c,req,"fail",action(req)));assertEquals("reconciliation_required",unsure.get("status"));
        amount(3000,person(c.b,c.r).get("refundReserved"));amount(0,person(c.b,c.r).get("refundableAmount"));assertEquals(1,seats(c.s));
        Map confirmed=action(req);confirmed.put("noActualPaymentConfirmed",true);assertEquals("failed",ok(transition(c,req,"fail",confirmed)).get("status"));
        amount(0,person(c.b,c.r).get("refundReserved"));amount(3800,person(c.b,c.r).get("refundableAmount"));assertEquals(0,payments(c));
        String second=requested(c,"3800.00");Map cancel=action(second);cancel.put("noActualPaymentConfirmed",true);assertEquals("cancelled",ok(transition(c,second,"cancel",cancel)).get("status"));
        amount(3800,person(c.b,c.r).get("refundableAmount"));
    }
    @Test void requestAndPaymentReplaysNeverDuplicateMoneyOrAcceptChangedPayload()throws Exception {
        Cash c=cash("3800.00");Map body=refundBody(c,"1200.00");
        List<Map> results=parallel(Arrays.asList(()->request(c.r,body),()->request(c.r,body)));assertEquals(ok(results.get(0)),ok(results.get(1)));
        String req=ok(results.get(0)).get("requestId").toString();Map paid=paymentBody(req,"1200.00");
        results=parallel(Arrays.asList(()->payment(c.r,req,paid),()->payment(c.r,req,paid)));assertEquals(ok(results.get(0)),ok(results.get(1)));
        assertEquals(1,payments(c));amount(1200,person(c.b,c.r).get("refundedAmount"));amount(2600,person(c.b,c.r).get("refundableAmount"));
        paid.put("actualAmount","1300.00");conflict(payment(c.r,req,paid));body.put("requestedAmount","1300.00");((Map)((List)body.get("allocations")).get(0)).put("amount","1300.00");conflict(request(c.r,body));
    }
    @Test void concurrentRequestsCannotOverReserveAndARequestHasOnlyOneActualPayment()throws Exception {
        Cash c=cash("3800.00");Map first=refundBody(c,"3000.00"),second=new LinkedHashMap(first);second.put("operationId",id());
        List<Map> results=parallel(Arrays.asList(()->request(c.r,first),()->request(c.r,second)));
        assertEquals(1,results.stream().filter(x->code(x)==200).count());assertEquals(1,results.stream().filter(x->code(x)==409).count());amount(3000,person(c.b,c.r).get("refundReserved"));
        String req=ok(results.stream().filter(x->code(x)==200).findFirst().get()).get("requestId").toString();Map p=paymentBody(req,"3000.00"),q=new LinkedHashMap(p);q.put("operationId",id());q.put("externalReference","SYNTHETIC-"+id());
        results=parallel(Arrays.asList(()->payment(c.r,req,p),()->payment(c.r,req,q)));assertEquals(1,results.stream().filter(x->code(x)==200).count());assertEquals(1,results.stream().filter(x->code(x)==409).count());
        assertEquals(1,payments(c));amount(800,person(c.b,c.r).get("refundableAmount"));assertEquals(1,seats(c.s));
    }
    @Test void attributionFailureKeepsActualFactAndReservationsThenExactRetryAppliesOnce() {
        Cash c=cash("1000.00","2800.00");Map body=refundBody(c,"2000.00");
        body.put("allocations",Arrays.asList(map("allocationId",c.allocations.get(0),"amount","1000.00"),map("allocationId",c.allocations.get(1),"amount","1000.00")));
        String req=ok(request(c.r,body)).get("requestId").toString();Map actual=paymentBody(req,"2000.00");
        String fail=c.allocations.stream().sorted().skip(1).findFirst().get();
        db.execute("CREATE TRIGGER synthetic_refund_attribution_failure BEFORE INSERT ON ql_refund_attribution FOR EACH ROW BEGIN IF NEW.allocation_id='"+fail+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic attribution failure'; END IF; END");
        try {
            Map saved=ok(payment(c.r,req,actual));assertEquals("recorded_unreconciled",saved.get("status"));assertEquals(1,payments(c));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_refund_attribution WHERE payment_id=?",Integer.class,saved.get("paymentId")));
            assertEquals("reconciliation_required",person(c.b,c.r).get("refundState"));amount(0,person(c.b,c.r).get("refundableAmount"));
            amount(2000,person(c.b,c.r).get("refundedAmount"));assertEquals(1,seats(c.s));conflict(request(c.r,refundBody(c,"100.00")));
            Map close=action(req);close.put("noActualPaymentConfirmed",true);conflict(transition(c,req,"cancel",close));
        } finally {db.execute("DROP TRIGGER synthetic_refund_attribution_failure");}
        assertEquals("applied",ok(payment(c.r,req,actual)).get("status"));assertEquals(1,payments(c));amount(1800,person(c.b,c.r).get("refundableAmount"));amount(0,person(c.b,c.r).get("refundReserved"));
    }
    @Test void captureDatabaseFailureRollsBackFactAndExactOriginalOperationCanRetry() {
        Cash c=cash("3800.00");String req=requested(c,"1000.00");Map body=paymentBody(req,"1000.00");
        db.execute("CREATE TRIGGER synthetic_refund_capture_failure BEFORE INSERT ON ql_refund_payment FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic payment failure'");
        try{conflict(payment(c.r,req,body));assertEquals(0,payments(c));amount(1000,person(c.b,c.r).get("refundReserved"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_finance_operation WHERE id=? AND result_json IS NULL",Integer.class,body.get("operationId")));}
        finally{db.execute("DROP TRIGGER synthetic_refund_capture_failure");}
        Map changed=new LinkedHashMap(body);changed.put("actualAmount","900.00");conflict(payment(c.r,req,changed));assertEquals("applied",ok(payment(c.r,req,body)).get("status"));assertEquals(1,payments(c));
    }
    @Test void realAmountDiscrepancyIsPreservedAndNeverPretendsRefundCompleted() {
        Cash c=cash("3800.00");String req=requested(c,"1000.00");Map body=paymentBody(req,"1100.00");
        Map saved=ok(payment(c.r,req,body));assertEquals("recorded_unreconciled",saved.get("status"));amount(1100,person(c.b,c.r).get("refundedAmount"));
        assertEquals("reconciliation_required",person(c.b,c.r).get("refundState"));amount(0,person(c.b,c.r).get("refundableAmount"));assertEquals(1,payments(c));
        Map cancel=action(req);cancel.put("noActualPaymentConfirmed",true);conflict(transition(c,req,"fail",cancel));
        Map corrected=new LinkedHashMap(body);corrected.put("actualAmount","1000.00");conflict(payment(c.r,req,corrected));success(payment(c.r,req,body));assertEquals(1,payments(c));
        success(cancelPerson(c,cancelBody(c)));conflict(reapply(c));assertEquals(0,seats(c.s));
    }
    @Test void duplicateExternalReceiptAcrossRequestsDoesNotCreateSecondFact() {
        Cash c=cash("3800.00");String first=requested(c,"1000.00");Map p=paymentBody(first,"1000.00");success(payment(c.r,first,p));
        String second=requested(c,"1000.00");Map q=paymentBody(second,"1000.00");q.put("externalReference",p.get("externalReference"));
        conflict(payment(c.r,second,q));assertEquals(1,payments(c));amount(1000,person(c.b,c.r).get("refundedAmount"));amount(1000,person(c.b,c.r).get("refundReserved"));
    }
    @Test void cancelAndRefundOnePersonLeaveCompanionFundsAndSeatUntouched() {
        String s=session(),b=participant(s,true);List<Map<String,Object>> people=rows(b);String a=people.get(0).get("id").toString(),z=people.get(1).get("id").toString();
        String receipt=captured(b,"7600.00").get("receiptId").toString();Map split=ok(allocation(b,allocationBody(b,receipt,map("registrationId",a,"amount","3800.00"),map("registrationId",z,"amount","3800.00"))));
        Map<String,String> allocations=new HashMap<>();for(Map line:(List<Map>)split.get("allocations"))allocations.put(line.get("registrationId").toString(),line.get("allocationId").toString());
        success(settle(a,settlement(b,a,"3800.00",Collections.singletonList(allocations.get(a)))));success(settle(z,settlement(b,z,"3800.00",Collections.singletonList(allocations.get(z)))));
        Cash c=new Cash();c.s=s;c.b=b;c.r=a;c.allocations.add(allocations.get(a));Map other=db.queryForMap("SELECT * FROM ql_registration WHERE id=?",z);
        Map cancel=cancelBody(c);Map done=ok(cancelPerson(c,cancel));assertEquals(done,ok(cancelPerson(c,cancel)));assertEquals(1,seats(s));assertEquals(0,payments(c));
        String req=requested(c,"3800.00");success(payment(a,req,paymentBody(req,"3800.00")));assertEquals(1,seats(s));
        assertEquals(other,db.queryForMap("SELECT * FROM ql_registration WHERE id=?",z));assertEquals("cancelled",person(b,a).get("registrationStatus"));assertEquals(1,receipts(b));amount(3800,((Map)finance(b).get("orderSummary")).get("netReceived"));
    }
    @Test void cancelThenFullyRefundAllowsFreshReapplicationWithoutRestoringOldRow() {
        Cash c=cash("3800.00");success(cancelPerson(c,cancelBody(c)));assertEquals(0,seats(c.s));conflict(reapply(c));
        String req=requested(c,"1000.00");success(payment(c.r,req,paymentBody(req,"1000.00")));conflict(reapply(c));
        req=requested(c,"2800.00");Map payment=paymentBody(req,"2800.00");success(payment(c.r,req,payment));Map old=db.queryForMap("SELECT * FROM ql_registration WHERE id=?",c.r);
        Map newer=ok(reapply(c));assertNotEquals(c.b,newer.get("id"));assertEquals("pending",person(newer.get("id").toString(),registration(newer.get("id").toString())).get("registrationStatus"));
        success(settle(c.r,c.settlement));success(payment(c.r,req,payment));assertEquals(old,db.queryForMap("SELECT * FROM ql_registration WHERE id=?",c.r));assertEquals(0,seats(c.s));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE customer_id=? AND session_id=? AND active_registration_slot=1",Integer.class,c.account[1],c.s));
        assertEquals(400,code(reapply(c))); // Existing active-registration rule retains its original error code.
    }
    @Test void pendingCancellationWithoutMoneyCanReapplyAndOldConfirmCannotReviveIt() {
        Cash c=new Cash();c.s=session();c.account=account();flag("true");c.b=order(c.s,c.account,false);c.r=registration(c.b);
        success(cancelPerson(c,cancelBody(c)));Map newer=ok(reapply(c));assertNotEquals(c.b,newer.get("id"));assertEquals(0,seats(c.s));
        conflict(call("/life/registration/"+c.r+"/payment-confirmation",HttpMethod.PUT,map("paymentMethod","cash","amount",3800),admin(),true));
        assertEquals("cancelled",person(c.b,c.r).get("registrationStatus"));
    }
    @Test void pendingRefundOrActualRefundCannotBeBypassedByLateSettlement() {
        Cash c=new Cash();c.s=session();c.account=account();flag("true");c.b=order(c.s,c.account,false);c.r=registration(c.b);
        c.allocations.addAll(allocated(c.b,captured(c.b,"3800.00").get("receiptId").toString(),c.r,"3800.00"));
        String req=requested(c,"1000.00");conflict(settle(c.r,settlement(c.b,c.r,"3800.00",c.allocations)));
        success(payment(c.r,req,paymentBody(req,"1000.00")));conflict(settle(c.r,settlement(c.b,c.r,"3800.00",c.allocations)));assertEquals(0,seats(c.s));
    }
    @Test void staleForeignDuplicateAndActorSpoofedRefundsAreRejected() {
        Cash c=cash("3800.00"),other=cash("3800.00");Map body=refundBody(c,"100.00");body.put("expectedRevision",999);conflict(request(c.r,body));
        body=refundBody(c,"100.00");((Map)((List)body.get("allocations")).get(0)).put("allocationId",other.allocations.get(0));conflict(request(c.r,body));
        body=refundBody(c,"200.00");body.put("allocations",Arrays.asList(map("allocationId",c.allocations.get(0),"amount","100.00"),map("allocationId",c.allocations.get(0),"amount","100.00")));assertEquals(400,code(request(c.r,body)));
        body=refundBody(c,"100.00");body.put("operatorId",9);assertEquals(400,code(request(c.r,body)));
        String req=requested(c,"100.00");conflict(payment(other.r,req,paymentBody(req,"100.00")));assertEquals(0,payments(c));assertEquals(2,seats(c.s)+seats(other.s));
    }
    @Test void independentPermissionsDenyOldPaymentEditRolesAndAppTokens() {
        Cash c=cash("3800.00");String old=actor("life:registration:payment","life:registration:edit"),refund=actor("life:registration:refund"),cancel=actor("life:registration:cancel");
        Map body=refundBody(c,"1000.00");String base="/life/registration/"+c.r;
        assertEquals(403,code(call(base+"/refund-requests",HttpMethod.POST,body,old,true)));assertEquals(403,code(call(base+"/cancel",HttpMethod.PUT,cancelBody(c),old,true)));
        String req=ok(call(base+"/refund-requests",HttpMethod.POST,body,refund,true)).get("requestId").toString();
        assertEquals(403,code(call(base+"/refund-requests/"+req+"/payments",HttpMethod.POST,paymentBody(req,"1000.00"),cancel,true)));
        assertEquals(403,code(call(base+"/cancel",HttpMethod.PUT,cancelBody(c),refund,true)));
        assertEquals(401,code(call(base+"/refund-requests",HttpMethod.POST,refundBody(c,"100.00"),c.account[0],false)));
        Map another=refundBody(c,"100.00");another.put("operationId",body.get("operationId"));conflict(call(base+"/refund-requests",HttpMethod.POST,another,admin(),true));
        success(call(base+"/cancel",HttpMethod.PUT,cancelBody(c),cancel,true));assertEquals(0,payments(c));
    }
    @Test void gateOffBlocksNewRefundCancelAndRetriesButPreservesReadsAndLegacyBehavior() {
        Cash c=cash("3800.00");Map body=refundBody(c,"1000.00");String req=ok(request(c.r,body)).get("requestId").toString();Map cancel=cancelBody(c);
        flag("false");assertEquals(423,code(request(c.r,body)));assertEquals(423,code(payment(c.r,req,paymentBody(req,"1000.00"))));assertEquals(423,code(cancelPerson(c,cancel)));
        assertEquals("requested",person(c.b,c.r).get("refundState"));assertEquals(1,seats(c.s));assertEquals(false,finance(c.b).get("writeEnabled"));
        String legacy=order(session(),account(),false);flag("true");conflict(call("/life/registration/"+registration(legacy)+"/cancel",HttpMethod.PUT,map("operationId",id(),"expectedRevision",0,"reason","Synthetic legacy boundary"),admin(),true));
        conflict(request(registration(legacy),refundBody(c,"100.00")));assertEquals("legacy_batch",finance(legacy).get("mode"));
    }
    @Test void attendanceAndStaleRevisionPreventCancellationWithoutHalfState() {
        Cash c=cash("3800.00");Map stale=cancelBody(c);stale.put("expectedRevision",999);conflict(cancelPerson(c,stale));assertEquals(1,seats(c.s));
        db.update("INSERT INTO ql_participation(id,registration_id,customer_id,session_id,attendance_status) VALUES(?,?,?,?,'completed')",id(),c.r,c.account[1],c.s);
        assertEquals(400,code(cancelPerson(c,cancelBody(c))));assertEquals("confirmed",person(c.b,c.r).get("registrationStatus"));assertEquals(1,seats(c.s));
    }
    @Test void reservationInsertFailureRollsBackRequestAndAllLinesThenSameOperationRetries() {
        Cash c=cash("3800.00");Map body=refundBody(c,"1000.00");
        db.execute("CREATE TRIGGER synthetic_reservation_failure BEFORE INSERT ON ql_refund_reservation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic reservation failure'");
        try{conflict(request(c.r,body));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_refund_request WHERE registration_id=?",Integer.class,c.r));amount(3800,person(c.b,c.r).get("refundableAmount"));}
        finally{db.execute("DROP TRIGGER synthetic_reservation_failure");}
        success(request(c.r,body));amount(2800,person(c.b,c.r).get("refundableAmount"));
    }
    @Test void concurrentCancellationAndRebookingNeverCreateTwoActiveRows()throws Exception {
        Cash c=new Cash();c.s=session();c.account=account();flag("true");c.b=order(c.s,c.account,false);c.r=registration(c.b);
        Map body=cancelBody(c);List<Map> results=parallel(Arrays.asList(()->cancelPerson(c,body),()->reapply(c)));
        success(results.get(0));if(code(results.get(1))!=200)success(reapply(c));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE customer_id=? AND session_id=? AND active_registration_slot=1",Integer.class,c.account[1],c.s));assertEquals("cancelled",person(c.b,c.r).get("registrationStatus"));assertEquals(0,seats(c.s));
    }
    @Test void distinctReservedRefundsCanCompleteConcurrentlyWithoutExceedingOriginalAllocation()throws Exception {
        Cash c=cash("3800.00");String first=requested(c,"1000.00"),second=requested(c,"2800.00");
        Map p=paymentBody(first,"1000.00"),q=paymentBody(second,"2800.00");
        List<Map> results=parallel(Arrays.asList(()->payment(c.r,first,p),()->payment(c.r,second,q)));
        assertEquals("applied",ok(results.get(0)).get("status"));assertEquals("applied",ok(results.get(1)).get("status"));
        amount(3800,person(c.b,c.r).get("refundedAmount"));amount(0,person(c.b,c.r).get("refundReserved"));assertEquals(2,payments(c));assertEquals(1,seats(c.s));
    }
    @Test void cancellationAuditFailureRollsBackSeatAndExactRetryCancelsOnce() {
        Cash c=cash("3800.00");Map body=cancelBody(c);
        db.execute("CREATE TRIGGER synthetic_cancel_audit_failure BEFORE INSERT ON ql_registration_status_log FOR EACH ROW BEGIN IF NEW.to_status='cancelled' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic cancellation audit failure'; END IF; END");
        try{conflict(cancelPerson(c,body));assertEquals("confirmed",person(c.b,c.r).get("registrationStatus"));assertEquals(1,seats(c.s));}
        finally{db.execute("DROP TRIGGER synthetic_cancel_audit_failure");}
        success(cancelPerson(c,body));assertEquals(0,seats(c.s));assertEquals(0,payments(c));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration_status_log WHERE registration_id=? AND to_status='cancelled'",Integer.class,c.r));
    }
    @Test void cancelledSeatCanBeTakenByOneOtherPersonWithoutRestoringCancelledFunding()throws Exception {
        Cash c=cash("3800.00");String other=participant(c.s,false),r=registration(other);
        List<String> funds=allocated(other,captured(other,"3800.00").get("receiptId").toString(),r,"3800.00");
        db.update("UPDATE ql_session SET capacity=1 WHERE id=?",c.s);
        Map confirm=settlement(other,r,"3800.00",funds),cancel=cancelBody(c);
        List<Map> results=parallel(Arrays.asList(()->cancelPerson(c,cancel),()->settle(r,confirm)));success(results.get(0));
        if(code(results.get(1))!=200)success(settle(r,settlement(other,r,"3800.00",funds)));
        assertEquals(1,seats(c.s));assertEquals("cancelled",person(c.b,c.r).get("registrationStatus"));assertEquals("confirmed",person(other,r).get("registrationStatus"));amount(3800,person(c.b,c.r).get("refundableAmount"));
    }
    @Test void malformedMoneyTimeAndUnknownActualFieldsReturnClientErrorsBeforeFactWrite() {
        Cash c=cash("3800.00");for(String value:Arrays.asList("-1.00","0.001","10000000000.00"))assertEquals(400,code(request(c.r,refundBody(c,value))));
        String req=requested(c,"1000.00");Map body=paymentBody(req,"1000.00");body.put("occurredAt","2026-02-30T08:00:00+08:00");assertEquals(400,code(payment(c.r,req,body)));
        body=paymentBody(req,"1000.00");body.put("operatorId",999);assertEquals(400,code(payment(c.r,req,body)));assertEquals(0,payments(c));
    }
}
