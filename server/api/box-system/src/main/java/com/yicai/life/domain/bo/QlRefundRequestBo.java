package com.yicai.life.domain.bo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.*;

@Data
public class QlRefundRequestBo {
    @NotBlank private String operationId;
    @NotNull @Min(0) private Integer expectedRevision;
    @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) private BigDecimal requestedAmount;
    @NotEmpty @Size(max=100) @Valid private List<Line> allocations;
    @NotBlank @Size(max=500) private String reason;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key,Object value){throw new IllegalArgumentException("未知退款字段："+key);}
    @Data public static class Line {
        @NotBlank private String allocationId;
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) private BigDecimal amount;
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String key,Object value){throw new IllegalArgumentException("未知退款分配字段："+key);}
    }
}
