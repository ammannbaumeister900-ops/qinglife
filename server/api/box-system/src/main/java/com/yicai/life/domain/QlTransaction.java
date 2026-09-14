package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("ql_transaction")
public class QlTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String customerId;
    private String sessionId;
    private String registrationBatchId;
    private String registrationId;
    private String transactionType;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private Date transactionAt;
    private String recordSource;
    private String externalTransactionNo;
    private Long operatorId;
    private String remark;
    private Integer revision;
    private Date createdAt;
    private Date updatedAt;
}
