package com.yicai.web.controller.life;

import com.yicai.common.annotation.Log;
import com.yicai.common.core.controller.BaseController;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.PageUtils;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.QlReportHandleBo;
import com.yicai.life.mapper.QlReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RequestMapping("/life/report")
public class QlReportController extends BaseController {
    private final QlReportMapper reportMapper;

    @PreAuthorize("@ss.hasPermi('life:report:list')")
    @GetMapping("/list")
    public TableDataInfo<Map<String, Object>> list(String status) {
        return PageUtils.buildDataInfo(reportMapper.selectList(status));
    }

    @PreAuthorize("@ss.hasPermi('life:report:edit')")
    @Log(title = "内容举报处理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult<Void> handle(@PathVariable String id, @Valid @RequestBody QlReportHandleBo bo) {
        if (!Arrays.asList("processing", "resolved", "rejected").contains(bo.getStatus())) {
            throw new CustomException("处理状态不合法");
        }
        Long operatorId = SecurityUtils.getLoginUser().getUser().getUserId();
        return toAjax(reportMapper.handle(id, bo.getStatus(), bo.getHandleResult(), operatorId, new Date()));
    }
}
