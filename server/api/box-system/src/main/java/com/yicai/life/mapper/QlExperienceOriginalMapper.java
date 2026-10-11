package com.yicai.life.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface QlExperienceOriginalMapper {
 String ORIGINAL_SQL="SELECT id,note,energy,relaxation,DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s.%f') AS recordStamp FROM ql_experience_record WHERE customer_id=#{customer} AND registration_id=#{registration} AND phase='after' AND node_key=''";
 @Select("SELECT id FROM ql_registration WHERE id=#{registration} AND customer_id=#{customer} FOR UPDATE")
 String lockRegistration(@Param("customer") String customer,@Param("registration") String registration);
 @Select(ORIGINAL_SQL) Map<String,Object> read(@Param("customer") String customer,@Param("registration") String registration);
 @Select(ORIGINAL_SQL+" FOR UPDATE") Map<String,Object> readForUpdate(@Param("customer") String customer,@Param("registration") String registration);
 @Insert("INSERT INTO ql_experience_record(id,registration_id,customer_id,session_id,phase,node_key,note,visibility_scope,created_at,updated_at) VALUES(#{id},#{registration},#{customer},#{session},'after','',#{note},'private',#{now},#{now}) ON DUPLICATE KEY UPDATE note=VALUES(note),updated_at=VALUES(updated_at)")
 int save(@Param("id") String id,@Param("customer") String customer,@Param("registration") String registration,@Param("session") String session,@Param("note") String note,@Param("now") Date now);
}
