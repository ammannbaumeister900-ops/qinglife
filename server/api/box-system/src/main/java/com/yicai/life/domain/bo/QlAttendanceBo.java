package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class QlAttendanceBo {
    @NotBlank(message = "签到状态不能为空")
    private String attendanceStatus;
    private String changeReason;
}
