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
import com.yicai.life.domain.vo.AppUserInfoVo;
import com.yicai.life.domain.bo.AppUserInfoBo;
import com.yicai.life.service.IAppUserInfoService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 注册用户信息Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/userInfo")
public class AppUserInfoController extends BaseController {

    private final IAppUserInfoService iAppUserInfoService;

    /**
     * 查询注册用户信息列表
     */
    @PreAuthorize("@ss.hasPermi('life:userInfo:list')")
    @GetMapping("/list")
    public TableDataInfo<AppUserInfoVo> list(@Validated AppUserInfoBo bo) {
        return iAppUserInfoService.queryPageList(bo);
    }

    /**
     * 导出注册用户信息列表
     */
    @PreAuthorize("@ss.hasPermi('life:userInfo:export')")
    @Log(title = "注册用户信息", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<AppUserInfoVo> export(@Validated AppUserInfoBo bo) {
        List<AppUserInfoVo> list = iAppUserInfoService.queryList(bo);
        ExcelUtil<AppUserInfoVo> util = new ExcelUtil<AppUserInfoVo>(AppUserInfoVo.class);
        return util.exportExcel(list, "注册用户信息");
    }

    /**
     * 获取注册用户信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:userInfo:query')")
    @GetMapping("/{id}")
    public AjaxResult<AppUserInfoVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iAppUserInfoService.queryById(id));
    }

    /**
     * 新增注册用户信息
     */
    @PreAuthorize("@ss.hasPermi('life:userInfo:add')")
    @Log(title = "注册用户信息", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody AppUserInfoBo bo) {
        return toAjax(iAppUserInfoService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改注册用户信息
     */
    @PreAuthorize("@ss.hasPermi('life:userInfo:edit')")
    @Log(title = "注册用户信息", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody AppUserInfoBo bo) {
        return toAjax(iAppUserInfoService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除注册用户信息
     */
    @PreAuthorize("@ss.hasPermi('life:userInfo:remove')")
    @Log(title = "注册用户信息" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iAppUserInfoService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }

    /**
     * 修改注册用户状态
     */
    @PreAuthorize("@ss.hasPermi('life:userInfo:edit')")
    @Log(title = "注册用户信息", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/changeStatus")
    public AjaxResult<Void> changeStatus(@Validated(EditGroup.class) @RequestBody AppUserInfoBo bo) {
        return toAjax(iAppUserInfoService.updateByBo(bo) ? 1 : 0);
    }
}
