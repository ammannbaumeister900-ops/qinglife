package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

@Data
public class QlMiniAppQuoteBo {
    @NotBlank private String sessionId;
    @Valid @NotEmpty @Size(max = 10) private List<QlMiniAppRegistrationBo.Participant> participants;
}
