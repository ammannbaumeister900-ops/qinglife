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
import com.yicai.life.domain.vo.AppUserPublishPraiseVo;
import com.yicai.life.domain.bo.AppUserPublishPraiseBo;
import com.yicai.life.service.IAppUserPublishPraiseService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 用户动态点赞记录Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/userPublishPraise")
public class AppUserPublishPraiseController extends BaseController {

    private final IAppUserPublishPraiseService iAppUserPublishPraiseService;

    /**
     * 查询用户动态点赞记录列表
     */
    @PreAuthorize("@ss.hasPermi('life:userPublishPraise:list')")
    @GetMapping("/list")
    public TableDataInfo<AppUserPublishPraiseVo> list(@Validated AppUserPublishPraiseBo bo) {
        return iAppUserPublishPraiseService.queryPageList(bo);
    }

    /**
     * 导出用户动态点赞记录列表
     */
    @PreAuthorize("@ss.hasPermi('life:userPublishPraise:export')")
    @Log(title = "用户动态点赞记录", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppUserPublishPraiseVo> export(@Validated AppUserPublishPraiseBo bo) {
        List<AppUserPublishPraiseVo> list = iAppUserPublishPraiseService.queryList(bo);
        ExcelUtil<AppUserPublishPraiseVo> util = new ExcelUtil<AppUserPublishPraiseVo>(AppUserPublishPraiseVo.class);
        return util.exportExcel(list, "用户动态点赞记录");
    }

    /**
     * 获取用户动态点赞记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:userPublishPraise:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppUserPublishPraiseVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppUserPublishPraiseService.queryById(id));
    }

    /**
     * 新增用户动态点赞记录
     */
    @PreAuthorize("@ss.hasPermi('life:userPublishPraise:add')")
    @Log(title = "用户动态点赞记录", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppUserPublishPraiseBo bo) {
        return toAjax(iAppUserPublishPraiseService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改用户动态点赞记录
     */
    @PreAuthorize("@ss.hasPermi('life:userPublishPraise:edit')")
    @Log(title = "用户动态点赞记录", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppUserPublishPraiseBo bo) {
        return toAjax(iAppUserPublishPraiseService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除用户动态点赞记录
     */
    @PreAuthorize("@ss.hasPermi('life:userPublishPraise:remove')")
    @Log(title = "用户动态点赞记录" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppUserPublishPraiseService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
