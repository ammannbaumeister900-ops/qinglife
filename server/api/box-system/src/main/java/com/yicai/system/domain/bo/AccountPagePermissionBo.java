package com.yicai.system.domain.bo;
import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
@Data
public class AccountPagePermissionBo {
    @NotNull private Boolean inherit;
    @NotNull @Size(max=1000) private List<Long> pageIds;
}
