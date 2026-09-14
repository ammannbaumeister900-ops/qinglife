package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
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
        private String phone;
    }
}
