package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class QlSettlementReversalBo {
    @NotBlank(message = "撤销原因不能为空")
    @Size(max = 500, message = "撤销原因最多500字")
    private String reason;
}