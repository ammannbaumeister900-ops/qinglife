package com.yicai.life.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class QlSessionVo {
    private String id;
    private Integer sessionNumber;
    private String name;
    private String theme;
    private String coverUrl;
    private String intro;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date registrationOpenAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date registrationCloseAt;
    private String leaderName;
    private String cancelPolicy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
