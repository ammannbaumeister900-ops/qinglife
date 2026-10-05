package com.yicai.life.service;
import com.yicai.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.yicai.life.domain.bo.QlReflectionModerationBo;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class QlCampReflectionServiceTest {
 Map<String,Object> owner(){Map<String,Object> o=new HashMap<>();o.put("registrationStatus","confirmed");o.put("sessionStatus","in_progress");o.put("startDate","2026-10-03");o.put("endDate","2026-10-05");o.put("endTime","17:00:00");return o;}
 @Test void writingStaysOpenFromStartDayThroughCampThenContinuesAfterCompletion(){
  Map<String,Object> o=owner();
  assertFalse(QlCampReflectionService.canWrite(o,"before",LocalDateTime.parse("2026-10-02T23:59:59")));
  for(String time:Arrays.asList("2026-10-03T00:00:00","2026-10-03T23:59:59","2026-10-04T00:00:00","2026-10-04T23:59:59","2026-10-05T16:59:59")){
   assertTrue(QlCampReflectionService.canWrite(o,"before",LocalDateTime.parse(time)),time);
   assertFalse(QlCampReflectionService.canWrite(o,"after",LocalDateTime.parse(time)),time);
  }
  assertFalse(QlCampReflectionService.canWrite(o,"before",LocalDateTime.parse("2026-10-05T17:00:00")));
  assertTrue(QlCampReflectionService.canWrite(o,"after",LocalDateTime.parse("2026-10-05T17:00:00")));
  assertTrue(QlCampReflectionService.canWrite(o,"after",LocalDateTime.parse("2027-01-01T00:00:00")));
  assertFalse(QlCampReflectionService.canWrite(o,"during",LocalDateTime.parse("2026-10-04T12:00:00")));
 }
 @Test void completionWithoutEndTimeStillHasNoGapAndCompletedStatusSelectsAfter(){
  Map<String,Object> o=owner();o.put("endTime",null);
  assertTrue(QlCampReflectionService.canWrite(o,"before",LocalDateTime.parse("2026-10-05T23:59:59")));
  assertTrue(QlCampReflectionService.canWrite(o,"after",LocalDateTime.parse("2026-10-06T00:00:00")));
  o.put("sessionStatus","completed");
  assertFalse(QlCampReflectionService.canWrite(o,"before",LocalDateTime.parse("2026-10-04T12:00:00")));
  assertTrue(QlCampReflectionService.canWrite(o,"after",LocalDateTime.parse("2026-10-04T12:00:00")));
 }
 @Test void pendingCancelledAndDraftCannotWrite(){Map<String,Object> o=owner();o.put("registrationStatus","pending");assertFalse(QlCampReflectionService.canWrite(o,"before",LocalDateTime.parse("2026-10-03T12:00:00")));o.put("registrationStatus","confirmed");for(String status:Arrays.asList("cancelled","draft")){o.put("sessionStatus",status);assertFalse(QlCampReflectionService.canWrite(o,"before",LocalDateTime.parse("2026-10-03T12:00:00")));}}
 @Test void codePointLimitKeepsUnicodeAndNeverTruncates(){String emoji=new String(Character.toChars(0x1f33f));String max=String.join("",Collections.nCopies(150,emoji));assertEquals(max,QlCampReflectionService.validateNote(max));assertThrows(CustomException.class,()->QlCampReflectionService.validateNote(max+"轻"));assertThrows(CustomException.class,()->QlCampReflectionService.validateNote("  "));assertEquals(emoji,QlCampReflectionService.prefix(emoji+"轻",1));}

 Map<String,Object> reviewRow(){Map<String,Object> r=new HashMap<>();r.put("note","原始投稿，有一点紧张，也期待新的变化");r.put("status","submitted");r.put("revision",1);r.put("consent",1);r.put("sortOrder",100);return r;}
 QlReflectionModerationBo reviewBo(String text){QlReflectionModerationBo b=new QlReflectionModerationBo();b.setRevision(1);b.setDisplayNote(text);return b;}
 @Test void approvalAndPublicationAreOneRevisionWithFullEditableTextAndOriginalPreserved(){
  JdbcTemplate db=mock(JdbcTemplate.class);when(db.queryForList(anyString(),eq("voice"))).thenReturn(Collections.singletonList(reviewRow()));
  QlCampReflectionService s=new QlCampReflectionService(null,null,db,null);String text=String.join("",Collections.nCopies(100,"轻"));s.moderate("voice","approvePublish",reviewBo(text),42L);
  verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("published"),eq(QlCampReflectionService.prefix(text,32)),eq(text),eq(100),any(Date.class),eq("voice"));
  verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("approvePublish"),eq(2),eq(42L),any(Date.class));
 }
 @Test void reviewOnlyRetainsPendingPublicationAndAcceptsEditedText(){
  JdbcTemplate db=mock(JdbcTemplate.class);when(db.queryForList(anyString(),eq("voice"))).thenReturn(Collections.singletonList(reviewRow()));
  new QlCampReflectionService(null,null,db,null).moderate("voice","approve",reviewBo("慢慢来，期待新的变化"),42L);
  verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("approved"),eq("慢慢来，期待新的变化"),eq("慢慢来，期待新的变化"),eq(100),any(Date.class),eq("voice"));
 }
 @Test void withdrawnChangedOrAlreadyPublishedSubmissionsCannotPassCombinedReview(){
  JdbcTemplate db=mock(JdbcTemplate.class);Map<String,Object> row=reviewRow();when(db.queryForList(anyString(),eq("voice"))).thenReturn(Collections.singletonList(row));QlCampReflectionService s=new QlCampReflectionService(null,null,db,null);
  row.put("consent",0);assertThrows(CustomException.class,()->s.moderate("voice","approvePublish",reviewBo("展示内容"),42L));row.put("consent",1);row.put("revision",2);assertThrows(CustomException.class,()->s.moderate("voice","approvePublish",reviewBo("展示内容"),42L));row.put("revision",1);row.put("status","published");assertThrows(CustomException.class,()->s.moderate("voice","approvePublish",reviewBo("展示内容"),42L));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void fullDisplayTextRejectsEmptyOrOverLimitWithoutWriting(){
  JdbcTemplate db=mock(JdbcTemplate.class);when(db.queryForList(anyString(),eq("voice"))).thenReturn(Collections.singletonList(reviewRow()));QlCampReflectionService s=new QlCampReflectionService(null,null,db,null);
  for(String text:Arrays.asList("  ",String.join("",Collections.nCopies(151,"轻"))))assertThrows(CustomException.class,()->s.moderate("voice","approvePublish",reviewBo(text),42L));verify(db,never()).update(anyString(),any(Object[].class));
 }

 @Test void editingAnApprovedOrHiddenVoicePreservesItsStateAndOriginal(){
  for(String state:Arrays.asList("approved","hidden")){
   JdbcTemplate db=mock(JdbcTemplate.class);Map<String,Object> row=reviewRow();row.put("status",state);when(db.queryForList(anyString(),eq("voice"))).thenReturn(Collections.singletonList(row));
   new QlCampReflectionService(null,null,db,null).moderate("voice","edit",reviewBo("修改后的完整展示文字"),42L);
   verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq(state),eq("修改后的完整展示文字"),eq("修改后的完整展示文字"),eq(100),any(Date.class),eq("voice"));
   verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("edit"),eq(2),eq(42L),any(Date.class));assertEquals("原始投稿，有一点紧张，也期待新的变化",row.get("note"));
  }
 }
 @Test void displayedWithdrawnOrStaleVoiceCannotBeEditedAndEmptyDisplayIsRejected(){
  JdbcTemplate db=mock(JdbcTemplate.class);Map<String,Object> row=reviewRow();when(db.queryForList(anyString(),eq("voice"))).thenReturn(Collections.singletonList(row));QlCampReflectionService service=new QlCampReflectionService(null,null,db,null);
  for(String state:Arrays.asList("submitted","published","withdrawn","rejected")){row.put("status",state);assertThrows(CustomException.class,()->service.moderate("voice","edit",reviewBo("修改"),42L));}
  row.put("status","hidden");row.put("revision",2);assertThrows(CustomException.class,()->service.moderate("voice","edit",reviewBo("修改"),42L));row.put("revision",1);row.put("consent",0);assertThrows(CustomException.class,()->service.moderate("voice","edit",reviewBo("修改"),42L));row.put("consent",1);assertThrows(CustomException.class,()->service.moderate("voice","edit",reviewBo("  "),42L));verify(db,never()).update(anyString(),any(Object[].class));
 }
}
