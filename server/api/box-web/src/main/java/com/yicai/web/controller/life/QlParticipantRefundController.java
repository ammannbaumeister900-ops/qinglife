package com.yicai.web.controller.life;

import com.yicai.common.annotation.Log;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.*;
import com.yicai.life.service.QlParticipantRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;

@Validated @RestController @RequiredArgsConstructor
@RequestMapping("/life/registration")
public class QlParticipantRefundController {
    private final QlParticipantRefundService refunds;
    @PreAuthorize("@ss.hasPermi('life:registration:refund')")
    @Log(title="逐人退款申请",businessType=BusinessType.INSERT,isSaveRequestData=false)
    @PostMapping("/{id}/refund-requests")
    public AjaxResult<Map<String,Object>> request(@PathVariable String id,@Valid @RequestBody QlRefundRequestBo bo) {
        return AjaxResult.success(refunds.request(id,bo,operator()));
    }
    @PreAuthorize("@ss.hasPermi('life:registration:refund')")
    @Log(title="逐人退款处理中",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/{id}/refund-requests/{requestId}/processing")
    public AjaxResult<Map<String,Object>> processing(@PathVariable String id,@PathVariable String requestId,@Valid @RequestBody QlFinanceActionBo bo) {
        return AjaxResult.success(refunds.transition(id,requestId,"processing",bo,operator()));
    }
    @PreAuthorize("@ss.hasPermi('life:registration:refund')")
    @Log(title="取消退款申请",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/{id}/refund-requests/{requestId}/cancel")
    public AjaxResult<Map<String,Object>> cancelRequest(@PathVariable String id,@PathVariable String requestId,@Valid @RequestBody QlFinanceActionBo bo) {
        return AjaxResult.success(refunds.transition(id,requestId,"cancel",bo,operator()));
    }
    @PreAuthorize("@ss.hasPermi('life:registration:refund')")
    @Log(title="退款失败核对",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/{id}/refund-requests/{requestId}/fail")
    public AjaxResult<Map<String,Object>> fail(@PathVariable String id,@PathVariable String requestId,@Valid @RequestBody QlFinanceActionBo bo) {
        return AjaxResult.success(refunds.transition(id,requestId,"fail",bo,operator()));
    }
    @PreAuthorize("@ss.hasPermi('life:registration:refund')")
    @Log(title="记录实际线下退款",businessType=BusinessType.INSERT,isSaveRequestData=false)
    @PostMapping("/{id}/refund-requests/{requestId}/payments")
    public AjaxResult<Map<String,Object>> payment(@PathVariable String id,@PathVariable String requestId,@Valid @RequestBody QlRefundPaymentBo bo) {
        return AjaxResult.success(refunds.payment(id,requestId,bo,operator()));
    }
    @PreAuthorize("@ss.hasPermi('life:registration:cancel')")
    @Log(title="逐人取消报名",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/{id}/cancel")
    public AjaxResult<Map<String,Object>> cancel(@PathVariable String id,@Valid @RequestBody QlFinanceActionBo bo) {
        return AjaxResult.success(refunds.cancel(id,bo,operator()));
    }
    private Long operator(){return SecurityUtils.getLoginUser().getUser().getUserId();}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class,javax.validation.ConstraintViolationException.class})
    public AjaxResult<Void> invalidInput(Exception error){return AjaxResult.error(400,"退款或取消参数无效，请核对金额、标识和允许字段");}
}
