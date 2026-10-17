package com.yicai.life.domain.bo;

import lombok.Data;
import java.util.*;

/** Offline article-only input. IDs must come from an authorized export, never a title or sequence guess. */
@Data
public class EssayImportBo {
    private String sourceId;
    private String sourceUrl;
    private String title;
    private String introduction;
    private String content;
    private String authorName;
    private String publishedAt;
    private String timeBasis;
    private String cover;
    private Integer status;
    private Integer orderNum;
    private String approvalRef;
    private List<LabelInput> labels = new ArrayList<>();
    private Map<String,String> mediaMap = new TreeMap<>();
    @Data public static class LabelInput {
        private String sourceId;
        private String name;
        private Integer status;
        private Integer level;
    }
}
