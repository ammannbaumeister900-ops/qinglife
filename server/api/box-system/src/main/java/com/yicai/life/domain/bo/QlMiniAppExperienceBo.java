package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class QlMiniAppExperienceBo {
    @NotBlank private String registrationId;
    @NotBlank private String phase;
    private String sessionDayId;
    @Min(1) @Max(5) private Integer energy;
    @Min(1) @Max(5) private Integer relaxation;
    @Size(max = 1000) private String note;
}
