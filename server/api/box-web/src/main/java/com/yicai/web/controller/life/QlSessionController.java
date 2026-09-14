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
import com.yicai.life.domain.bo.QlSessionBo;
import com.yicai.life.domain.vo.QlSessionVo;
import com.yicai.life.service.IQlSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RequestMapping("/life/session")
public class QlSessionController extends BaseController {
    private final IQlSessionService sessionService;
    private final com.yicai.life.service.IQlMiniAppService miniAppService;

    @PreAuthorize("@ss.hasPermi('life:session:edit')")
    @PostMapping("/{id}/invitation")
    public AjaxResult<java.util.Map<String,Object>> invite(@PathVariable String id) { return AjaxResult.success(miniAppService.createInvitation(null, id, true)); }

    @PreAuthorize("@ss.hasPermi('life:session:list')")
    @GetMapping("/list")
    public TableDataInfo<QlSessionVo> list(QlSessionBo bo) {
        return sessionService.queryPageList(bo);
    }

    @PreAuthorize("@ss.hasPermi('life:session:query')")
    @GetMapping("/{id}")
    public AjaxResult<QlSessionVo> getInfo(@PathVariable String id) {
        return AjaxResult.success(sessionService.queryById(id));
    }

    @PreAuthorize("@ss.hasPermi('life:session:add')")
    @Log(title = "期次管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody QlSessionBo bo) {
        return toAjax(sessionService.insertByBo(bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:session:edit')")
    @Log(title = "期次管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody QlSessionBo bo) {
        return toAjax(sessionService.updateByBo(bo, currentUserId()));
    }

    private Long currentUserId() {
        return SecurityUtils.getLoginUser().getUser().getUserId();
    }
}
