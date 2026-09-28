package com.yicai.life.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class QlCustomerVo {
    private String id;
    private String customerNo;
    private String nickname;
    private String realName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date birthDate;
    private String gender;
    private String city;
    private BigDecimal heightCm;
    private String firstSource;
    private String dataSource;
    private String dataConfidence;
    private String status;
    private Integer revision;
    private String wechatNickname;
    private String wechatAvatar;
    private String accountStatus;
    private String phoneHint;
    private String idCardHint;
    private Integer age;
    private BigDecimal latestWeightKg;
    private Integer assessmentCount;
    private String firstAssessmentAt;
    private String latestAssessmentAt;
    private String latestCleanBodyGoals;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
