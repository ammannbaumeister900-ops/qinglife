package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

@Data
public class QlReceiptAllocationBo {
    @NotBlank private String operationId;
    @NotNull @Min(0) private Integer expectedRevision;
    @NotBlank private String receiptId;
    @NotNull @Min(0) private Integer expectedReceiptRevision;
    @NotEmpty @Size(max=50) @Valid private List<Line> lines;
    @NotBlank @Size(max=500) private String reason;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key,Object value) { throw new IllegalArgumentException("未知分配字段："+key); }
    @Data public static class Line {
        @NotBlank private String registrationId;
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) private BigDecimal amount;
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String key,Object value) { throw new IllegalArgumentException("未知分配字段："+key); }
    }
}
