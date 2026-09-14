package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
public class QlPaymentBo {
    @NotBlank(message = "付款状态不能为空")
    private String paymentStatus;
    @DecimalMin(value = "0", message = "实付金额不能小于0")
    private BigDecimal amount;
    private String paymentMethod;
    private String changeReason;
}
