package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlFriendAssessmentBo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QlFriendAssessmentValidatorTest {
    @Test void acceptsMidnightSleepAndValidPayload() { assertDoesNotThrow(() -> QlFriendAssessmentValidator.validate(valid())); }

    @Test void rejectsNoneCombinedWithConditionAndUnknownEnums() {
        QlFriendAssessmentBo mixed=valid(); mixed.setHealthConditions(Arrays.asList("无","高血压"));
        assertThrows(CustomException.class,()->QlFriendAssessmentValidator.validate(mixed));
        QlFriendAssessmentBo invalid=valid(); invalid.setEnergyStatus("特别好");
        assertThrows(CustomException.class,()->QlFriendAssessmentValidator.validate(invalid));
    }

    @Test void requiresExplicitSensitiveConsent() {
        QlFriendAssessmentBo b=valid(); b.setSensitiveConsent(false);
        assertThrows(CustomException.class,()->QlFriendAssessmentValidator.validate(b));
    }

    private QlFriendAssessmentBo valid() {
        QlFriendAssessmentBo b=new QlFriendAssessmentBo();
        b.setClientRequestId(UUID.randomUUID().toString()); b.setName("测试轻友"); b.setNickname("");
        b.setBirthDate(new Date(946684800000L)); b.setPhone("+86 138-0000-0000");
        b.setHeightCm(new BigDecimal("165.0")); b.setWeightKg(new BigDecimal("55.5")); b.setCity("上海");
        b.setCleanBodyGoals(Arrays.asList("减重","调理")); b.setDietPreference("荤素各半"); b.setWaterIntakeMl(1500);
        b.setWakeTime("07:00"); b.setSleepTime("00:30"); b.setBowelStatus("1次/天"); b.setEnergyStatus("一般"); b.setExerciseStatus("偶尔运动");
        b.setEmotionalStatus(Collections.singletonList("平静")); b.setHealthConditions(Collections.singletonList("无"));
        b.setReferralSource("朋友小雨"); b.setSensitiveConsent(true); return b;
    }
}
