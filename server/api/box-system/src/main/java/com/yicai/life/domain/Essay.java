package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 文章对象 essay
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("essay")
public class Essay implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 介绍
     */
    @JsonProperty(value = "Introduction")
    private String Introduction;

    /**
     * 1使用
     */
    private Integer status;

    /**
     * 排序，正序
     */
    private Long orderNum;

    /**
     * 创建时间
     */
    private LocalDateTime insertTime;

    /**
     * 作者
     */
    private Long author;

    /**
     * 封面
     */
    private String titleUrl;

    /**
     * $column.columnComment
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * $column.columnComment
     */
    private Long updateUser;

    /**
     * 是否有视频
     */
    private Integer isVideo;

}
