package com.yicai.life.domain.bo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

@Data
public class QlPassAccountBo {
    @NotBlank(message = "请选择轻友")
    private String customerId;
    @NotBlank(message = "请填写卡次名称")
    @Size(max = 50, message = "卡次名称最多50字")
    private String passType;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date validFrom;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date validUntil;
    @NotNull(message = "请填写初始卡次")
    @Min(value = 1, message = "初始卡次至少为1")
    private Integer initialUnits;
    @NotBlank(message = "请填写开户或核验依据")
    @Size(max = 500, message = "依据最多500字")
    private String reason;
}
