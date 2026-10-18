package com.yicai.web.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/MySQL/Redis pass flows; inherits all phase-1/2 and legacy cash/pricing regressions. */
class ParticipantPassHttpIT extends ParticipantRefundHttpIT {
    class Card {String s,b,r,a,owner;String[] app;}
    String openCard(String owner,int units) {
        Map response=call("/life/pass",HttpMethod.POST,map("customerId",owner,"passType","Synthetic "+id(),"initialUnits",units,"reason","Synthetic verified opening"),admin(),true);
        success(response);return response.get("data").toString();
    }
    Card card(boolean multiple,int units) {
        Card c=new Card();c.s=session();c.app=account();c.owner=c.app[1];flag("true");c.b=order(c.s,c.app,multiple);
        c.r=rows(c.b).stream().filter(r->c.owner.equals(r.get("customer_id"))).findFirst().get().get("id").toString();c.a=openCard(c.owner,units);return c;
    }
    String companion(Card c){return rows(c.b).stream().filter(r->!c.r.equals(r.get("id"))).findFirst().get().get("id").toString();}
    int balance(String account){return db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account);}
    int debits(String account){return db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE pass_account_id=? AND entry_type='consume'",Integer.class,account);}
    Map verifyBody(Card c,String r,String account,int units,String scope){return map("operationId",id(),"expectedRevision",person(c.b,r).get("revision"),"registrationId",r,"passAccountId",account,"passUnits",units,"scope",scope,"basis","Synthetic operator verified holder and beneficiary");}
    Map verify(String r,Map bo){return call("/life/registration/"+r+"/pass-verifications",HttpMethod.POST,bo,admin(),true);}
    String verified(Card c,String r,String account,int units,String scope){return ok(verify(r,verifyBody(c,r,account,units,scope))).get("verificationId").toString();}
    Map passBody(Card c,String r,String account,int units,String verification){return map("mode","participant","settlementType","pass","operationId",id(),"expectedRevision",person(c.b,r).get("revision"),"verificationId",verification,"passAccountId",account,"passUnits",units,"reason","Synthetic person pass settlement");}
    Map returnBody(Card c,String r){return map("mode","participant","operationId",id(),"expectedRevision",person(c.b,r).get("revision"),"reason","Synthetic original-account return");}
    Map returned(String r,String round,Map bo){return call("/life/registration/"+r+"/settlement/"+round+"/revoke",HttpMethod.PUT,bo,admin(),true);}
    Map revokeBody(Card c,String r){Map b=returnBody(c,r);b.remove("mode");return b;}
    Map revoke(String r,String verification,Map body){return call("/life/registration/"+r+"/pass-verifications/"+verification+"/revoke",HttpMethod.PUT,body,admin(),true);}
    Map cancel(Card c,String r){return call("/life/registration/"+r+"/cancel",HttpMethod.PUT,revokeBody(c,r),admin(),true);}
    @Test void selfUseConsumesOneRoundAndConfirmsOnlyThePersonWithCompleteProvenance() {
        Card c=card(true,5);String v=verified(c,c.r,c.a,2,"self");assertEquals(5,balance(c.a));assertEquals(0,seats(c.s));
        Map body=passBody(c,c.r,c.a,2,v),result=ok(settle(c.r,body));assertEquals(result,ok(settle(c.r,body)));assertEquals(3,balance(c.a));assertEquals(1,seats(c.s));
        assertEquals(c.owner,result.get("holderCustomerId"));assertEquals(c.owner,result.get("beneficiaryCustomerId"));assertEquals(c.s,result.get("sessionId"));
        Map row=db.queryForMap("SELECT * FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",result.get("settlementId"));
        assertEquals(c.r,row.get("registration_id"));assertNull(row.get("registration_batch_id"));assertEquals(-2,((Number)row.get("quantity_delta")).intValue());
        assertEquals(v,db.queryForObject("SELECT pass_verification_id FROM ql_settlement_log WHERE id=?",String.class,result.get("settlementId")));
        assertEquals("unpaid",person(c.b,c.r).get("paymentStatus"));assertEquals("pending",person(c.b,companion(c)).get("registrationStatus"));assertEquals(0,receipts(c.b));
        assertEquals(1,((List)person(c.b,c.r).get("passUsage")).size());
    }
    @Test void sameOrderBuyerCardCanPayCompanionWhileCompanionCanAlsoUseOwnCard() {
        Card c=card(true,4);String child=companion(c),childCustomer=db.queryForObject("SELECT customer_id FROM ql_registration WHERE id=?",String.class,child);
        String v=verified(c,child,c.a,1,"same_order");Map saved=ok(settle(child,passBody(c,child,c.a,1,v)));
        assertEquals(c.owner,saved.get("holderCustomerId"));assertEquals(childCustomer,saved.get("beneficiaryCustomerId"));assertEquals(3,balance(c.a));assertEquals(1,seats(c.s));
        Card other=card(true,4);String otherChild=companion(other),customer=db.queryForObject("SELECT customer_id FROM ql_registration WHERE id=?",String.class,otherChild);
        String own=openCard(customer,3),self=verified(other,otherChild,own,2,"self");success(settle(otherChild,passBody(other,otherChild,own,2,self)));
        assertEquals(1,balance(own));assertEquals(4,balance(other.a));assertEquals(1,seats(other.s));
    }
    @Test void unrelatedHoldersCrossOrderVerificationAndScopeSpoofingAreRejected() {
        Card c=card(true,3),other=card(false,3);String child=companion(c);
        assertEquals(400,code(verify(child,verifyBody(c,child,other.a,1,"same_order"))));
        assertEquals(400,code(verify(child,verifyBody(c,child,c.a,1,"self"))));
        conflict(verify(c.r,verifyBody(c,c.r,c.a,1,"same_order")));
        String v=verified(other,other.r,other.a,1,"self");conflict(settle(c.r,passBody(c,c.r,c.a,1,v)));
        Map spoof=verifyBody(c,child,c.a,1,"same_order");spoof.put("holderCustomerId",other.owner);assertEquals(400,code(verify(child,spoof)));
        assertEquals(3,balance(c.a));assertEquals(3,balance(other.a));assertEquals(0,seats(c.s));
    }
    @Test void revokedUnusedVerificationCannotDeductAndItsImmutableBasisIsRetained() {
        Card c=card(false,3);Map original=verifyBody(c,c.r,c.a,1,"self");String v=ok(verify(c.r,original)).get("verificationId").toString();
        Map body=revokeBody(c,c.r),saved=ok(revoke(c.r,v,body));assertEquals(saved,ok(revoke(c.r,v,body)));assertEquals("revoked",saved.get("state"));
        conflict(settle(c.r,passBody(c,c.r,c.a,1,v)));assertEquals(3,balance(c.a));assertEquals(0,seats(c.s));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_operator_verification WHERE id=? AND basis=?",Integer.class,v,original.get("basis")));
        String newer=verified(c,c.r,c.a,1,"self");success(settle(c.r,passBody(c,c.r,c.a,1,newer)));assertEquals(2,balance(c.a));
    }
    @Test void usedVerificationCannotBeRevokedOrReusedForAnotherRoundOrPerson() {
        Card c=card(true,3);String v=verified(c,c.r,c.a,1,"self"),round=ok(settle(c.r,passBody(c,c.r,c.a,1,v))).get("settlementId").toString();
        conflict(revoke(c.r,v,revokeBody(c,c.r)));success(returned(c.r,round,returnBody(c,c.r)));
        conflict(settle(c.r,passBody(c,c.r,c.a,1,v)));conflict(settle(companion(c),passBody(c,companion(c),c.a,1,v)));
        assertEquals(3,balance(c.a));assertEquals(1,debits(c.a));assertEquals(1,seats(c.s));
    }
    @Test void newerVerificationSupersedesOlderAndQuantityOrAccountChangesCannotBypassIt() {
        Card c=card(false,4);String first=verified(c,c.r,c.a,1,"self"),second=verified(c,c.r,c.a,2,"self");
        conflict(settle(c.r,passBody(c,c.r,c.a,1,first)));conflict(settle(c.r,passBody(c,c.r,c.a,1,second)));
        String another=openCard(c.owner,5);conflict(settle(c.r,passBody(c,c.r,another,2,second)));
        success(settle(c.r,passBody(c,c.r,c.a,2,second)));assertEquals(2,balance(c.a));assertEquals(5,balance(another));
    }
    @Test void sharedAccountConcurrentDeductionsAcrossPeriodsNeverOverdraw()throws Exception {
        Card c=card(false,1);Card other=new Card();other.s=session();other.app=c.app;other.owner=c.owner;other.b=order(other.s,other.app,false);other.r=registration(other.b);other.a=c.a;
        String v=verified(c,c.r,c.a,1,"self"),w=verified(other,other.r,c.a,1,"self");
        Map one=passBody(c,c.r,c.a,1,v),two=passBody(other,other.r,c.a,1,w);
        List<Map> results=parallel(Arrays.asList(()->settle(c.r,one),()->settle(other.r,two)));
        assertEquals(1,results.stream().filter(x->code(x)==200).count());assertEquals(1,results.stream().filter(x->code(x)==400).count());
        assertEquals(0,balance(c.a));assertEquals(1,debits(c.a));assertEquals(1,seats(c.s)+seats(other.s));
    }
    @Test void twoPeopleCanConsumeSameAccountConcurrentlyWhenBalanceIsEnough()throws Exception {
        Card c=card(true,3);String child=companion(c),v=verified(c,c.r,c.a,1,"self"),w=verified(c,child,c.a,2,"same_order");
        Map one=passBody(c,c.r,c.a,1,v),two=passBody(c,child,c.a,2,w);List<Map> results=parallel(Arrays.asList(()->settle(c.r,one),()->settle(child,two)));
        success(results.get(0));success(results.get(1));assertEquals(0,balance(c.a));assertEquals(2,debits(c.a));assertEquals(2,seats(c.s));
    }
    @Test void identicalConcurrentSettlementOnlyCreatesOneDebitAndChangedPayloadCannotReuseKey()throws Exception {
        Card c=card(false,3);String v=verified(c,c.r,c.a,1,"self");Map bo=passBody(c,c.r,c.a,1,v);
        List<Map> results=parallel(Arrays.asList(()->settle(c.r,bo),()->settle(c.r,bo)));assertEquals(ok(results.get(0)),ok(results.get(1)));
        assertEquals(1,debits(c.a));assertEquals(2,balance(c.a));bo.put("passUnits",2);conflict(settle(c.r,bo));
        assertEquals(1,seats(c.s));
    }
    @Test void insufficientBalanceCanRetryExactRequestAfterAnAuditedExistingAdjustment() {
        Card c=card(false,1);String v=verified(c,c.r,c.a,2,"self");Map body=passBody(c,c.r,c.a,2,v);
        assertEquals(400,code(settle(c.r,body)));assertEquals(1,balance(c.a));assertEquals(0,seats(c.s));
        success(call("/life/pass/"+c.a+"/adjustments",HttpMethod.POST,map("quantityDelta",1,"reason","Synthetic verified balance top-up"),admin(),true));
        success(settle(c.r,body));assertEquals(0,balance(c.a));assertEquals(1,debits(c.a));
    }
    @Test void expiryAndInactiveAccountsAreCheckedAgainAtConsumption() {
        Card c=card(false,3);String v=verified(c,c.r,c.a,1,"self");Map body=passBody(c,c.r,c.a,1,v);
        db.update("UPDATE ql_pass_account SET valid_until=DATE_SUB(CURRENT_DATE(),INTERVAL 1 DAY) WHERE id=?",c.a);
        assertEquals(400,code(settle(c.r,body)));assertEquals(0,debits(c.a));
        db.update("UPDATE ql_pass_account SET valid_until=NULL,status='void' WHERE id=?",c.a);assertEquals(400,code(settle(c.r,body)));
        db.update("UPDATE ql_pass_account SET status='active',valid_from=DATE_ADD(CURRENT_DATE(),INTERVAL 1 DAY) WHERE id=?",c.a);assertEquals(400,code(settle(c.r,body)));
        assertEquals(3,balance(c.a));assertEquals(0,seats(c.s));
    }
    @Test void cancelDoesNotReturnCardAndReturnAfterCancelAllowsNewRegistration() {
        Card c=card(false,3);String v=verified(c,c.r,c.a,1,"self"),round=ok(settle(c.r,passBody(c,c.r,c.a,1,v))).get("settlementId").toString();
        success(cancel(c,c.r));assertEquals(2,balance(c.a));assertEquals(0,seats(c.s));
        conflict(post("/registrations",signup(c.s,self("Synthetic renewed person")),c.app[0]));
        success(returned(c.r,round,returnBody(c,c.r)));assertEquals(3,balance(c.a));assertEquals("cancelled",person(c.b,c.r).get("registrationStatus"));
        Map saved=ok(post("/registrations",signup(c.s,self("Synthetic renewed person")),c.app[0]));assertNotEquals(c.b,saved.get("id"));assertEquals(0,seats(c.s));
    }
    @Test void returnToExpiredInactiveOriginalHolderDoesNotReleaseSeatOrChangeAccountState() {
        Card c=card(true,4);String child=companion(c),v=verified(c,child,c.a,2,"same_order"),round=ok(settle(child,passBody(c,child,c.a,2,v))).get("settlementId").toString();
        db.update("UPDATE ql_pass_account SET status='void',valid_until=DATE_SUB(CURRENT_DATE(),INTERVAL 1 DAY) WHERE id=?",c.a);
        Map original=db.queryForMap("SELECT * FROM ql_pass_account WHERE id=?",c.a);Map result=ok(returned(child,round,returnBody(c,child)));
        assertEquals(4,balance(c.a));assertEquals(c.owner,result.get("holderCustomerId"));assertEquals(1,seats(c.s));assertEquals(original,db.queryForMap("SELECT * FROM ql_pass_account WHERE id=?",c.a));
        assertEquals("confirmed",person(c.b,child).get("registrationStatus"));assertEquals("pending",person(c.b,child).get("settlementStatus"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE pass_account_id=? AND entry_type='void' AND quantity_delta=2",Integer.class,c.a));
    }
    @Test void concurrentReturnOnlyRestoresOriginalDebitOnceAndOldReplayCannotReverseNewRound()throws Exception {
        Card c=card(false,4);String v=verified(c,c.r,c.a,2,"self"),round=ok(settle(c.r,passBody(c,c.r,c.a,2,v))).get("settlementId").toString();Map body=returnBody(c,c.r);
        List<Map> results=parallel(Arrays.asList(()->returned(c.r,round,body),()->returned(c.r,round,body)));assertEquals(ok(results.get(0)),ok(results.get(1)));assertEquals(4,balance(c.a));
        conflict(returned(c.r,round,returnBody(c,c.r)));
        String newer=verified(c,c.r,c.a,1,"self"),newRound=ok(settle(c.r,passBody(c,c.r,c.a,1,newer))).get("settlementId").toString();
        success(returned(c.r,round,body));assertEquals(3,balance(c.a));assertEquals(newRound,db.queryForObject("SELECT current_settlement_id FROM ql_registration WHERE id=?",String.class,c.r));
        success(returned(c.r,newRound,returnBody(c,c.r)));assertEquals(4,balance(c.a));assertEquals(1,seats(c.s));
    }
    @Test void usedVerificationAndReturnPreserveCompanionStateAndOriginalConsume() {
        Card c=card(true,4);String child=companion(c),v=verified(c,child,c.a,1,"same_order"),round=ok(settle(child,passBody(c,child,c.a,1,v))).get("settlementId").toString();
        Map sibling=db.queryForMap("SELECT * FROM ql_registration WHERE id=?",c.r),debit=db.queryForMap("SELECT * FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",round);
        success(returned(child,round,returnBody(c,child)));assertEquals(sibling,db.queryForMap("SELECT * FROM ql_registration WHERE id=?",c.r));assertEquals(debit,db.queryForMap("SELECT * FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",round));
        assertEquals(debit.get("id"),db.queryForObject("SELECT original_pass_ledger_id FROM ql_settlement_reversal WHERE settlement_id=?",String.class,round));
    }
    @Test void settlementFailureAfterDebitRollsBackLedgerRoundConfirmationAndVerificationBinding() {
        Card c=card(false,3);String v=verified(c,c.r,c.a,1,"self");Map body=passBody(c,c.r,c.a,1,v);
        db.execute("CREATE TRIGGER synthetic_pass_confirm_failure BEFORE UPDATE ON ql_registration FOR EACH ROW BEGIN IF NEW.settlement_status='confirmed' AND NEW.settlement_type='pass' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic person confirm failure'; END IF; END");
        try{conflict(settle(c.r,body));assertEquals(3,balance(c.a));assertEquals(0,debits(c.a));assertEquals(0,seats(c.s));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE pass_verification_id=?",Integer.class,v));}
        finally{db.execute("DROP TRIGGER synthetic_pass_confirm_failure");}
        success(settle(c.r,body));assertEquals(2,balance(c.a));assertEquals(1,debits(c.a));
    }
    @Test void returnAuditFailureRollsBackVoidAndExactRequestCanRetry() {
        Card c=card(false,3);String v=verified(c,c.r,c.a,1,"self"),round=ok(settle(c.r,passBody(c,c.r,c.a,1,v))).get("settlementId").toString();Map body=returnBody(c,c.r);
        db.execute("CREATE TRIGGER synthetic_pass_return_failure BEFORE INSERT ON ql_settlement_reversal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic return audit failure'");
        try{conflict(returned(c.r,round,body));assertEquals(2,balance(c.a));assertEquals(round,db.queryForObject("SELECT current_settlement_id FROM ql_registration WHERE id=?",String.class,c.r));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",Integer.class,round));}
        finally{db.execute("DROP TRIGGER synthetic_pass_return_failure");}
        success(returned(c.r,round,body));assertEquals(3,balance(c.a));assertEquals(1,seats(c.s));
    }
    @Test void missingOrMismatchedOriginalConsumeCannotCreateReturnAndOtherHolderStaysUntouched() {
        Card c=card(false,3);String v=verified(c,c.r,c.a,1,"self"),round=ok(settle(c.r,passBody(c,c.r,c.a,1,v))).get("settlementId").toString();
        String ledger=db.queryForObject("SELECT id FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",String.class,round);
        db.update("UPDATE ql_pass_ledger SET registration_id=NULL WHERE id=?",ledger);
        conflict(returned(c.r,round,returnBody(c,c.r)));assertEquals(2,balance(c.a));
        db.update("UPDATE ql_pass_ledger SET registration_id=? WHERE id=?",c.r,ledger);
        String owner=account()[1];db.update("UPDATE ql_pass_account SET customer_id=? WHERE id=?",owner,c.a);conflict(returned(c.r,round,returnBody(c,c.r)));
        assertEquals(2,balance(c.a));assertEquals(1,seats(c.s));
    }
    @Test void cashPassMixingMultiCardAndCashRefundForPassAreRejected() {
        Card c=card(false,4);String v=verified(c,c.r,c.a,1,"self");Map mixed=passBody(c,c.r,c.a,1,v);mixed.put("finalAmount","100.00");assertEquals(400,code(settle(c.r,mixed)));
        mixed=passBody(c,c.r,c.a,1,v);mixed.put("passAccountIds",Arrays.asList(c.a,id()));assertEquals(400,code(settle(c.r,mixed)));
        mixed=passBody(c,c.r,c.a,1,v);mixed.put("allocationIds",Collections.singletonList(id()));assertEquals(400,code(settle(c.r,mixed)));
        Cash cash=cash("3800.00");Map cashVerification=verifyBody(c,c.r,c.a,1,"self");cashVerification.put("registrationId",cash.r);cashVerification.put("expectedRevision",person(cash.b,cash.r).get("revision"));conflict(verify(cash.r,cashVerification));
        String receipt=captured(c.b,"100.00").get("receiptId").toString();allocated(c.b,receipt,c.r,"100.00");
        conflict(settle(c.r,passBody(c,c.r,c.a,1,v)));assertEquals(4,balance(c.a));
        Card clean=card(false,2);String verified=verified(clean,clean.r,clean.a,1,"self");success(settle(clean.r,passBody(clean,clean.r,clean.a,1,verified)));
        Cash view=new Cash();view.b=clean.b;view.r=clean.r;view.allocations.add(id());conflict(request(clean.r,refundBody(view,"1.00")));assertEquals(1,balance(clean.a));
    }
    @Test void gateOffKeepsPassReadsAndSeatsButRejectsNewActionsAndSuccessfulReplays() {
        Card c=card(false,3);Map verification=verifyBody(c,c.r,c.a,1,"self");String v=ok(verify(c.r,verification)).get("verificationId").toString();Map body=passBody(c,c.r,c.a,1,v);
        String round=ok(settle(c.r,body)).get("settlementId").toString();flag("false");
        assertEquals(423,code(verify(c.r,verification)));assertEquals(423,code(settle(c.r,body)));assertEquals(423,code(returned(c.r,round,returnBody(c,c.r))));
        assertEquals(1,seats(c.s));assertEquals(2,balance(c.a));assertEquals(1,((List)person(c.b,c.r).get("passUsage")).size());
    }
    @Test void independentVerificationConsumeAndReturnPermissionsCannotBeReplacedByOldRights() {
        Card c=card(false,3);String old=actor("life:registration:payment","life:registration:edit","life:pass:adjust"),verifyOnly=actor("life:registration:payment","life:registration:edit","life:pass:verify");
        Map verification=verifyBody(c,c.r,c.a,1,"self");String base="/life/registration/"+c.r;
        assertEquals(403,code(call(base+"/pass-verifications",HttpMethod.POST,verification,old,true)));assertEquals(403,code(call(base+"/pass-verifications",HttpMethod.POST,verification,verifyOnly,true)));
        assertEquals(401,code(call(base+"/pass-verifications",HttpMethod.POST,verification,c.app[0],false)));
        String authorized=actor("life:registration:payment","life:registration:edit","life:pass:verify","life:pass:consume"),v=ok(call(base+"/pass-verifications",HttpMethod.POST,verification,authorized,true)).get("verificationId").toString();
        Map settle=passBody(c,c.r,c.a,1,v);assertEquals(403,code(call(base+"/settlement",HttpMethod.PUT,settle,old,true)));
        String round=ok(call(base+"/settlement",HttpMethod.PUT,settle,authorized,true)).get("settlementId").toString();
        String oldRevoke=actor("life:registration:settlement:revoke"),returnOnly=actor("life:pass:restore");Map refund=returnBody(c,c.r);
        assertEquals(403,code(call(base+"/settlement/"+round+"/revoke",HttpMethod.PUT,refund,oldRevoke,true)));assertEquals(403,code(call(base+"/settlement/"+round+"/revoke",HttpMethod.PUT,refund,returnOnly,true)));
        String restorer=actor("life:registration:settlement:revoke","life:pass:restore");success(call(base+"/settlement/"+round+"/revoke",HttpMethod.PUT,refund,restorer,true));assertEquals(3,balance(c.a));
    }
    @Test void finalSeatFailureNeverLeavesDebitAndRetryAfterCapacityChangeIsSafe() {
        Card c=card(true,3);String child=companion(c),v=verified(c,c.r,c.a,1,"self"),w=verified(c,child,c.a,1,"same_order");
        db.update("UPDATE ql_session SET capacity=1 WHERE id=?",c.s);success(settle(c.r,passBody(c,c.r,c.a,1,v)));Map second=passBody(c,child,c.a,1,w);
        assertEquals(400,code(settle(child,second)));assertEquals(2,balance(c.a));assertEquals(1,debits(c.a));
        db.update("UPDATE ql_session SET capacity=2 WHERE id=?",c.s);success(settle(child,second));assertEquals(1,balance(c.a));assertEquals(2,seats(c.s));
    }
    @Test void failedVerificationInsertCanRetryExactOperationAndDoesNotReserveBalance() {
        Card c=card(false,2);Map body=verifyBody(c,c.r,c.a,1,"self");
        db.execute("CREATE TRIGGER synthetic_verification_failure BEFORE INSERT ON ql_pass_operator_verification FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic verification failure'");
        try{conflict(verify(c.r,body));assertEquals(2,balance(c.a));assertEquals(0,debits(c.a));}
        finally{db.execute("DROP TRIGGER synthetic_verification_failure");}
        Map first=ok(verify(c.r,body));assertEquals(first,ok(verify(c.r,body)));body.put("basis","Synthetic changed verification");conflict(verify(c.r,body));assertEquals(2,balance(c.a));
    }
    @Test void concurrentVerificationRevocationAndConsumptionCannotBothWin()throws Exception {
        Card c=card(false,2);String v=verified(c,c.r,c.a,1,"self");Map consume=passBody(c,c.r,c.a,1,v),revoke=revokeBody(c,c.r);
        List<Map> results=parallel(Arrays.asList(()->settle(c.r,consume),()->revoke(c.r,v,revoke)));
        assertEquals(1,results.stream().filter(r->code(r)==200).count());assertEquals(1,results.stream().filter(r->code(r)==409).count());
        int revoked=db.queryForObject("SELECT COUNT(*) FROM ql_pass_verification_revocation WHERE verification_id=?",Integer.class,v);
        assertEquals(1,revoked+debits(c.a));assertEquals(2-debits(c.a),balance(c.a));assertEquals(debits(c.a),seats(c.s));
    }
    @Test void cancellationInvalidatesUnusedVerificationAndCannotBeRestoredByLateConfirmation() {
        Card c=card(false,2);String v=verified(c,c.r,c.a,1,"self");success(cancel(c,c.r));
        conflict(settle(c.r,passBody(c,c.r,c.a,1,v)));assertEquals(2,balance(c.a));assertEquals(0,seats(c.s));
        assertEquals("cancelled",person(c.b,c.r).get("registrationStatus"));Map newer=ok(post("/registrations",signup(c.s,self("Synthetic new record")),c.app[0]));assertNotEquals(c.b,newer.get("id"));
        assertEquals(0,debits(c.a));
    }
    @Test void inconsistentOrderAndParticipantSessionCannotConsumeOutsideTheLockedPeriod() {
        Card c=card(false,2);String otherSession=session();
        db.update("UPDATE ql_registration SET session_id=? WHERE id=?",otherSession,c.r);
        conflict(verify(c.r,verifyBody(c,c.r,c.a,1,"self")));
        assertEquals(2,balance(c.a));assertEquals(0,debits(c.a));assertEquals(0,seats(c.s));assertEquals(0,seats(otherSession));
    }
}
