package com.yicai.life.domain.bo;

import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
public class QlRegistrationBo extends BaseEntity {
    @NotBlank(message = "报名ID不能为空", groups = EditGroup.class)
    private String id;
    @NotBlank(message = "轻友不能为空", groups = AddGroup.class)
    private String customerId;
    @NotBlank(message = "期次不能为空", groups = AddGroup.class)
    private String sessionId;
    private String registrationStatus;
    private String registrationSource;
    private String sessionReferrerCustomerId;
    private String returnPrimaryTrigger;
    private String returnTriggerNote;
    private String attributionSource;
    private String nickname;
    private Integer sessionNumber;
    /** Optional digits-only substring filter; exact sessionNumber takes precedence. */
    @javax.validation.constraints.Pattern(regexp="[0-9]{0,10}",message="期次查询只能输入最多10位数字")
    private String sessionNumberKeyword;
    private String paymentStatus;
    @javax.validation.constraints.Pattern(regexp="pending|completed|cancelled",message="付款进度无效")
    private String paymentProgress;
}
