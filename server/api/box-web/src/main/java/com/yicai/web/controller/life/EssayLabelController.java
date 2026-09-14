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
import com.yicai.life.domain.vo.EssayLabelVo;
import com.yicai.life.domain.bo.EssayLabelBo;
import com.yicai.life.service.IEssayLabelService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 文章标签Controller
 *
 * @author zhixia
 * @date 2022-02-25
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/essayLabel")
public class EssayLabelController extends BaseController {

    private final IEssayLabelService iEssayLabelService;

    /**
     * 查询文章标签列表
     */
    @PreAuthorize("@ss.hasPermi('life:label:list')")
    @GetMapping("/list")
    public TableDataInfo<EssayLabelVo> list(@Validated EssayLabelBo bo) {
        return iEssayLabelService.queryPageList(bo);
    }

    /**
     * 导出文章标签列表
     */
    @PreAuthorize("@ss.hasPermi('life:label:export')")
    @Log(title = "文章标签", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<EssayLabelVo> export(@Validated EssayLabelBo bo) {
        List<EssayLabelVo> list = iEssayLabelService.queryList(bo);
        ExcelUtil<EssayLabelVo> util = new ExcelUtil<EssayLabelVo>(EssayLabelVo.class);
        return util.exportExcel(list, "文章标签");
    }

    /**
     * 获取文章标签详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:label:query')")
    @GetMapping("/{id}")
    public AjaxResult<EssayLabelVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iEssayLabelService.queryById(id));
    }

    /**
     * 新增文章标签
     */
    @PreAuthorize("@ss.hasPermi('life:label:add')")
    @Log(title = "文章标签", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody EssayLabelBo bo) {
        return toAjax(iEssayLabelService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改文章标签
     */
    @PreAuthorize("@ss.hasPermi('life:label:edit')")
    @Log(title = "文章标签", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody EssayLabelBo bo) {
        return toAjax(iEssayLabelService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除文章标签
     */
    @PreAuthorize("@ss.hasPermi('life:label:remove')")
    @Log(title = "文章标签" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iEssayLabelService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
