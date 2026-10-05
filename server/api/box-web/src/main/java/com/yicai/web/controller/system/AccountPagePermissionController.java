package com.yicai.web.controller.system;
import com.yicai.common.annotation.Log;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.system.domain.bo.AccountPagePermissionBo;
import com.yicai.system.service.AccountPagePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/system/account-permissions")
public class AccountPagePermissionController {
    private final AccountPagePermissionService pages;
    @PreAuthorize("@ss.hasPermi('system:accountPermission:list')") @GetMapping
    public AjaxResult<?> list(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize){return AjaxResult.success(pages.list(keyword,pageNum,pageSize));}
    @PreAuthorize("@ss.hasPermi('system:accountPermission:list')") @GetMapping("/{userId}")
    public AjaxResult<?> detail(@PathVariable Long userId){return AjaxResult.success(pages.detail(userId));}
    @PreAuthorize("@ss.hasPermi('system:accountPermission:edit')") @Log(title="账号页面权限",businessType=BusinessType.GRANT) @PutMapping("/{userId}")
    public AjaxResult<?> save(@PathVariable Long userId,@Validated @RequestBody AccountPagePermissionBo bo){pages.save(userId,bo,SecurityUtils.getLoginUser().getUser().getUserId());return AjaxResult.success();}
}
