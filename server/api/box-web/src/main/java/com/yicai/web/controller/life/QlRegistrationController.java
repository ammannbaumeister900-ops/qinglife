package com.yicai.web.controller.life;

import com.yicai.common.annotation.Log;
import com.yicai.common.annotation.RepeatSubmit;
import com.yicai.common.core.controller.BaseController;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.QlPaymentBo;
import com.yicai.life.domain.bo.QlRegistrationBo;
import com.yicai.life.domain.vo.QlRegistrationVo;
import com.yicai.life.service.IQlRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Validated
@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RequestMapping("/life/registration")
public class QlRegistrationController extends BaseController {
    private final IQlRegistrationService registrationService;

    @PreAuthorize("@ss.hasPermi('life:registration:list')")
    @GetMapping("/list")
    public TableDataInfo<QlRegistrationVo> list(QlRegistrationBo bo) {
        return registrationService.queryPageList(bo);
    }

    @PreAuthorize("@ss.hasPermi('life:registration:query')")
    @GetMapping("/{id}")
    public AjaxResult<QlRegistrationVo> getInfo(@PathVariable String id) {
        return AjaxResult.success(registrationService.queryById(id));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:add')")
    @Log(title = "报名管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody QlRegistrationBo bo) {
        return toAjax(registrationService.insertByBo(bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:edit')")
    @Log(title = "报名管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody QlRegistrationBo bo) {
        return toAjax(registrationService.updateByBo(bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:payment')")
    @Log(title = "人工付款登记", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/payment")
    public AjaxResult<Void> changePayment(@PathVariable String id, @Valid @RequestBody QlPaymentBo bo) {
        return toAjax(registrationService.changePayment(id, bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:payment')")
    @Log(title = "报名订单人工付款登记", businessType = BusinessType.UPDATE)
    @PutMapping("/batch/{batchId}/payment")
    public AjaxResult<Void> changeBatchPayment(@PathVariable String batchId, @Valid @RequestBody QlPaymentBo bo) {
        return toAjax(registrationService.changeBatchPayment(batchId, bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:edit')")
    @Log(title = "取消整单报名", businessType = BusinessType.UPDATE)
    @PutMapping("/batch/{batchId}/cancel")
    public AjaxResult<Void> cancelBatch(@PathVariable String batchId,@RequestBody java.util.Map<String,String> body) {
        return toAjax(registrationService.cancelBatch(batchId,body.get("reason"),currentUserId()));
    }

    private Long currentUserId() {
        return SecurityUtils.getLoginUser().getUser().getUserId();
    }
}
