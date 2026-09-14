package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ql_customer")
public class QlCustomer implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String customerNo;
    private String nickname;
    private String realName;
    private Date birthDate;
    private String gender;
    private String city;
    private String firstSource;
    private String dataSource;
    private String dataConfidence;
    private String status;
    private Integer revision;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;
}
