package com.yicai.life.domain.bo;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

@Data
public class QlReflectionPublishBatchBo {
    @Valid @NotEmpty @Size(max=50) private List<Item> items;
    @Data public static class Item extends QlReflectionModerationBo {
        @NotNull @Pattern(regexp="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") private String id;
    }
}
