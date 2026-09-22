package com.yicai.life.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

@Data
public class QlMiniAppDailyRecordBo {
    private String sessionId;
    @NotNull(message = "记录日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date recordDate;
    @NotBlank(message = "记录阶段不能为空")
    private String recordStage;
    private Integer planDay;
    @NotBlank(message = "今日选择不能为空")
    private String choiceValue;
    @Size(max = 500, message = "记录内容不能超过500字")
    private String note;
}
