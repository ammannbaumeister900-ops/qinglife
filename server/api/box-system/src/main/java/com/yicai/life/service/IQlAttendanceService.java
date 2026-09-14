package com.yicai.life.service;

import com.yicai.life.domain.bo.QlAttendanceBo;

import java.util.List;
import java.util.Map;

public interface IQlAttendanceService {
    List<Map<String, Object>> list(Integer sessionNumber, String attendanceStatus, String keyword);
    boolean updateStatus(String id, QlAttendanceBo bo, Long operatorId);
}
