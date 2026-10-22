package com.yicai.life.service;
import com.yicai.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QlPublicCampVoicesTest {
 QlCampReflectionService service(JdbcTemplate db){return new QlCampReflectionService(null,null,db,null,null,null,null);}
 Map<String,Object> row(String id){Map<String,Object> m=new HashMap<>();m.put("id",id);m.put("cursorOrder",100);m.put("sessionId","s");m.put("note","公开原文");return m;}
 @Test void paginationIsBoundedAndCursorUsesParameters(){
  JdbcTemplate db=mock(JdbcTemplate.class);String id="00000000-0000-0000-0000-000000000001";
  when(db.queryForList(anyString(),eq("s"),eq(100),eq(100),eq(id),eq(2))).thenReturn(new ArrayList<>(Arrays.asList(row(id),row("00000000-0000-0000-0000-000000000002"))));
  Map<String,Object> page=service(db).publicVoicePage("s",1,"100:"+id);
  assertEquals(true,page.get("hasMore"));assertEquals("100:"+id,page.get("nextCursor"));assertEquals(1,((List<?>)page.get("list")).size());
  assertFalse(((Map<?,?>)((List<?>)page.get("list")).get(0)).containsKey("cursorOrder"));
  verify(db).queryForList(contains("f.sort_order>?"),eq("s"),eq(100),eq(100),eq(id),eq(2));
 }
 @Test void invalidPagesAndCursorNeverReachDatabase(){JdbcTemplate db=mock(JdbcTemplate.class);QlCampReflectionService s=service(db);for(int size:Arrays.asList(-1,0,25,Integer.MAX_VALUE))assertThrows(CustomException.class,()->s.publicVoicePage("s",size,null));assertThrows(CustomException.class,()->s.publicVoicePage("s",16,"1:SQL injection"));verifyNoInteractions(db);}
 @Test void hiddenOrWrongSessionDetailIsNotPublic(){JdbcTemplate db=mock(JdbcTemplate.class);String id=UUID.randomUUID().toString();when(db.queryForList(anyString(),any(Object[].class))).thenReturn(Collections.emptyList());assertEquals(404,assertThrows(CustomException.class,()->service(db).publicVoice("other",id)).getCode());verify(db).queryForList(contains("AND f.id=?"),eq("other"),eq(id));}
}
