package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ql_registration_status_log")
public class QlRegistrationStatusLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String registrationId;
    private String statusType;
    private String fromStatus;
    private String toStatus;
    private String changeReason;
    private Long operatorId;
    private Date changedAt;
}
