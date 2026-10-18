package com.yicai.web.integration;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Operations projections + real browser. Inherits unchanged phase 1-3 / legacy HTTP regressions. */
class ParticipantOperationsHttpIT extends ParticipantPassHttpIT {
    List<Map> choices(String r,String scope,String token) {
        Map response=call("/life/selection/registrations/"+r+"/passes"+(scope==null?"":"?scope="+scope),HttpMethod.GET,null,token,true);
        success(response);return (List<Map>)response.get("data");
    }
    @Test void scopedSelectorReturnsOwnOrOrderHolderAccountsWithoutBroadCustomerAccess() {
        Card c=card(true,4);String child=companion(c),owner=db.queryForObject("SELECT customer_id FROM ql_registration WHERE id=?",String.class,child),own=openCard(owner,2);
        assertEquals(c.a,choices(child,null,admin()).get(0).get("id"));
        assertEquals(own,choices(child,"self",admin()).get(0).get("id"));
        assertEquals(c.a,choices(child,"same_order",admin()).get(0).get("id"));
        assertEquals(new HashSet<>(Arrays.asList("id","passType","balance","usable","status","validFrom","validUntil","createdAt")),choices(child,"self",admin()).get(0).keySet());
        assertEquals(400,code(call("/life/selection/registrations/"+child+"/passes?scope=customer",HttpMethod.GET,null,admin(),true)));
        String noList=actor("life:registration:payment","life:registration:edit");
        assertEquals(403,code(call("/life/selection/registrations/"+child+"/passes?scope=self",HttpMethod.GET,null,noList,true)));
    }
    @Test void scopedSelectorDoesNotChangeLegacyDefaultSelection() {
        flag("false");String[] app=account();String batch=order(session(),app,true),r=registration(batch),card=openCard(app[1],2);
        assertEquals(card,choices(r,null,admin()).get(0).get("id"));
        assertEquals(404,code(call("/life/selection/registrations/"+r+"/passes?scope=self",HttpMethod.GET,null,admin(),true)));
    }
    @Test void financeProjectionReflectsReservationReleaseActualRefundAndRebookingWithoutChangingFacts() {
        Cash c=cash("3800.00");Map p=person(c.b,c.r);assertNotNull(p.get("name"));assertEquals(c.account[1],p.get("customerId"));assertNotNull(p.get("currentSettlementId"));
        String request=requested(c,"100.00");List<Map> allocations=(List<Map>)finance(c.b).get("allocations");amount(3700,allocations.get(0).get("refundableAmount"));
        Map body=action(request);body.put("noActualPaymentConfirmed",true);success(transition(c,request,"cancel",body));
        amount(3800,((List<Map>)finance(c.b).get("allocations")).get(0).get("refundableAmount"));
        String full=requested(c,"3800.00");success(payment(c.r,full,paymentBody(full,"3800.00")));
        Map history=((List<Map>)finance(c.b).get("refundRequests")).stream().filter(x->full.equals(x.get("requestId"))).findFirst().get();
        assertEquals("Synthetic offline outflow evidence",history.get("evidence"));assertEquals("transfer",history.get("paymentMethod"));
        assertFalse(person(c.b,c.r).containsKey("rebookingHint"));success(cancelPerson(c,cancelBody(c)));
        assertTrue(person(c.b,c.r).get("rebookingHint").toString().contains("请新建报名"));
        amount(3800,((List<Map>)finance(c.b).get("receipts")).get(0).get("amount"));assertEquals(0,seats(c.s));
    }
    @Test void readOnlyAndClosedGateProjectionsKeepTheSameAuthoritativeAmounts() {
        Card c=card(true,3);String child=companion(c),v=verified(c,child,c.a,1,"same_order");success(settle(child,passBody(c,child,c.a,1,v)));
        String viewer=actor("life:registration:query","life:registration:payment");flag("false");
        Map view=ok(call("/life/registration/batch/"+c.b+"/finance",HttpMethod.GET,null,viewer,true));
        assertEquals(false,view.get("writeEnabled"));Map person=((List<Map>)view.get("participants")).stream().filter(x->child.equals(x.get("id"))).findFirst().get();
        assertEquals("pass",person.get("settlementType"));assertEquals(true,person.get("seatOccupied"));assertEquals("unpaid",person.get("paymentStatus"));assertNotNull(person.get("passAccountId"));
    }
    @Test void paginatedRegistrationListSeparatesPersonAndLegacyReadiness() {
        String s=session(),participant=participant(s,true);flag("false");String legacy=order(s,account(),true);
        db.update("UPDATE ql_registration SET registration_status='confirmed' WHERE batch_id=?",legacy);
        Map response=call("/life/registration/list?sessionNumber="+db.queryForObject("SELECT session_number FROM ql_session WHERE id=?",Integer.class,s),HttpMethod.GET,null,admin(),true);success(response);
        List<Map> rows=(List<Map>)response.get("rows");assertEquals(4,rows.size());
        for(Map row:rows)assertEquals(legacy.equals(row.get("batchId")),row.get("batchReadyForPayment"));
        assertEquals(2,rows.stream().filter(x->participant.equals(x.get("batchId"))).count());
    }
    @Test void actualAdminBrowserCompletesOperationsAgainstRealIsolatedApi() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("QINGLIFE_M2_UI_TEST")),"Opt-in local built admin + browser required");
        flag("true");String s=session();String[] app=account();
        String batch=ok(post("/registrations",signup(s,self("合成主报名人"),child("合成现金同行人"),child("合成卡次同行人")),app[0])).get("id").toString();
        String card=openCard(app[1],5);List<Map<String,Object>> people=rows(batch);
        String cardPerson=people.stream().filter(x->"合成卡次同行人".equals(x.get("participant_name_snapshot"))).findFirst().get().get("id").toString();
        openCard(db.queryForObject("SELECT customer_id FROM ql_registration WHERE id=?",String.class,cardPerson),2);
        Path repo=Paths.get(System.getProperty("qinglife.migrations")).toAbsolutePath().normalize().getParent().getParent();
        Path artifacts=Paths.get(System.getenv("QINGLIFE_M2_UI_ARTIFACTS"));Files.createDirectories(artifacts);
        Cash difference=cash("3800.00");
        String viewer=actor("life:registration:list","life:registration:query","life:registration:payment");
        // Only navigation directories, with no extra action permissions, for synthetic viewer roles.
        db.update("INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT ur.role_id,m.menu_id FROM sys_user_role ur JOIN sys_user u ON u.user_id=ur.user_id CROSS JOIN sys_menu m WHERE u.nick_name='Synthetic M2 operator' AND m.menu_type='M'");
        Map input=map("api","http://127.0.0.1:"+httpPort,"token",admin(),"viewer",viewer,"batchId",batch,"sessionNumber",db.queryForObject("SELECT session_number FROM ql_session WHERE id=?",Integer.class,s),"cardId",card,"cardPerson",cardPerson,"differenceSession",db.queryForObject("SELECT session_number FROM ql_session WHERE id=?",Integer.class,difference.s));
        Process process=new ProcessBuilder(System.getenv().getOrDefault("QINGLIFE_NODE","node"),repo.resolve("scripts/test-participant-operations-ui.cjs").toString()).directory(repo.toFile()).redirectErrorStream(true).redirectOutput(artifacts.resolve("browser-run.log").toFile()).start();
        try {
            process.getOutputStream().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(input));process.getOutputStream().close();
            assertTrue(process.waitFor(300,TimeUnit.SECONDS),"Browser flow timed out; inspect isolated artifacts");assertEquals(0,process.exitValue(),"Browser workflow failed; inspect browser-run.log");
        } finally {if(process.isAlive())process.destroyForcibly();}
        assertEquals(1,seats(s));assertEquals(5,balance(card));assertEquals(1,debits(card));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_batch_id=?",Integer.class,batch));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_refund_payment p JOIN ql_refund_request q ON q.id=p.request_id JOIN ql_registration r ON r.id=q.registration_id WHERE r.batch_id=?",Integer.class,batch));
        String self=people.stream().filter(x->app[1].equals(x.get("customer_id"))).findFirst().get().get("id").toString();
        amount(3800,person(batch,self).get("refundedAmount"));assertEquals("cancelled",person(batch,self).get("registrationStatus"));
        assertTrue(person(batch,self).get("rebookingHint").toString().contains("请新建报名"));
        assertNotEquals(batch,ok(post("/registrations",signup(s,self("合成重新报名")),app[0])).get("id"));
    }
}
