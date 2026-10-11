package com.yicai.web.controller.life;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.life.domain.bo.*;
import com.yicai.life.service.QlCampReflectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/app/qinglife")
public class QlCampReflectionController {
 private final QlCampReflectionService reflections;
 private final Validator validator;
 @GetMapping("/camp-voices/{sessionId}") public AjaxResult<List<Map<String,Object>>> publicVoices(@PathVariable String sessionId){return AjaxResult.success(reflections.publicVoices(sessionId));}
 @GetMapping("/reflections/{sessionId}") public AjaxResult<Map<String,Object>> mine(@RequestHeader(value="token",required=false) String token,@PathVariable String sessionId){return AjaxResult.success(reflections.mine(token,sessionId));}
 @PutMapping("/reflections") public AjaxResult<Map<String,Object>> save(@RequestHeader(value="token",required=false) String token,@RequestBody QlCampReflectionBo bo){
  QlCampReflectionService.validateSubmissionContract(bo);
  Set<ConstraintViolation<QlCampReflectionBo>> errors=validator.validate(bo);
  if(!errors.isEmpty())throw new ConstraintViolationException(errors);
  return AjaxResult.success(reflections.save(token,bo));
 }
 @DeleteMapping("/reflections/{sessionId}/{phase}/share") public AjaxResult<Void> withdraw(@RequestHeader(value="token",required=false) String token,@PathVariable String sessionId,@PathVariable String phase){reflections.withdraw(token,sessionId,phase);return AjaxResult.success();}
 @PostMapping("/reflections/voice") public AjaxResult<Map<String,Object>> voice(@RequestHeader(value="token",required=false) String token,@Valid @RequestBody QlReflectionVoiceBo bo){return AjaxResult.success(reflections.transcribe(token,bo));}
}
