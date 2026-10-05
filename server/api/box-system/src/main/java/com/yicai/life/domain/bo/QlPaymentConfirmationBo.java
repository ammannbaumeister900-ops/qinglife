package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class QlPaymentConfirmationBo {
    @NotBlank(message = "请选择付款方式")
    private String paymentMethod;
    @DecimalMin(value = "0", message = "付款金额不能小于0")
    @Digits(integer = 8, fraction = 2, message = "付款金额最多8位整数和2位小数")
    private BigDecimal amount;
    @Min(value = 1, message = "使用卡次至少1次")
    private Integer passUnits;
    private String passAccountId;
    private String expectedSettlementId;
    @Size(max = 500, message = "备注最多500字")
    private String note;
}
