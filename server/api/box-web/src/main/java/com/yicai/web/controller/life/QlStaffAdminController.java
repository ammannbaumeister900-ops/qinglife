package com.yicai.web.controller.life;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.common.annotation.Log;
import com.yicai.common.enums.BusinessType;
import com.yicai.life.service.QlStaffWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/life/staff")
public class QlStaffAdminController {
 private final QlStaffWorkspaceService staff;
 @PreAuthorize("@ss.hasPermi('life:staff:manage')")
 @GetMapping("/list") public AjaxResult<?> list(){return AjaxResult.success(staff.grants());}
 @PreAuthorize("@ss.hasPermi('life:staff:manage')")
 @GetMapping("/app-users") public AjaxResult<?> appUsers(@RequestParam(defaultValue="") String q){return AjaxResult.success(staff.appUsers(q));}
 @PreAuthorize("@ss.hasPermi('life:staff:manage')")
 @GetMapping("/operators") public AjaxResult<?> operators(){return AjaxResult.success(staff.operators());}
 @Log(title="工作人员授权",businessType=BusinessType.UPDATE)
 @PreAuthorize("@ss.hasPermi('life:staff:manage')")
 @PutMapping public AjaxResult<?> grant(@RequestBody Map<String,Object> body){staff.grant(body,SecurityUtils.getLoginUser().getUser().getUserId());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('life:serviceRecord:list')")
 @GetMapping("/interview-page") public AjaxResult<?> interviewPage(@RequestParam(defaultValue="") String realName,@RequestParam(defaultValue="") String nickname,@RequestParam(defaultValue="") String operatorName,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize){return AjaxResult.success(staff.interviewPage(realName,nickname,operatorName,pageNum,pageSize));}
 @PreAuthorize("@ss.hasPermi('life:serviceRecord:list')")
 @GetMapping("/interview-images") public AjaxResult<?> interviewImages(@RequestParam String customerId){return AjaxResult.success(staff.interviewImages(customerId));}
 @PreAuthorize("@ss.hasPermi('life:serviceRecord:list')")
 @GetMapping("/interviews") public AjaxResult<?> interviews(@RequestParam(required=false) String customerId){return AjaxResult.success(staff.interviews(customerId));}
 @PreAuthorize("@ss.hasPermi('life:serviceRecord:list')")
 @GetMapping("/images/{id}") public org.springframework.http.ResponseEntity<byte[]> image(@PathVariable String id){Map<String,Object> im=staff.image(id);return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.parseMediaType(im.get("mime_type").toString())).body((byte[])im.get("image_data"));}

}
