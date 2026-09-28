package com.yicai.life.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class QlFriendAssessmentBo {
    @NotBlank private String clientRequestId;
    @NotBlank private String name;
    private String nickname;
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") private Date birthDate;
    @NotBlank private String phone;
    @NotNull private BigDecimal heightCm;
    @NotNull private BigDecimal weightKg;
    @NotBlank private String city;
    @NotNull private List<String> cleanBodyGoals;
    @NotBlank private String dietPreference;
    @NotNull private Integer waterIntakeMl;
    @NotBlank private String wakeTime;
    @NotBlank private String sleepTime;
    @NotBlank private String bowelStatus;
    @NotBlank private String energyStatus;
    @NotBlank private String exerciseStatus;
    @NotNull private List<String> emotionalStatus;
    @NotNull private List<String> healthConditions;
    private String otherHealthCondition;
    private String medications;
    private String pregnancyStatus;
    @NotBlank private String referralSource;
    private String retrainingReason;
    @NotNull private Boolean sensitiveConsent;
}
