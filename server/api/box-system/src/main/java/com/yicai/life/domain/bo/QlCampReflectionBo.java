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
 // New public requests must explicitly use the authenticated nickname.
 private Boolean anonymous;
 @Size(max=64) private String originalVersion;
 @Pattern(regexp="experience|reflection|new") private String source;
}
