package com.yicai.life.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface QlReportMapper {
    @Select({"<script>",
            "SELECT r.id, r.legacy_publish_id AS publishId, r.reason_code AS reasonCode, r.reason_note AS reasonNote,",
            "r.status, r.handle_result AS handleResult, r.created_at AS createdAt, r.handled_at AS handledAt,",
            "c.customer_no AS reporterNo, c.nickname AS reporterNickname, u.nick_name AS handlerName,",
            "p.content AS postContent",
            "FROM ql_post_report r JOIN ql_customer c ON c.id=r.reporter_customer_id",
            "LEFT JOIN sys_user u ON u.user_id=r.handled_by",
            "LEFT JOIN app_user_publish p ON p.id=r.legacy_publish_id",
            "<where><if test='status != null and status != \"\"'>r.status=#{status}</if></where>",
            "ORDER BY r.created_at DESC",
            "</script>"})
    List<Map<String, Object>> selectList(@Param("status") String status);

    @Update("UPDATE ql_post_report SET status=#{status}, handled_by=#{operatorId}, handled_at=#{now}, " +
            "handle_result=#{result} WHERE id=#{id}")
    int handle(@Param("id") String id, @Param("status") String status,
               @Param("result") String result, @Param("operatorId") Long operatorId,
               @Param("now") Date now);
}
