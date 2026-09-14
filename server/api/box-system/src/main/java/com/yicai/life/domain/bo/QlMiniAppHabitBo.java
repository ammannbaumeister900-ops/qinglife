package com.yicai.life.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class QlMiniAppHabitBo {
    private String sessionId;
    @NotNull(message = "计划天数不能为空")
    private Integer planLength;
    @NotNull(message = "开始日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startedAt;
}
