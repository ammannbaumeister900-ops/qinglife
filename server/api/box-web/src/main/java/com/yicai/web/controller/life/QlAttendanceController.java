package com.yicai.web.controller.life;

import com.yicai.common.annotation.Log;
import com.yicai.common.core.controller.BaseController;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.utils.PageUtils;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.QlAttendanceBo;
import com.yicai.life.service.IQlAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RequestMapping("/life/attendance")
public class QlAttendanceController extends BaseController {
    private final IQlAttendanceService attendanceService;
    private final com.yicai.life.service.QlAttendanceAudit attendanceAudit;

    @PreAuthorize("@ss.hasPermi('life:attendance:list')")
    @GetMapping("/{id}/history")
    public AjaxResult history(@PathVariable String id) {
        return AjaxResult.success(attendanceAudit.history(id));
    }

    @PreAuthorize("@ss.hasPermi('life:attendance:list')")
    @GetMapping("/list")
    public TableDataInfo<Map<String, Object>> list(Integer sessionNumber, String attendanceStatus, String keyword) {
        return PageUtils.buildDataInfo(attendanceService.list(sessionNumber, attendanceStatus, keyword));
    }

    @PreAuthorize("@ss.hasPermi('life:attendance:edit')")
    @Log(title = "活动签到", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public AjaxResult<Void> updateStatus(@PathVariable String id, @Valid @RequestBody QlAttendanceBo bo) {
        Long operatorId = SecurityUtils.getLoginUser().getUser().getUserId();
        return toAjax(attendanceService.updateStatus(id, bo, operatorId));
    }
}
