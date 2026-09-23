package com.yicai.web.controller.life;

import com.yicai.common.annotation.Log;
import com.yicai.common.annotation.RepeatSubmit;
import com.yicai.common.core.controller.BaseController;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.QlPassAccountBo;
import com.yicai.life.domain.bo.QlPassAdjustmentBo;
import com.yicai.life.service.QlPassService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/life/pass")
public class QlPassController extends BaseController {
    private final QlPassService passService;

    @PreAuthorize("@ss.hasPermi('life:pass:list')")
    @GetMapping("/list")
    public AjaxResult<List<Map<String,Object>>> list(@RequestParam(required = false) String customerId) {
        return AjaxResult.success(passService.list(customerId));
    }

    @PreAuthorize("@ss.hasPermi('life:pass:query')")
    @GetMapping("/{id}/ledger")
    public AjaxResult<List<Map<String,Object>>> ledger(@PathVariable String id) {
        return AjaxResult.success(passService.ledger(id));
    }

    @PreAuthorize("@ss.hasPermi('life:pass:add')")
    @Log(title = "卡次开户", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public AjaxResult<String> open(@Validated @RequestBody QlPassAccountBo bo) {
        return AjaxResult.success("操作成功", passService.open(bo, userId()));
    }

    @PreAuthorize("@ss.hasPermi('life:pass:adjust')")
    @Log(title = "卡次调整", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/adjustments")
    public AjaxResult<Integer> adjust(@PathVariable String id, @Validated @RequestBody QlPassAdjustmentBo bo) {
        return AjaxResult.success(passService.adjust(id, bo, userId()));
    }

    private Long userId() { return SecurityUtils.getLoginUser().getUser().getUserId(); }
}
