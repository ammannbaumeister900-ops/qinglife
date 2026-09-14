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
import com.yicai.life.domain.vo.CollectVo;
import com.yicai.life.domain.bo.CollectBo;
import com.yicai.life.service.ICollectService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 用户收藏文章Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/collect")
public class CollectController extends BaseController {

    private final ICollectService iCollectService;

    /**
     * 查询用户收藏文章列表
     */
    @PreAuthorize("@ss.hasPermi('life:collect:list')")
    @GetMapping("/list")
    public TableDataInfo<CollectVo> list(@Validated CollectBo bo) {
        return iCollectService.selectPageList(bo);
    }

    /**
     * 导出用户收藏文章列表
     */
    @PreAuthorize("@ss.hasPermi('life:collect:export')")
    @Log(title = "用户收藏文章", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<CollectVo> export(@Validated CollectBo bo) {
        List<CollectVo> list = iCollectService.queryList(bo);
        ExcelUtil<CollectVo> util = new ExcelUtil<CollectVo>(CollectVo.class);
        return util.exportExcel(list, "用户收藏文章");
    }

    /**
     * 获取用户收藏文章详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:collect:query')")
    @GetMapping("/{id}")
    public AjaxResult<CollectVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iCollectService.queryById(id));
    }

    /**
     * 新增用户收藏文章
     */
    @PreAuthorize("@ss.hasPermi('life:collect:add')")
    @Log(title = "用户收藏文章", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody CollectBo bo) {
        return toAjax(iCollectService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改用户收藏文章
     */
    @PreAuthorize("@ss.hasPermi('life:collect:edit')")
    @Log(title = "用户收藏文章", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody CollectBo bo) {
        return toAjax(iCollectService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除用户收藏文章
     */
    @PreAuthorize("@ss.hasPermi('life:collect:remove')")
    @Log(title = "用户收藏文章" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iCollectService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
