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
import com.yicai.life.domain.vo.AppUserPublishVo;
import com.yicai.life.domain.bo.AppUserPublishBo;
import com.yicai.life.service.IAppUserPublishService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 用户动态Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/userPublish")
public class AppUserPublishController extends BaseController {

    private final IAppUserPublishService iAppUserPublishService;

    /**
     * 查询用户动态列表
     */
    @PreAuthorize("@ss.hasPermi('life:userPublish:list')")
    @GetMapping("/list")
    public TableDataInfo<AppUserPublishVo> list(@Validated AppUserPublishBo bo) {
        return iAppUserPublishService.selectPageList(bo);
    }

    /**
     * 导出用户动态列表
     */
    @PreAuthorize("@ss.hasPermi('life:userPublish:export')")
    @Log(title = "用户动态", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppUserPublishVo> export(@Validated AppUserPublishBo bo) {
        List<AppUserPublishVo> list = iAppUserPublishService.queryList(bo);
        ExcelUtil<AppUserPublishVo> util = new ExcelUtil<AppUserPublishVo>(AppUserPublishVo.class);
        return util.exportExcel(list, "用户动态");
    }

    /**
     * 获取用户动态详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:userPublish:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppUserPublishVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppUserPublishService.queryById(id));
    }

    /**
     * 新增用户动态
     */
    @PreAuthorize("@ss.hasPermi('life:userPublish:add')")
    @Log(title = "用户动态", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppUserPublishBo bo) {
        return toAjax(iAppUserPublishService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改用户动态
     */
    @PreAuthorize("@ss.hasPermi('life:userPublish:edit')")
    @Log(title = "用户动态", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppUserPublishBo bo) {
        return toAjax(iAppUserPublishService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除用户动态
     */
    @PreAuthorize("@ss.hasPermi('life:userPublish:remove')")
    @Log(title = "用户动态" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppUserPublishService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
