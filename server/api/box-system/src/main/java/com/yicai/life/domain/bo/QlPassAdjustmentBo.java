package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class QlPassAdjustmentBo {
    @NotNull(message = "请填写调整次数")
    private Integer quantityDelta;
    @NotBlank(message = "调整卡次必须填写原因")
    @Size(max = 500, message = "原因最多500字")
    private String reason;
}
