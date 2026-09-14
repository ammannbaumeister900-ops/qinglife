package com.yicai.life.service.impl;

import cn.hutool.core.util.StrUtil;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlAttendanceBo;
import com.yicai.life.mapper.QlAttendanceMapper;
import com.yicai.life.service.IQlAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class QlAttendanceServiceImpl implements IQlAttendanceService {
    private static final List<String> STATUSES = Arrays.asList(
            "not_arrived", "checked_in", "late", "absent", "left_early", "cancelled");
    private final QlAttendanceMapper attendanceMapper;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final com.yicai.life.service.QlAttendanceAudit audit;

    @Override
    public List<Map<String, Object>> list(Integer sessionNumber, String attendanceStatus, String keyword) {
        return attendanceMapper.selectList(sessionNumber, attendanceStatus, keyword);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean updateStatus(String id, QlAttendanceBo bo, Long operatorId) {
        if (!STATUSES.contains(bo.getAttendanceStatus())) {
            throw new CustomException("签到状态不合法", 400);
        }
        if (Arrays.asList("absent", "cancelled").contains(bo.getAttendanceStatus())
                && StrUtil.isBlank(bo.getChangeReason())) {
            throw new CustomException("缺席或取消必须填写原因", 400);
        }
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM ql_participation_day WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new CustomException("签到记录不存在", 404);
        Map<String,Object> before = rows.get(0);
        if (bo.getAttendanceStatus().equals(before.get("attendance_status"))
                && java.util.Objects.equals(bo.getChangeReason(), before.get("change_reason"))) return true;
        boolean updated = attendanceMapper.updateStatus(id, bo.getAttendanceStatus(), bo.getChangeReason(),
                operatorId, new Date()) == 1;
        if (updated) audit.record(id, before, bo.getAttendanceStatus(), bo.getChangeReason(), operatorId, "web_admin");
        return updated;
    }
}
