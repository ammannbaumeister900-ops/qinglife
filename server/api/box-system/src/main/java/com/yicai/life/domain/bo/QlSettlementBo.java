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
    @javax.validation.constraints.Pattern(regexp="participant")
    private String mode;
    private String operationId;
    @Min(0) private Integer expectedRevision;
    @Size(max=100) private java.util.List<String> allocationIds;
    private Boolean differenceConfirmed;
    @Size(max=500) private String differenceReason;
    @Size(max=500) private String reason;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final java.util.Map<String,Object> extraFields=new java.util.LinkedHashMap<>();
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void extra(String name,Object value) {extraFields.put(name,value);}
    @DecimalMin(value = "0", message = "最终金额不能小于0")
    private BigDecimal finalAmount;
    @NotBlank(message = "结算方式不能为空")
    private String settlementType;
    @Min(value = 0, message = "卡次不能小于0")
    private Integer passUnits;
    private String passAccountId;
    private String verificationId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @javax.validation.constraints.AssertTrue(message="最终金额不能为空")
    public boolean isFinalAmountProvided(){return "participant".equals(mode)&&"pass".equals(settlementType)||finalAmount!=null;}
    @Size(max = 500, message = "结算说明最多500字")
    private String note;
}
