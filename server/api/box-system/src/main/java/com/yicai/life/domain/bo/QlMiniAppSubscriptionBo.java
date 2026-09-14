package com.yicai.life.domain.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class QlMiniAppSubscriptionBo {
    @NotBlank(message = "订阅状态不能为空")
    private String status;
}
