package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ql_registration")
public class QlRegistration implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private java.math.BigDecimal unitPrice;
    private String batchId;
    private String customerId;
    private String sessionId;
    private String registrationStatus;
    private String paymentStatus;
    private String registrationSource;
    private Date registeredAt;
    private Date confirmedAt;
    private Long confirmedBy;
    private String sessionReferrerCustomerId;
    private String returnPrimaryTrigger;
    private String returnTriggerNote;
    private String attributionSource;
    private String remark;
    private Integer revision;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;
}
