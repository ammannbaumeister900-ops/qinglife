package com.yicai.life.domain.bo;
import lombok.Data;
@Data
public class ArticlePlacementBo {
    private Integer expectedRevision;
    private Long essayId;
    private Boolean enabled;
    private Boolean authorizationConfirmed;
    private Boolean editorialApproved;
    private Boolean identityConfirmed;
    private String evidenceRef;
    private String expectedArticleHash;
}
