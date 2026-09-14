package com.yicai.life.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class QlCustomerBo extends BaseEntity {
    @NotBlank(message = "轻友ID不能为空", groups = EditGroup.class)
    private String id;
    private String customerNo;
    @NotBlank(message = "称呼不能为空", groups = {AddGroup.class, EditGroup.class})
    private String nickname;
    private String realName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date birthDate;
    private String gender;
    private String city;
    private String firstSource;
    private String dataSource;
    private String dataConfidence;
    /** Exact current mobile number lookup; it is not persisted on the customer row. */
    private String phone;
    private String status;
}
