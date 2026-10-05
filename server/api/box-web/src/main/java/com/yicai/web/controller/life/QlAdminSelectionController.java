package com.yicai.web.controller.life;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.service.QlAdminSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/life/selection")
public class QlAdminSelectionController {
    private final QlAdminSelectionService selections;
    @PreAuthorize("@ss.hasAnyPermi('life:pass:list,life:registration:list')") @GetMapping("/customers")
    public AjaxResult<?> customers(){return AjaxResult.success(selections.customers(userId()));}
    @PreAuthorize("@ss.hasPermi('life:registration:list')") @GetMapping("/sessions")
    public AjaxResult<?> sessions(){return AjaxResult.success(selections.sessions(userId()));}
    @PreAuthorize("@ss.hasAnyPermi('life:registration:payment,life:registration:settlement:revoke')") @GetMapping("/registrations/{id}/passes")
    public AjaxResult<?> passes(@PathVariable String id){return AjaxResult.success(selections.registrationPasses(id,userId()));}
    private Long userId(){return SecurityUtils.getLoginUser().getUser().getUserId();}
}
