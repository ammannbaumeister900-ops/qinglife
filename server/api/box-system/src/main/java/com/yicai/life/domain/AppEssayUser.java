package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 文章用户关联对象 app_essay_user
 *
 * @author zhixia
 * @date 2022-05-16
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("app_essay_user")
public class AppEssayUser implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 文章id
     */
    private Long essayId;

    /**
     * 用户id
     */
    private Long appUserId;

    /**
     * 状态 0：未读 1：已读
     */
    private Long readState;

    /**
     * 已读时间
     */
    private Date readTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * $column.columnComment
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * $column.columnComment
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * $column.columnComment
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

}
