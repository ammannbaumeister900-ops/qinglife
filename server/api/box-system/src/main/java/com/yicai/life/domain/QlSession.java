package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("ql_session")
public class QlSession implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private Integer sessionNumber;
    private String name;
    private String intro;
    private Date startDate;
    private Date endDate;
    private Integer capacity;
    private BigDecimal standardPrice;
    private BigDecimal returningPrice;
    private String venue;
    private String publicVenue;
    private String province;
    private String city;
    private String status;
    private String registrationConfirmMode;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.IGNORED)
    private Date registrationOpenAt;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.IGNORED)
    private Date registrationCloseAt;
    private String leaderName;
    private String cancelPolicy;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;
}
