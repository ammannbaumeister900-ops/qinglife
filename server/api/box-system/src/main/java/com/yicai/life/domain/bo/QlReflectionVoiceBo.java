package com.yicai.life.domain.bo;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class QlReflectionVoiceBo {
 @NotBlank @Size(max=36) private String registrationId;
 @NotBlank @Pattern(regexp="before|after") private String phase;
 @NotBlank @Size(max=2800000) private String audio;
 @NotNull @Min(1) @Max(45000) private Integer durationMs;
 @AssertTrue(message="请先同意本次语音转文字处理") private boolean voiceConsent;
}
