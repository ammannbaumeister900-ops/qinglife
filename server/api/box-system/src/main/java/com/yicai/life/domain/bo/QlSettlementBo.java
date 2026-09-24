package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class QlSettlementBo {
    @NotNull(message = "最终金额不能为空，可填写0")
    @DecimalMin(value = "0", message = "最终金额不能小于0")
    private BigDecimal finalAmount;
    @NotBlank(message = "结算方式不能为空")
    private String settlementType;
    @Min(value = 0, message = "卡次不能小于0")
    private Integer passUnits;
    private String passAccountId;
    @Size(max = 500, message = "结算说明最多500字")
    private String note;
}
