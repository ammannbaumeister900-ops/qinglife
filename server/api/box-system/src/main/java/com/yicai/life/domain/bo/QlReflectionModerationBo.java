package com.yicai.life.domain.bo;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class QlReflectionModerationBo {
 @NotNull @Min(1) private Integer revision;
 @Size(max=80) private String excerpt;
 @Size(max=300) private String displayNote;
 @Min(0) @Max(999) private Integer sortOrder;
}
