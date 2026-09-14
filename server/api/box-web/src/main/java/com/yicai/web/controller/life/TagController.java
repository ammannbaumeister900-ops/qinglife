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
import com.yicai.life.domain.vo.TagVo;
import com.yicai.life.domain.bo.TagBo;
import com.yicai.life.service.ITagService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 动态标签Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/tag")
public class TagController extends BaseController {

    private final ITagService iTagService;

    /**
     * 查询动态标签列表
     */
    @PreAuthorize("@ss.hasPermi('life:tag:list')")
    @GetMapping("/list")
    public TableDataInfo<TagVo> list(@Validated TagBo bo) {
        return iTagService.queryPageList(bo);
    }

    /**
     * 导出动态标签列表
     */
    @PreAuthorize("@ss.hasPermi('life:tag:export')")
    @Log(title = "动态标签", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<TagVo> export(@Validated TagBo bo) {
        List<TagVo> list = iTagService.queryList(bo);
        ExcelUtil<TagVo> util = new ExcelUtil<TagVo>(TagVo.class);
        return util.exportExcel(list, "动态标签");
    }

    /**
     * 获取动态标签详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:tag:query')")
    @GetMapping("/{id}")
    public AjaxResult<TagVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iTagService.queryById(id));
    }

    /**
     * 新增动态标签
     */
    @PreAuthorize("@ss.hasPermi('life:tag:add')")
    @Log(title = "动态标签", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody TagBo bo) {
        return toAjax(iTagService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改动态标签
     */
    @PreAuthorize("@ss.hasPermi('life:tag:edit')")
    @Log(title = "动态标签", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody TagBo bo) {
        return toAjax(iTagService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除动态标签
     */
    @PreAuthorize("@ss.hasPermi('life:tag:remove')")
    @Log(title = "动态标签" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iTagService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
