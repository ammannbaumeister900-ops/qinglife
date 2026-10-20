package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/** Per-publication operational conclusions; never a customer age or identity profile. */
@Data
public class QlPublicationReviewBo {
    @NotNull @Min(1) @Max(1) private Integer version;
    @NotNull @Pattern(regexp="ready|deferred") private String decision;
    @Size(max=300) private String contentSafetyConclusion;
    @Size(max=300) private String identityConclusion;
    @Pattern(regexp="satisfied|not_applicable|insufficient") private String authorizationConclusion;
    @NotBlank @Size(max=600) private String basisSummary;
    @Valid @Size(max=5) private List<Reference> references;
    @Data public static class Reference {
        @NotNull @Pattern(regexp="registration|assessment|interaction|consent") private String type;
        @NotNull @Pattern(regexp="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") private String id;
        @Pattern(regexp="[0-9a-f]{64}") private String sourceHash;
    }
}
