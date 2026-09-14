package com.yicai.web.controller.life;

import java.util.List;
import java.util.Arrays;

import com.yicai.life.domain.bo.AppUserInfoBo;
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
import com.yicai.life.domain.vo.EssayVo;
import com.yicai.life.domain.bo.EssayBo;
import com.yicai.life.service.IEssayService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 文章Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/essay")
public class EssayController extends BaseController {

    private final IEssayService iEssayService;

    /**
     * 查询文章列表
     */
    @PreAuthorize("@ss.hasPermi('life:essay:list')")
    @GetMapping("/list")
    public TableDataInfo<EssayVo> list(@Validated EssayBo bo) {
        return iEssayService.queryPageList(bo);
    }

    /**
     * 导出文章列表
     */
    @PreAuthorize("@ss.hasPermi('life:essay:export')")
    @Log(title = "文章", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<EssayVo> export(@Validated EssayBo bo) {
        List<EssayVo> list = iEssayService.queryList(bo);
        ExcelUtil<EssayVo> util = new ExcelUtil<EssayVo>(EssayVo.class);
        return util.exportExcel(list, "文章");
    }

    /**
     * 获取文章详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:essay:query')")
    @GetMapping("/{id}")
    public AjaxResult<EssayVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iEssayService.queryById(id));
    }

    /**
     * 新增文章
     */
    @PreAuthorize("@ss.hasPermi('life:essay:add')")
    @Log(title = "文章", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody EssayBo bo) {
        return toAjax(iEssayService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改文章
     */
    @PreAuthorize("@ss.hasPermi('life:essay:edit')")
    @Log(title = "文章", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody EssayBo bo) {
        return toAjax(iEssayService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除文章
     */
    @PreAuthorize("@ss.hasPermi('life:essay:remove')")
    @Log(title = "文章" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iEssayService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }

    /**
     * 修改文章状态
     */
    @PreAuthorize("@ss.hasPermi('life:essay:edit')")
    @Log(title = "文章", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/changeStatus")
    public AjaxResult<Void> changeStatus(@Validated(EditGroup.class) @RequestBody EssayBo bo) {
        return toAjax(iEssayService.changeStatus(bo) ? 1 : 0);
    }
}
