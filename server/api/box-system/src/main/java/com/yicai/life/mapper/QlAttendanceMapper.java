package com.yicai.life.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface QlAttendanceMapper {
    @Select({"<script>",
            "SELECT p.id, p.registration_id AS registrationId, p.attendance_status AS attendanceStatus,",
            "COALESCE(o.corrected_source,p.check_in_source) AS checkInSource, DATE_FORMAT(CASE WHEN COALESCE(o.corrected_source,p.check_in_source)='mobile_workspace' THEN CONVERT_TZ(p.checked_in_at,'+00:00','+08:00') ELSE p.checked_in_at END,'%Y-%m-%d %H:%i:%s') AS checkedInAt, p.change_reason AS changeReason,",
            "d.day_no AS dayNo, d.activity_date AS activityDate, d.theme,",
            "s.session_number AS sessionNumber, s.name AS sessionName,",
            "c.customer_no AS customerNo, c.nickname, c.real_name AS realName",
            "FROM ql_participation_day p",
            "LEFT JOIN ql_attendance_origin_correction o ON o.participation_day_id=p.id AND o.original_checked_in_at=p.checked_in_at AND o.original_operator_id=p.checked_in_by AND o.original_source=p.check_in_source",
            "JOIN ql_registration r ON r.id=p.registration_id",
            "JOIN ql_session_day d ON d.id=p.session_day_id",
            "JOIN ql_session s ON s.id=d.session_id",
            "JOIN ql_customer c ON c.id=p.customer_id",
            "<where>",
            "<if test='sessionNumber != null'> AND s.session_number=#{sessionNumber}</if>",
            "<if test='attendanceStatus != null and attendanceStatus != \"\"'> AND p.attendance_status=#{attendanceStatus}</if>",
            "<if test='keyword != null and keyword != \"\"'> AND (c.nickname LIKE CONCAT('%',#{keyword},'%') OR c.real_name LIKE CONCAT('%',#{keyword},'%') OR CONVERT(c.customer_no USING utf8mb4) LIKE CONCAT('%',#{keyword},'%'))</if>",
            "</where>",
            "ORDER BY d.activity_date DESC, d.day_no, c.nickname",
            "</script>"})
    List<Map<String, Object>> selectList(@Param("sessionNumber") Integer sessionNumber,
                                         @Param("attendanceStatus") String attendanceStatus,
                                         @Param("keyword") String keyword);

    @Update("UPDATE ql_participation_day SET attendance_status=#{status}, check_in_source='web_admin', " +
            "checked_in_at=CASE WHEN #{status} IN ('checked_in','late','left_early') THEN #{now} ELSE checked_in_at END, " +
            "checked_in_by=#{operatorId}, change_reason=#{reason}, revision=revision+1, updated_at=#{now} WHERE id=#{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("reason") String reason, @Param("operatorId") Long operatorId,
                     @Param("now") Date now);
}
