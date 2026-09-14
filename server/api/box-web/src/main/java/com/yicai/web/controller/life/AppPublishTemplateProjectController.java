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
import com.yicai.life.domain.vo.AppPublishTemplateProjectVo;
import com.yicai.life.domain.bo.AppPublishTemplateProjectBo;
import com.yicai.life.service.IAppPublishTemplateProjectService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 状态模板项目Controller
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/publishTemplateProject")
public class AppPublishTemplateProjectController extends BaseController {

    private final IAppPublishTemplateProjectService iAppPublishTemplateProjectService;

    /**
     * 查询状态模板项目列表
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplateProject:list')")
    @GetMapping("/list")
    public TableDataInfo<AppPublishTemplateProjectVo> list(@Validated AppPublishTemplateProjectBo bo) {
        return iAppPublishTemplateProjectService.queryPageList(bo);
    }

    /**
     * 导出状态模板项目列表
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplateProject:export')")
    @Log(title = "状态模板项目", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppPublishTemplateProjectVo> export(@Validated AppPublishTemplateProjectBo bo) {
        List<AppPublishTemplateProjectVo> list = iAppPublishTemplateProjectService.queryList(bo);
        ExcelUtil<AppPublishTemplateProjectVo> util = new ExcelUtil<AppPublishTemplateProjectVo>(AppPublishTemplateProjectVo.class);
        return util.exportExcel(list, "状态模板项目");
    }

    /**
     * 获取状态模板项目详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplateProject:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppPublishTemplateProjectVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppPublishTemplateProjectService.queryById(id));
    }

    /**
     * 新增状态模板项目
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplateProject:add')")
    @Log(title = "状态模板项目", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppPublishTemplateProjectBo bo) {
        return toAjax(iAppPublishTemplateProjectService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改状态模板项目
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplateProject:edit')")
    @Log(title = "状态模板项目", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppPublishTemplateProjectBo bo) {
        return toAjax(iAppPublishTemplateProjectService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除状态模板项目
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplateProject:remove')")
    @Log(title = "状态模板项目" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppPublishTemplateProjectService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
