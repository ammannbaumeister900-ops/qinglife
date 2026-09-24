package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class QlMiniAppRegistrationBo {
    @NotBlank(message = "期次不能为空")
    private String sessionId;
    @NotBlank(message = "请求编号不能为空")
    private String clientRequestId;
    private String invitationCode;
    private String motivation;
    private Boolean serviceConsent;
    @NotBlank(message = "主要联系人不能为空")
    @Size(max = 50, message = "主要联系人最多50字")
    private String contactName;
    @NotBlank(message = "主要联系人手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "主要联系人手机号格式不正确")
    private String contactPhone;
    @Valid
    @NotEmpty(message = "请至少选择一位参与人")
    private List<Participant> participants;

    @Data
    public static class Participant {
        private String customerId;
        private Boolean self;
        @NotBlank(message = "参与人姓名不能为空")
        private String name;
        private String relation;
        private Boolean minor;
        @Pattern(regexp = "^$|^1\\d{10}$", message = "参与人手机号格式不正确")
        private String phone;
    }
}
