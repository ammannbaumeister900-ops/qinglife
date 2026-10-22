package com.yicai.life.domain.bo;
import lombok.Data;
import javax.validation.constraints.*;
/** Public reading directory filters. */
@Data
public class QlReadingQueryBo {
    @NotNull @Min(1) @Max(100000) private Integer pageNum=1;
    @NotNull @Min(1) @Max(50) private Integer pageSize=12;
    @Pattern(regexp="[0-9]{1,19}") private String labelId;
    @Size(max=100) private String query;
    private boolean stories;
}