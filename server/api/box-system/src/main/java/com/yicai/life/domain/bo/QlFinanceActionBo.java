package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class QlFinanceActionBo {
    @NotBlank private String operationId;
    @NotNull @Min(0) private Integer expectedRevision;
    @NotBlank @Size(max=500) private String reason;
    private Boolean noActualPaymentConfirmed;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key,Object value){throw new IllegalArgumentException("未知操作字段："+key);}
}
