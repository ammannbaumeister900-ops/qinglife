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
import com.yicai.life.domain.vo.AppUserCommentVo;
import com.yicai.life.domain.bo.AppUserCommentBo;
import com.yicai.life.service.IAppUserCommentService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 动态评论Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/userComment")
public class AppUserCommentController extends BaseController {

    private final IAppUserCommentService iAppUserCommentService;

    /**
     * 查询动态评论列表
     */
    @PreAuthorize("@ss.hasPermi('life:userComment:list')")
    @GetMapping("/list")
    public TableDataInfo<AppUserCommentVo> list(@Validated AppUserCommentBo bo) {
        return iAppUserCommentService.queryPageList(bo);
    }

    /**
     * 导出动态评论列表
     */
    @PreAuthorize("@ss.hasPermi('life:userComment:export')")
    @Log(title = "动态评论", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppUserCommentVo> export(@Validated AppUserCommentBo bo) {
        List<AppUserCommentVo> list = iAppUserCommentService.queryList(bo);
        ExcelUtil<AppUserCommentVo> util = new ExcelUtil<AppUserCommentVo>(AppUserCommentVo.class);
        return util.exportExcel(list, "动态评论");
    }

    /**
     * 获取动态评论详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:userComment:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppUserCommentVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppUserCommentService.queryById(id));
    }

    /**
     * 新增动态评论
     */
    @PreAuthorize("@ss.hasPermi('life:userComment:add')")
    @Log(title = "动态评论", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppUserCommentBo bo) {
        return toAjax(iAppUserCommentService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改动态评论
     */
    @PreAuthorize("@ss.hasPermi('life:userComment:edit')")
    @Log(title = "动态评论", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppUserCommentBo bo) {
        return toAjax(iAppUserCommentService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除动态评论
     */
    @PreAuthorize("@ss.hasPermi('life:userComment:remove')")
    @Log(title = "动态评论" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppUserCommentService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
