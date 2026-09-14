package com.yicai.life.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class QlSessionBo extends BaseEntity {
    @NotBlank(message = "期次ID不能为空", groups = EditGroup.class)
    private String id;
    @NotNull(message = "期次编号不能为空", groups = {AddGroup.class, EditGroup.class})
    @Min(value = 1, message = "期次编号必须大于0", groups = {AddGroup.class, EditGroup.class})
    private Integer sessionNumber;
    @NotBlank(message = "期次名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;
    private String intro;
    @NotNull(message = "开始日期不能为空", groups = {AddGroup.class, EditGroup.class})
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startDate;
    @NotNull(message = "结束日期不能为空", groups = {AddGroup.class, EditGroup.class})
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endDate;
    @Min(value = 1, message = "名额必须大于0", groups = {AddGroup.class, EditGroup.class})
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
}
