package com.yicai.life.domain.bo;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class QlCampReflectionBo {
 @NotBlank @Size(max=36) private String registrationId;
 @NotBlank @Pattern(regexp="before|after") private String phase;
 @NotNull @Min(0) private Integer revision;
 @NotNull @Size(max=300) private String note;
 @NotBlank @Pattern(regexp="private|submit") private String action;
 private Boolean shareConsent;
 // Missing in older clients, which only support anonymous sharing.
 private Boolean anonymous;
}
