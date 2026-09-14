package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class QlMiniAppAttendanceBo {
    @NotBlank(message = "报名记录不能为空")
    private String registrationId;
    @NotBlank(message = "活动日不能为空")
    private String sessionDayId;
}
