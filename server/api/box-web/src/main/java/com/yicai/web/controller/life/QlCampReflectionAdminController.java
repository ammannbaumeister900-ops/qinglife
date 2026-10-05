package com.yicai.web.controller.life;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.bo.QlReflectionModerationBo;
import com.yicai.life.service.QlCampReflectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/life/camp-reflection")
public class QlCampReflectionAdminController {
 private final QlCampReflectionService reflections;
 @PreAuthorize("@ss.hasPermi('life:reflection:list')") @GetMapping("/list")
 public AjaxResult<Map<String,Object>> list(@RequestParam(required=false) String sessionId,@RequestParam(required=false) String phase,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize){return AjaxResult.success(reflections.list(sessionId,phase,status,pageNum,pageSize));}
 @PreAuthorize("@ss.hasPermi('life:reflection:review')") @PutMapping("/{id}/review/{action}")
 public AjaxResult<Void> review(@PathVariable String id,@PathVariable String action,@Valid @RequestBody QlReflectionModerationBo bo){if(!java.util.Arrays.asList("approve","reject","edit").contains(action))throw new com.yicai.common.exception.CustomException("审核操作无效",400);reflections.moderate(id,action,bo,SecurityUtils.getLoginUser().getUser().getUserId());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('life:reflection:review') and @ss.hasPermi('life:reflection:publish')") @PutMapping("/{id}/approve-and-publish")
 public AjaxResult<Void> approveAndPublish(@PathVariable String id,@Valid @RequestBody QlReflectionModerationBo bo){reflections.moderate(id,"approvePublish",bo,SecurityUtils.getLoginUser().getUser().getUserId());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('life:reflection:publish')") @PutMapping("/{id}/display/{action}")
 public AjaxResult<Void> display(@PathVariable String id,@PathVariable String action,@Valid @RequestBody QlReflectionModerationBo bo){if(!java.util.Arrays.asList("publish","hide","order").contains(action))throw new com.yicai.common.exception.CustomException("展示操作无效",400);reflections.moderate(id,action,bo,SecurityUtils.getLoginUser().getUser().getUserId());return AjaxResult.success();}
}
