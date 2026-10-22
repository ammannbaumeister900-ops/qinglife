package com.yicai.web.controller.life;

import com.yicai.common.annotation.Log;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.*;
import com.yicai.life.service.QlParticipantPassService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;

@Validated @RestController @RequiredArgsConstructor
@RequestMapping("/life/registration")
public class QlParticipantPassController {
    private final QlParticipantPassService passes;
    @PreAuthorize("@ss.hasPermi('life:registration:payment') && @ss.hasPermi('life:registration:edit') && @ss.hasPermi('life:pass:consume') && @ss.hasPermi('life:pass:verify')")
    @Log(title="运营卡次核实",businessType=BusinessType.INSERT,isSaveRequestData=false)
    @PostMapping("/{id}/pass-verifications")
    public AjaxResult<Map<String,Object>> verify(@PathVariable String id,@Valid @RequestBody QlPassVerificationBo bo){
        return AjaxResult.success(passes.verify(id,bo,SecurityUtils.getLoginUser().getUser().getUserId()));
    }
    @PreAuthorize("@ss.hasPermi('life:registration:payment') && @ss.hasPermi('life:registration:edit') && @ss.hasPermi('life:pass:consume') && @ss.hasPermi('life:pass:verify')")
    @Log(title="撤销未使用卡次核实",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/{id}/pass-verifications/{verificationId}/revoke")
    public AjaxResult<Map<String,Object>> revoke(@PathVariable String id,@PathVariable String verificationId,@Valid @RequestBody QlFinanceActionBo bo){
        return AjaxResult.success(passes.revokeVerification(id,verificationId,bo,SecurityUtils.getLoginUser().getUser().getUserId()));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,javax.validation.ConstraintViolationException.class})
    public AjaxResult<Void> invalidInput(Exception error){return AjaxResult.error(400,"卡次核实参数无效，请核对允许字段");}
}
