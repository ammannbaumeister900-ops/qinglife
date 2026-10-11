package com.yicai.life.domain.bo;
import lombok.Data;
import java.math.BigDecimal;
import javax.validation.constraints.*;
@Data
public class QlOfflineReceiptBo {
    @NotBlank @Pattern(regexp="wechat_scan|alipay_scan|transfer|cash|other")
    private String paymentMethod;
    @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2)
    private BigDecimal amount;
    @Size(max=100)
    private String receiptReference;
    @Size(max=500)
    private String evidence;
    @AssertTrue(message="请先核实实际到账") @NotNull
    private Boolean receivedConfirmed;
    @Pattern(regexp="[0-9a-fA-F-]{36}",message="操作标识无效")
    private String requestId;
    @Pattern(regexp="[0-9a-fA-F-]{36}",message="收款记录标识无效")
    private String receiptId;
    @Min(0) private Integer expectedRevision;
    @Size(max=200) private String correctionReason;
    private Boolean finalSettlementConfirmed;
    @Size(max=200) private String differenceReason;
    private Boolean partialPayment;
    private Boolean defer;
    @javax.validation.Valid @Size(max=3,message="最多上传3张图片")
    private java.util.List<Image> attachments;
    @Data public static class Image {
        @NotBlank private String mime;
        @NotBlank @Size(max=2800000,message="图片不能超过2MB") private String data;
    }
}
