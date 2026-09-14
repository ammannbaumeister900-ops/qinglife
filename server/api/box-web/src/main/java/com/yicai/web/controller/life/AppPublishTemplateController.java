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
import com.yicai.life.domain.vo.AppPublishTemplateVo;
import com.yicai.life.domain.bo.AppPublishTemplateBo;
import com.yicai.life.service.IAppPublishTemplateService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 动态模板Controller
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/publishTemplate")
public class AppPublishTemplateController extends BaseController {

    private final IAppPublishTemplateService iAppPublishTemplateService;

    /**
     * 查询动态模板列表
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplate:list')")
    @GetMapping("/list")
    public TableDataInfo<AppPublishTemplateVo> list(@Validated AppPublishTemplateBo bo) {
        return iAppPublishTemplateService.queryPageList(bo);
    }

    /**
     * 导出动态模板列表
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplate:export')")
    @Log(title = "动态模板", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppPublishTemplateVo> export(@Validated AppPublishTemplateBo bo) {
        List<AppPublishTemplateVo> list = iAppPublishTemplateService.queryList(bo);
        ExcelUtil<AppPublishTemplateVo> util = new ExcelUtil<AppPublishTemplateVo>(AppPublishTemplateVo.class);
        return util.exportExcel(list, "动态模板");
    }

    /**
     * 获取动态模板详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplate:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppPublishTemplateVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppPublishTemplateService.queryById(id));
    }

    /**
     * 新增动态模板
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplate:add')")
    @Log(title = "动态模板", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppPublishTemplateBo bo) {
        return toAjax(iAppPublishTemplateService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改动态模板
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplate:edit')")
    @Log(title = "动态模板", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppPublishTemplateBo bo) {
        return toAjax(iAppPublishTemplateService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除动态模板
     */
    @PreAuthorize("@ss.hasPermi('life:publishTemplate:remove')")
    @Log(title = "动态模板" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppPublishTemplateService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }

    /**
     * 获取可用模板
     */
    @GetMapping("/getTemplateList")
    public AjaxResult getTemplateList(){
        AppPublishTemplateBo bo = new AppPublishTemplateBo();
        bo.setStatus(1L);
        List<AppPublishTemplateVo> list = iAppPublishTemplateService.queryList(bo);
        return AjaxResult.success(list);
    }
}
