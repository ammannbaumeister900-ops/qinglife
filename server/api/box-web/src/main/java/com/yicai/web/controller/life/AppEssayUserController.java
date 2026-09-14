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
import com.yicai.life.domain.vo.AppEssayUserVo;
import com.yicai.life.domain.bo.AppEssayUserBo;
import com.yicai.life.service.IAppEssayUserService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 文章用户关联Controller
 *
 * @author zhixia
 * @date 2022-05-16
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/essayUser")
public class AppEssayUserController extends BaseController {

    private final IAppEssayUserService iAppEssayUserService;

    /**
     * 查询文章用户关联列表
     */
    @PreAuthorize("@ss.hasPermi('life:essayUser:list')")
    @GetMapping("/list")
    public TableDataInfo<AppEssayUserVo> list(@Validated AppEssayUserBo bo) {
        return iAppEssayUserService.queryPageList(bo);
    }

    /**
     * 导出文章用户关联列表
     */
    @PreAuthorize("@ss.hasPermi('life:essayUser:export')")
    @Log(title = "文章用户关联", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppEssayUserVo> export(@Validated AppEssayUserBo bo) {
        List<AppEssayUserVo> list = iAppEssayUserService.queryList(bo);
        ExcelUtil<AppEssayUserVo> util = new ExcelUtil<AppEssayUserVo>(AppEssayUserVo.class);
        return util.exportExcel(list, "文章用户关联");
    }

    /**
     * 获取文章用户关联详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:essayUser:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppEssayUserVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppEssayUserService.queryById(id));
    }

    /**
     * 新增文章用户关联
     */
    @PreAuthorize("@ss.hasPermi('life:essayUser:add')")
    @Log(title = "文章用户关联", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppEssayUserBo bo) {
        return toAjax(iAppEssayUserService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改文章用户关联
     */
    @PreAuthorize("@ss.hasPermi('life:essayUser:edit')")
    @Log(title = "文章用户关联", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppEssayUserBo bo) {
        return toAjax(iAppEssayUserService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除文章用户关联
     */
    @PreAuthorize("@ss.hasPermi('life:essayUser:remove')")
    @Log(title = "文章用户关联" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppEssayUserService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
