package com.yicai.web.controller.life;

import com.yicai.common.annotation.RepeatSubmit;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.life.domain.bo.*;
import com.yicai.life.service.IQlMiniAppService;
import com.yicai.life.service.QlFriendAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RequestMapping("/app/qinglife")
public class QlMiniAppController {
    private final IQlMiniAppService miniAppService;
    private final QlFriendAssessmentService friendAssessments;

    @GetMapping("/sessions")
    public AjaxResult<List<Map<String, Object>>> sessions() {
        return AjaxResult.success(miniAppService.listSessions());
    }

    @GetMapping("/readings/featured")
    public AjaxResult<List<Map<String, Object>>> featuredReadings() {
        return AjaxResult.success(miniAppService.listFeaturedReadings());
    }

    @GetMapping("/readings")
    public AjaxResult<Map<String,Object>> readings(@Valid QlReadingQueryBo query) { return AjaxResult.success(miniAppService.listReadings(query)); }
    @GetMapping("/readings/topics")
    public AjaxResult<List<Map<String,Object>>> readingTopics() { return AjaxResult.success(miniAppService.readingTopics()); }
    @GetMapping("/readings/home")
    public AjaxResult<Map<String,Object>> readingHome() { return AjaxResult.success(miniAppService.homeReadings()); }
    @GetMapping("/readings/{id}")
    public AjaxResult<Map<String, Object>> reading(@PathVariable Long id) {
        return AjaxResult.success(miniAppService.readingDetail(id));
    }

    @GetMapping("/contact")
    public AjaxResult<Map<String, Object>> contact() {
        return AjaxResult.success(miniAppService.contact());
    }

    @GetMapping("/sessions/{id}")
    public AjaxResult<Map<String,Object>> sessionDetail(@PathVariable String id) { return AjaxResult.success(miniAppService.sessionDetail(id)); }

    @PostMapping("/sessions/{id}/invitations")
    public AjaxResult<Map<String,Object>> invite(@RequestHeader(value="token", required=false) String token, @PathVariable String id) { return AjaxResult.success(miniAppService.createInvitation(token, id, false)); }

    @GetMapping("/invitations/{code}")
    public AjaxResult<Map<String,Object>> resolveInvite(@PathVariable String code) { return AjaxResult.success(miniAppService.resolveInvitation(code)); }

    @PutMapping("/experience-records")
    public AjaxResult<Void> experience(@RequestHeader(value="token", required=false) String token, @Valid @RequestBody QlMiniAppExperienceBo bo) { miniAppService.saveExperience(token, bo); return AjaxResult.success(); }

    @GetMapping("/me/overview")
    public AjaxResult<Map<String, Object>> overview(@RequestHeader(value = "token", required = false) String token) {
        return AjaxResult.success(miniAppService.overview(token));
    }

    @GetMapping("/me/referrals")
    public AjaxResult<List<Map<String,Object>>> referrals(@RequestHeader(value="token",required=false) String token) {
        return AjaxResult.success(miniAppService.myReferrals(token));
    }

    @GetMapping("/friend/assessment-invitations/{code}")
    public AjaxResult<Map<String,Object>> assessmentInvitation(@RequestHeader(value="token",required=false) String token,@PathVariable String code) {
        return AjaxResult.success(friendAssessments.assessmentInvitation(token,code));
    }

    @GetMapping("/friend/profile")
    public AjaxResult<Map<String,Object>> friendProfile(@RequestHeader(value="token",required=false) String token) {
        return AjaxResult.success(friendAssessments.myProfile(token));
    }

    @GetMapping("/friend/assessments")
    public AjaxResult<List<Map<String,Object>>> friendAssessments(@RequestHeader(value="token",required=false) String token) {
        return AjaxResult.success(friendAssessments.mine(token));
    }

    @RepeatSubmit
    @PostMapping("/friend/assessments")
    public AjaxResult<Map<String,Object>> submitFriendAssessment(@RequestHeader(value="token",required=false) String token,
                                                                 @Valid @RequestBody QlFriendAssessmentBo bo) {
        return AjaxResult.success(friendAssessments.submit(token,bo));
    }

    @PutMapping("/friend/assessments/{id}")
    public AjaxResult<Map<String,Object>> amendFriendAssessment(@RequestHeader(value="token",required=false) String token,
            @PathVariable String id,@Valid @RequestBody QlFriendAssessmentBo bo) {
        return AjaxResult.success(friendAssessments.amend(token,id,bo));
    }

    @PostMapping("/registrations")
    public AjaxResult<Map<String, Object>> register(@RequestHeader(value = "token", required = false) String token,
                                                    @Valid @RequestBody QlMiniAppRegistrationBo bo) {
        return AjaxResult.success(miniAppService.register(token, bo));
    }

    @RepeatSubmit
    @PostMapping("/attendance/check-in")
    public AjaxResult<Void> checkIn(@RequestHeader(value = "token", required = false) String token,
                                    @Valid @RequestBody QlMiniAppAttendanceBo bo) {
        miniAppService.checkIn(token, bo);
        return AjaxResult.success();
    }

    @PutMapping("/daily-records/today")
    public AjaxResult<Void> saveDailyRecord(@RequestHeader(value = "token", required = false) String token,
                                            @Valid @RequestBody QlMiniAppDailyRecordBo bo) {
        miniAppService.saveDailyRecord(token, bo);
        return AjaxResult.success();
    }

    @GetMapping("/daily-records")
    public AjaxResult<List<Map<String, Object>>> dailyRecords(@RequestHeader(value = "token", required = false) String token) {
        return AjaxResult.success(miniAppService.listDailyRecords(token));
    }

    @RepeatSubmit
    @PostMapping("/habits")
    public AjaxResult<Map<String, Object>> startHabit(@RequestHeader(value = "token", required = false) String token,
                                                      @Valid @RequestBody QlMiniAppHabitBo bo) {
        return AjaxResult.success(miniAppService.startHabit(token, bo));
    }

    @PutMapping("/habits/{id}/pause")
    public AjaxResult<Map<String,Object>> pauseHabit(@RequestHeader(value="token",required=false) String token, @PathVariable String id) {
        return AjaxResult.success(miniAppService.changeHabit(token,id,true));
    }

    @PutMapping("/habits/{id}/resume")
    public AjaxResult<Map<String,Object>> resumeHabit(@RequestHeader(value="token",required=false) String token, @PathVariable String id) {
        return AjaxResult.success(miniAppService.changeHabit(token,id,false));
    }
    @RepeatSubmit
    @PostMapping("/posts/{publishId}/reports")
    public AjaxResult<Void> reportPost(@RequestHeader(value = "token", required = false) String token,
                                       @PathVariable Long publishId, @RequestBody QlMiniAppReportBo bo) {
        miniAppService.reportPost(token, publishId, bo);
        return AjaxResult.success();
    }

    @PutMapping("/subscriptions/{templateKey}")
    public AjaxResult<Void> saveSubscription(@RequestHeader(value = "token", required = false) String token,
                                             @PathVariable String templateKey,
                                             @Valid @RequestBody QlMiniAppSubscriptionBo bo) {
        miniAppService.saveSubscription(token, templateKey, bo);
        return AjaxResult.success();
    }
}
