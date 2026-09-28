package com.yicai.web.controller.life;

import com.yicai.common.annotation.Log;
import com.yicai.common.annotation.RepeatSubmit;
import com.yicai.common.core.controller.BaseController;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import com.yicai.common.enums.BusinessType;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.QlCustomerBo;
import com.yicai.life.domain.vo.QlCustomerVo;
import com.yicai.life.service.IQlCustomerService;
import com.yicai.life.service.QlFriendAssessmentExportService;
import com.yicai.life.service.QlFriendAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RequestMapping("/life/customer")
public class QlCustomerController extends BaseController {
    private final IQlCustomerService customerService;
    private final com.yicai.life.service.QlCustomerDossierService dossierService;
    private final QlFriendAssessmentService friendAssessments;
    private final QlFriendAssessmentExportService assessmentExport;

    @PreAuthorize("@ss.hasPermi('life:assessment:statistics')")
    @GetMapping("/statistics")
    public AjaxResult<Map<String,Object>> statistics(@RequestParam(required=false) String startDate,
                                                     @RequestParam(required=false) String endDate) {
        return AjaxResult.success(friendAssessments.statistics(startDate,endDate));
    }

    @PreAuthorize("@ss.hasPermi('life:assessment:export') and @ss.hasPermi('life:assessment:sensitive')")
    @Log(title = "轻友登记导出", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult export(QlCustomerBo bo) { return assessmentExport.export(bo); }

    @PreAuthorize("@ss.hasPermi('life:customer:query')")
    @GetMapping("/{id}/dossier")
    public AjaxResult<Map<String,Object>> dossier(@PathVariable String id) {
        return AjaxResult.success(dossierService.overview(id));
    }

    @PreAuthorize("@ss.hasPermi('life:customer:list')")
    @GetMapping("/list")
    public TableDataInfo<QlCustomerVo> list(QlCustomerBo bo) {
        return customerService.queryPageList(bo);
    }

    @PreAuthorize("@ss.hasPermi('life:customer:query')")
    @GetMapping("/{id}")
    public AjaxResult<QlCustomerVo> getInfo(@PathVariable String id) {
        return AjaxResult.success(customerService.queryById(id));
    }

    @PreAuthorize("@ss.hasPermi('life:customer:query')")
    @GetMapping("/{id}/timeline")
    public AjaxResult<List<Map<String, Object>>> timeline(@PathVariable String id) {
        return AjaxResult.success(customerService.queryTimeline(id));
    }

    @PreAuthorize("@ss.hasPermi('life:customer:query')")
    @GetMapping("/{id}/assessments")
    public AjaxResult<List<Map<String,Object>>> assessments(@PathVariable String id) {
        boolean sensitive=com.yicai.common.utils.SecurityUtils.getLoginUser().getPermissions().contains("life:assessment:sensitive");
        return AjaxResult.success(friendAssessments.history(id,sensitive));
    }

    @PreAuthorize("@ss.hasPermi('life:customer:add')")
    @Log(title = "轻友档案", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public AjaxResult<Void> add(@Validated(AddGroup.class) @RequestBody QlCustomerBo bo) {
        return toAjax(customerService.insertByBo(bo, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('life:customer:edit')")
    @Log(title = "轻友档案", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public AjaxResult<Void> edit(@Validated(EditGroup.class) @RequestBody QlCustomerBo bo) {
        return toAjax(customerService.updateByBo(bo, currentUserId()));
    }

    private Long currentUserId() {
        return SecurityUtils.getLoginUser().getUser().getUserId();
    }
}
