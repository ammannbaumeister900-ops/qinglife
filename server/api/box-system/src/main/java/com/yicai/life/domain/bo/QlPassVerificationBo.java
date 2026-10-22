package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class QlPassVerificationBo {
    @NotBlank private String operationId;
    @NotNull @Min(0) private Integer expectedRevision;
    private String registrationId;
    @NotBlank private String passAccountId;
    @NotNull @Min(1) @Max(65535) private Integer passUnits;
    @NotBlank @Pattern(regexp="self|same_order") private String scope;
    @NotBlank @Size(max=600) private String basis;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key,Object value){throw new IllegalArgumentException("未知卡次核实字段："+key);}
}
