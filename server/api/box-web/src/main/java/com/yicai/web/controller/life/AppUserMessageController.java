package com.yicai.web.controller.life;

import java.util.List;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import javax.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.yicai.common.annotation.RepeatSubmit;
import com.yicai.common.annotation.Log;
import com.yicai.common.core.controller.BaseController;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import com.yicai.common.enums.BusinessType;
import com.yicai.life.domain.vo.AppUserMessageVo;
import com.yicai.life.domain.bo.AppUserMessageBo;
import com.yicai.life.service.IAppUserMessageService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 用户消息Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/userMessage")
public class AppUserMessageController extends BaseController {

    private final IAppUserMessageService iAppUserMessageService;

    /**
     * 查询用户消息列表
     */
    @PreAuthorize("@ss.hasPermi('life:userMessage:list')")
    @GetMapping("/list")
    public TableDataInfo<AppUserMessageVo> list(@Validated AppUserMessageBo bo) {
        return iAppUserMessageService.queryPageList(bo);
    }

    /**
     * 导出用户消息列表
     */
    @PreAuthorize("@ss.hasPermi('life:userMessage:export')")
    @Log(title = "用户消息", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppUserMessageVo> export(@Validated AppUserMessageBo bo) {
        List<AppUserMessageVo> list = iAppUserMessageService.queryList(bo);
        ExcelUtil<AppUserMessageVo> util = new ExcelUtil<AppUserMessageVo>(AppUserMessageVo.class);
        return util.exportExcel(list, "用户消息");
    }

    /**
     * 获取用户消息详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:userMessage:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppUserMessageVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppUserMessageService.queryById(id));
    }

    /**
     * 新增用户消息
     */
    @PreAuthorize("@ss.hasPermi('life:userMessage:add')")
    @Log(title = "用户消息", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppUserMessageBo bo) {
        return toAjax(iAppUserMessageService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改用户消息
     */
    @PreAuthorize("@ss.hasPermi('life:userMessage:edit')")
    @Log(title = "用户消息", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppUserMessageBo bo) {
        return toAjax(iAppUserMessageService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除用户消息
     */
    @PreAuthorize("@ss.hasPermi('life:userMessage:remove')")
    @Log(title = "用户消息" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppUserMessageService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
