package com.yicai.web.controller.life;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.annotation.Log;
import com.yicai.common.enums.BusinessType;
import com.yicai.life.service.*;
import com.yicai.life.domain.bo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import javax.validation.Valid;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/app/qinglife/staff")
public class QlStaffWorkspaceController {
 private final QlStaffWorkspaceService staff;
 private final IQlRegistrationService registrations;
 @GetMapping("/me") public AjaxResult<?> me(@RequestHeader(value="token",required=false) String token){return AjaxResult.success(staff.access(token));}
 @GetMapping("/people") public AjaxResult<?> people(@RequestHeader(value="token",required=false) String token,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="30") int limit){staff.access(token);return AjaxResult.success(staff.people(q,limit));}
 @GetMapping("/people/{id}") public AjaxResult<?> person(@RequestHeader(value="token",required=false) String token,@PathVariable String id){staff.access(token);Map<String,Object> out=staff.customer(id);out.put("activities",staff.registrations(null,id));out.put("passes",staff.passes(id));out.put("interviews",staff.interviews(id));return AjaxResult.success(out);}
 @PostMapping("/people") public AjaxResult<?> addPerson(@RequestHeader(value="token",required=false) String token,@RequestBody Map<String,Object> body){Map<String,Object>a=staff.access(token);staff.permit(a,"can_operate");return AjaxResult.success(Collections.singletonMap("id",staff.addCustomer(body,staff.operator(a))));}
 @GetMapping("/sessions") public AjaxResult<?> sessions(@RequestHeader(value="token",required=false) String token,@RequestParam(defaultValue="false") boolean history){staff.access(token);return AjaxResult.success(staff.sessions(history));}
 @GetMapping("/sessions/{id}/registrations") public AjaxResult<?> roster(@RequestHeader(value="token",required=false) String token,@PathVariable String id){staff.access(token);return AjaxResult.success(staff.registrations(id,null));}
 @GetMapping("/registrations/{id}") public AjaxResult<?> registration(@RequestHeader(value="token",required=false) String token,@PathVariable String id){Map<String,Object>a=staff.access(token);return AjaxResult.success(registrations.queryById(id));}
 @PostMapping("/registrations") public AjaxResult<?> enroll(@RequestHeader(value="token",required=false) String token,@RequestBody QlRegistrationBo bo){Map<String,Object>a=staff.access(token);staff.permit(a,"can_operate");staff.enroll(bo.getCustomerId(),bo.getSessionId(),a);return AjaxResult.success();}
 @PutMapping("/registrations/{id}/confirm") public AjaxResult<?> confirm(@RequestHeader(value="token",required=false) String token,@PathVariable String id){Map<String,Object>a=staff.access(token);staff.permit(a,"can_operate");staff.confirm(id,a);return AjaxResult.success();}
 @PutMapping("/registrations/{id}/payment") public AjaxResult<?> payment(@RequestHeader(value="token",required=false) String token,@PathVariable String id,@Valid @RequestBody QlPaymentBo bo){Map<String,Object>a=staff.access(token);staff.permit(a,"can_payment");if(!"paid".equals(bo.getPaymentStatus()))throw new com.yicai.common.exception.CustomException("更正收款请在后台操作",400);staff.payment(id,bo,a);return AjaxResult.success();}
 @GetMapping("/interviews") public AjaxResult<?> interviews(@RequestHeader(value="token",required=false) String token,@RequestParam(required=false) String customerId){staff.access(token);return AjaxResult.success(staff.interviews(customerId));}
 @PostMapping("/interviews") public AjaxResult<?> interview(@RequestHeader(value="token",required=false) String token,@RequestBody Map<String,Object> body){return AjaxResult.success(Collections.singletonMap("id",staff.addInterview(body,staff.access(token))));}
 @GetMapping("/images/{id}") public ResponseEntity<byte[]> image(@RequestHeader(value="token",required=false) String token,@PathVariable String id){staff.access(token);Map<String,Object> image=staff.image(id);return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(image.get("mime_type").toString())).body((byte[])image.get("image_data"));}

 @GetMapping("/registrations/{id}/attendance") public AjaxResult<?> attendance(@RequestHeader(value="token",required=false) String token,@PathVariable String id){staff.access(token);return AjaxResult.success(staff.attendance(id));}
 @PutMapping("/registrations/{id}/arrival") public AjaxResult<?> arrive(@RequestHeader(value="token",required=false) String token,@PathVariable String id,@RequestBody Map<String,Object> body){staff.arrive(id,QlStaffWorkspaceService.text(body,"dayId",36,true),staff.access(token));return AjaxResult.success();}
 @PutMapping("/registrations/{id}/cancel") public AjaxResult<?> cancel(@RequestHeader(value="token",required=false) String token,@PathVariable String id){staff.cancel(id,staff.access(token));return AjaxResult.success();}
 @GetMapping("/people/{id}/contacts") public AjaxResult<?> contacts(@RequestHeader(value="token",required=false) String token,@PathVariable String id){staff.access(token);return AjaxResult.success(staff.contacts(id));}
 @PostMapping("/contacts") public AjaxResult<?> addContact(@RequestHeader(value="token",required=false) String token,@RequestBody Map<String,Object> body){return AjaxResult.success(staff.addContact(body,staff.access(token)));}
 @GetMapping("/people/{id}/follow-ups") public AjaxResult<?> followUps(@RequestHeader(value="token",required=false) String token,@PathVariable String id){staff.access(token);return AjaxResult.success(staff.followUps(id));}
 @PostMapping("/follow-ups") public AjaxResult<?> addFollowUp(@RequestHeader(value="token",required=false) String token,@RequestBody Map<String,Object> body){return AjaxResult.success(Collections.singletonMap("id",staff.addFollowUp(body,staff.access(token))));}
 @PutMapping("/follow-ups/{id}/status") public AjaxResult<?> transitionFollowUp(@RequestHeader(value="token",required=false) String token,@PathVariable String id,@RequestBody Map<String,Object> body){staff.transitionFollowUp(id,body,staff.access(token));return AjaxResult.success();}
}
