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
import com.yicai.life.domain.bo.QlPaymentConfirmationBo;
import com.yicai.life.domain.bo.QlRegistrationBo;
import com.yicai.life.domain.bo.QlSettlementBo;
import com.yicai.life.domain.bo.QlSettlementReversalBo;
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
    private final com.yicai.life.service.QlOfflineReceiptService offlineReceipts;
    private final com.yicai.life.service.QlParticipantFinanceService participantFinance;
    private final com.yicai.life.service.QlParticipantPassService participantPass;

    @PreAuthorize("@ss.hasPermi('life:registration:list')")
    @GetMapping("/list")
    public TableDataInfo<QlRegistrationVo> list(@Valid QlRegistrationBo bo) {
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
    @Log(title = "报名付款确认", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/payment-confirmation")
    public AjaxResult<Void> confirmPayment(@PathVariable String id, @Valid @RequestBody QlPaymentConfirmationBo bo) {
        return toAjax(registrationService.confirmPayment(id, bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:payment') && (#bo.mode != 'participant' || @ss.hasPermi('life:registration:edit')) && (#bo.mode != 'participant' || #bo.settlementType != 'pass' || (@ss.hasPermi('life:pass:consume') && @ss.hasPermi('life:pass:verify')))")
    @Log(title = "最终结算确认", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/settlement")
    public AjaxResult<?> confirmSettlement(@PathVariable String id, @Valid @RequestBody QlSettlementBo bo) {
        if("participant".equals(bo.getMode()))return AjaxResult.success("pass".equals(bo.getSettlementType())?participantPass.settle(id,bo,currentUserId()):participantFinance.settle(id,bo,currentUserId()));
        return toAjax(registrationService.confirmSettlement(id, bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:settlement:revoke') && (#bo.mode != 'participant' || @ss.hasPermi('life:pass:restore'))")
    @Log(title = "撤销最终结算", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/settlement/{settlementId}/revoke")
    public AjaxResult<?> revokeSettlement(@PathVariable String id, @PathVariable String settlementId,
                                              @Valid @RequestBody QlSettlementReversalBo bo) {
        if("participant".equals(bo.getMode()))return AjaxResult.success(participantPass.reverse(id,settlementId,bo,currentUserId()));
        return toAjax(registrationService.revokeSettlement(id, settlementId, bo.getReason(), currentUserId()));
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

    @PreAuthorize("@ss.hasPermi('life:registration:payment') && @ss.hasPermi('life:registration:edit')")
    @Log(title = "核实线下到账并确认整单报名", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{id}/offline-receipt")
    public AjaxResult<?> confirmOfflineReceipt(@PathVariable String id,
            @Valid @RequestBody com.yicai.life.domain.bo.QlOfflineReceiptBo bo) {
        if(Boolean.TRUE.equals(bo.getCaptureOnly()))return AjaxResult.success(participantFinance.capture(id,bo,currentUserId()));
        return toAjax(offlineReceipts.confirm(id,bo,currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:payment') && @ss.hasPermi('life:registration:query')")
    @GetMapping("/batch/{batchId}/finance")
    public AjaxResult<java.util.Map<String,Object>> finance(@PathVariable String batchId) {
        return AjaxResult.success(participantFinance.finance(batchId));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:payment') && @ss.hasPermi('life:registration:edit')")
    @Log(title="逐人到账分配",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PostMapping("/batch/{batchId}/receipt-allocations")
    public AjaxResult<java.util.Map<String,Object>> allocate(@PathVariable String batchId,
            @Valid @RequestBody com.yicai.life.domain.bo.QlReceiptAllocationBo bo) {
        return AjaxResult.success(participantFinance.allocate(batchId,bo,currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:payment')")
    @GetMapping("/{id}/offline-receipts")
    public AjaxResult<java.util.List<java.util.Map<String,Object>>> offlineReceipts(@PathVariable String id) {
        return AjaxResult.success(offlineReceipts.list(id));
    }

    @PreAuthorize("@ss.hasPermi('life:registration:payment')")
    @GetMapping("/{id}/offline-receipt-images/{imageId}")
    public org.springframework.http.ResponseEntity<byte[]> receiptImage(@PathVariable String id,@PathVariable String imageId) {
        java.util.Map<String,Object> image=offlineReceipts.image(id,imageId);
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store")
            .header("X-Content-Type-Options","nosniff")
            .contentType(org.springframework.http.MediaType.parseMediaType(String.valueOf(image.get("mime"))))
            .body((byte[])image.get("data"));
    }

    private Long currentUserId() {
        return SecurityUtils.getLoginUser().getUser().getUserId();
    }
}
