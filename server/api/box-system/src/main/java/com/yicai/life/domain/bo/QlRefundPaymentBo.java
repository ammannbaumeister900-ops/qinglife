package com.yicai.life.domain.bo;

import lombok.Data;
import java.math.BigDecimal;
import javax.validation.constraints.*;

@Data
public class QlRefundPaymentBo {
    @NotBlank private String operationId;
    @NotNull @Min(0) private Integer expectedRevision;
    @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) private BigDecimal actualAmount;
    @NotBlank @Pattern(regexp="cash|wechat_scan|alipay_scan|transfer|other") private String paymentMethod;
    @Size(min=1,max=100) private String externalReference;
    @NotBlank @Size(max=500) private String evidence;
    @NotBlank @Pattern(regexp="[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\+08:00") private String occurredAt;
    @NotBlank @Size(max=500) private String reason;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key,Object value){throw new IllegalArgumentException("未知实际退款字段："+key);}
}
