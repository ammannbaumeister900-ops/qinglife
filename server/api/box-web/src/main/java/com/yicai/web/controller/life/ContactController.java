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
import com.yicai.life.domain.vo.ContactVo;
import com.yicai.life.domain.bo.ContactBo;
import com.yicai.life.service.IContactService;
import com.yicai.common.utils.poi.ExcelUtil;
import com.yicai.common.core.page.TableDataInfo;

/**
 * 联系信息Controller
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController
@RequestMapping("/life/contact")
public class ContactController extends BaseController {

    private final IContactService iContactService;

    /**
     * 查询联系信息列表
     */
    @PreAuthorize("@ss.hasPermi('life:contact:list')")
    @GetMapping("/list")
    public TableDataInfo<ContactVo> list(@Validated ContactBo bo) {
        return iContactService.queryPageList(bo);
    }

    /**
     * 导出联系信息列表
     */
    @PreAuthorize("@ss.hasPermi('life:contact:export')")
    @Log(title = "联系信息", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult<ContactVo> export(@Validated ContactBo bo) {
        List<ContactVo> list = iContactService.queryList(bo);
        ExcelUtil<ContactVo> util = new ExcelUtil<ContactVo>(ContactVo.class);
        return util.exportExcel(list, "联系信息");
    }

    /**
     * 获取联系信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('life:contact:query')")
    @GetMapping("/{id}")
    public AjaxResult<ContactVo> getInfo(@NotNull(message = "主键不能为空")
                                                  @PathVariable("id") Long id) {
        return AjaxResult.success(iContactService.queryById(id));
    }

    /**
     * 新增联系信息
     */
    @PreAuthorize("@ss.hasPermi('life:contact:add')")
    @Log(title = "联系信息", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping()
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody ContactBo bo) {
        return toAjax(iContactService.insertByBo(bo) ? 1 : 0);
    }

    /**
     * 修改联系信息
     */
    @PreAuthorize("@ss.hasPermi('life:contact:edit')")
    @Log(title = "联系信息", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping()
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody ContactBo bo) {
        return toAjax(iContactService.updateByBo(bo) ? 1 : 0);
    }

    /**
     * 删除联系信息
     */
    @PreAuthorize("@ss.hasPermi('life:contact:remove')")
    @Log(title = "联系信息" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ids) {
        return toAjax(iContactService.deleteWithValidByIds(Arrays.asList(ids), true) ? 1 : 0);
    }
}
