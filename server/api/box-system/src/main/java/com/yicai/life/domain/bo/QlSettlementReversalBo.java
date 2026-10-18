package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class QlSettlementReversalBo {
    @javax.validation.constraints.Pattern(regexp="participant")
    private String mode;
    private String operationId;
    @javax.validation.constraints.Min(0) private Integer expectedRevision;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final java.util.Map<String,Object> extraFields=new java.util.LinkedHashMap<>();
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void extra(String key,Object value){extraFields.put(key,value);}
    @NotBlank(message = "撤销原因不能为空")
    @Size(max = 500, message = "撤销原因最多500字")
    private String reason;
}
