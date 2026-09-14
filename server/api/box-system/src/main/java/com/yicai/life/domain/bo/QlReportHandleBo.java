package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class QlReportHandleBo {
    @NotBlank(message = "处理状态不能为空")
    private String status;
    @NotBlank(message = "处理结果不能为空")
    private String handleResult;
}
