package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlFriendAssessmentBo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public final class QlFriendAssessmentValidator {
    public static final String FORM_VERSION = "2026_v1";
    public static final String CONSENT_POLICY_VERSION = "friend_assessment_2026_v1";
    public static final Set<String> GOALS = set("排毒","减重","减压","调理");
    public static final Set<String> DIETS = set("全素","以素为主","以荤为主","荤素各半");
    public static final Set<String> BOWELS = set("2-3次/天","1次/天","便秘");
    public static final Set<String> ENERGIES = set("很好","一般","差");
    public static final Set<String> EXERCISES = set("不运动","偶尔运动","经常运动");
    public static final Set<String> EMOTIONS = set("平静","开心","轻松","焦虑","压力感","悲伤","没什么感觉");
    public static final Set<String> HEALTH = set("高血压","低血压","心脏病","血糖偏高","低血糖","糖尿病","脂肪肝","高血脂","胆结石","前列腺","甲状腺","甲亢","甲减","咽喉炎","胃炎","胆固醇高","肾炎","妇科问题","乳腺问题","睡眠","易疲劳","皮肤过敏","易感冒","头晕头痛","腰椎盘突出","其他","无");

    private QlFriendAssessmentValidator() { }

    public static void validate(QlFriendAssessmentBo b) {
        require(length(b.getName(),2,30),"姓名请输入2至30个字");
        require(b.getNickname()==null || b.getNickname().trim().length()<=50,"轻生活小名不能超过50个字");
        require(b.getBirthDate()!=null && !new java.sql.Date(b.getBirthDate().getTime()).toLocalDate().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))),"出生日期不能晚于今天");
        String phone=trim(b.getPhone());
        require(phone.matches("[+0-9()\\- ]{7,24}") && phone.replaceAll("\\D","").length()>=7,"联系电话格式不正确");
        require(range(b.getHeightCm(),"80","250"),"身高应在80至250厘米之间");
        require(range(b.getWeightKg(),"20","300") && b.getWeightKg().stripTrailingZeros().scale()<=1,"体重应在20至300公斤之间，最多一位小数");
        require(length(b.getCity(),1,100),"请输入所在城市");
        choices(b.getCleanBodyGoals(),GOALS,"清体目的",true);
        choice(b.getDietPreference(),DIETS,"饮食偏好");
        require(b.getWaterIntakeMl()!=null && b.getWaterIntakeMl()>=0 && b.getWaterIntakeMl()<=10000,"饮水量应在0至10000毫升之间");
        time(b.getWakeTime(),"起床时间"); time(b.getSleepTime(),"睡觉时间");
        choice(b.getBowelStatus(),BOWELS,"排便情况"); choice(b.getEnergyStatus(),ENERGIES,"精力状态"); choice(b.getExerciseStatus(),EXERCISES,"运动情况");
        choices(b.getEmotionalStatus(),EMOTIONS,"近期情绪",true);
        choices(b.getHealthConditions(),HEALTH,"身体情况",true);
        require(!(b.getHealthConditions().contains("无") && b.getHealthConditions().size()>1),"身体情况“无”不能与其他项同时选择");
        require(!b.getHealthConditions().contains("其他") || length(b.getOtherHealthCondition(),1,500),"请补充说明其他身体情况");
        require(b.getMedications()==null || b.getMedications().length()<=2000,"药物或保健品说明不能超过2000个字");
        require(b.getPregnancyStatus()==null || b.getPregnancyStatus().isEmpty() || Arrays.asList("否","是").contains(b.getPregnancyStatus()),"孕期状态选项无效");
        require(length(b.getReferralSource(),1,200),"请填写了解轻生活的途径");
        require(b.getRetrainingReason()==null || b.getRetrainingReason().length()<=2000,"复训原因不能超过2000个字");
        require(Boolean.TRUE.equals(b.getSensitiveConsent()),"请先阅读并同意敏感个人信息授权");
        require(b.getClientRequestId()!=null && b.getClientRequestId().matches("[A-Za-z0-9_-]{8,64}"),"提交标识无效，请重新进入页面");
    }

    private static void time(String value,String name){try{LocalTime.parse(value);}catch(Exception e){throw new CustomException(name+"格式不正确");}}
    private static void choice(String value,Set<String> values,String name){require(values.contains(value),name+"选项无效");}
    private static void choices(List<String> values,Set<String> allowed,String name,boolean required){
        require(values!=null && (!required || !values.isEmpty()),"请至少选择一项"+name);
        require(values.size()==new HashSet<>(values).size() && allowed.containsAll(values),name+"选项无效");
    }
    private static boolean length(String value,int min,int max){int n=trim(value).length();return n>=min&&n<=max;}
    private static boolean range(BigDecimal value,String min,String max){return value!=null&&value.compareTo(new BigDecimal(min))>=0&&value.compareTo(new BigDecimal(max))<=0;}
    private static String trim(String value){return value==null?"":value.trim();}
    private static void require(boolean ok,String message){if(!ok)throw new CustomException(message,400);}
    private static Set<String> set(String... values){return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));}
}
