package com.yicai.life.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class QlRegistrationVo {
    private String id;
    private String batchId;
    private String orderNo;
    private String buyerCustomerId;
    private String buyerNickname;
    private Integer participantCount;
    private String contactName;
    private String contactPhone;
    private BigDecimal quotedAmount;
    private BigDecimal payableAmount;
    private BigDecimal finalAmount;
    private String settlementStatus;
    private String settlementType;
    private Integer passUnits;
    private String passAccountId;
    private String settlementNote;
    private String batchPaymentStatus;
    private String customerId;
    private String customerNo;
    private String nickname;
    private String realName;
    private String sessionId;
    private Integer sessionNumber;
    private String sessionName;
    private BigDecimal standardPrice;
    private String registrationStatus;
    private String paymentStatus;
    private String registrationSource;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date registeredAt;
    private String sessionReferrerCustomerId;
    private String referrerNickname;
    private String returnPrimaryTrigger;
    private String returnTriggerNote;
    private String attributionSource;
    private String remark;
    private Integer revision;
}
