package com.yicai.web.controller.life;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.QlReflectionModerationBo;
import com.yicai.life.domain.bo.QlReflectionPublishBatchBo;
import com.yicai.life.service.QlCampReflectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/life/camp-reflection")
public class QlCampReflectionAdminController {
 private final QlCampReflectionService reflections;
 private final com.yicai.framework.web.service.PermissionService permissions;
 @PreAuthorize("@ss.hasPermi('life:reflection:list')") @GetMapping("/list")
 public AjaxResult<Map<String,Object>> list(@RequestParam(required=false) String sessionId,@RequestParam(required=false) String phase,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize){return AjaxResult.success(reflections.list(sessionId,phase,status,pageNum,pageSize));}
 @PreAuthorize("@ss.hasPermi('life:reflection:review')") @PutMapping("/{id}/review/{action}")
 public AjaxResult<Void> review(@PathVariable String id,@PathVariable String action,@Valid @RequestBody QlReflectionModerationBo bo){if(!java.util.Arrays.asList("approve","reject","edit").contains(action))throw new com.yicai.common.exception.CustomException("审核操作无效",400);reflections.moderate(id,action,bo,SecurityUtils.getLoginUser().getUser().getUserId());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('life:reflection:review') and @ss.hasPermi('life:reflection:publish')") @PutMapping("/{id}/approve-and-publish")
 public AjaxResult<Void> approveAndPublish(@PathVariable String id,@Valid @RequestBody QlReflectionModerationBo bo){reflections.moderate(id,"approvePublish",bo,SecurityUtils.getLoginUser().getUser().getUserId(),readableTypes());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('life:reflection:review') and @ss.hasPermi('life:reflection:publish')") @PutMapping("/publish-batch")
 public AjaxResult<Integer> publishBatch(@Valid @RequestBody QlReflectionPublishBatchBo bo){return AjaxResult.success(reflections.publishBatch(bo,SecurityUtils.getLoginUser().getUser().getUserId(),readableTypes()));}
 @PreAuthorize("@ss.hasPermi('life:reflection:publish')") @PutMapping("/{id}/display/{action}")
 public AjaxResult<Void> display(@PathVariable String id,@PathVariable String action,@Valid @RequestBody QlReflectionModerationBo bo){if(!java.util.Arrays.asList("publish","hide","order").contains(action))throw new com.yicai.common.exception.CustomException("展示操作无效",400);if("publish".equals(action))reflections.moderate(id,action,bo,SecurityUtils.getLoginUser().getUser().getUserId(),readableTypes());else reflections.moderate(id,action,bo,SecurityUtils.getLoginUser().getUser().getUserId());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('life:reflection:publish')") @PutMapping("/{id}/review-check")
 public AjaxResult<Void> defer(@PathVariable String id,@Valid @RequestBody QlReflectionModerationBo bo){reflections.moderate(id,"defer",bo,SecurityUtils.getLoginUser().getUser().getUserId(),readableTypes());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('life:reflection:publish') and @ss.hasPermi('life:customer:query')") @GetMapping("/{id}/review-history")
 public AjaxResult<Map<String,Object>> history(@PathVariable String id){return AjaxResult.success(reflections.reviewHistory(id,readableTypes()));}
 private java.util.Set<String> readableTypes(){java.util.Set<String> types=new java.util.HashSet<>();if(permissions.hasPermi("life:registration:query"))types.add("registration");if(permissions.hasPermi("life:customer:query"))java.util.Collections.addAll(types,"assessment","interaction","consent");return types;}
}
