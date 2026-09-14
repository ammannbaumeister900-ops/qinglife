package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 动态评论对象 app_user_comment
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("app_user_comment")
public class AppUserComment implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 评论人
     */
    private Long userId;

    /**
     * 评论人昵称
     */
    private String nickName;

    /**
     * 动态
     */
    private Long publishId;

    /**
     * 回复的评论
     */
    private Long commentId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * $column.columnComment
     */
    private Integer deleted;

    /**
     * $column.columnComment
     */
    private Long revesion;

    /**
     * $column.columnComment
     */
    private Date insertTime;

    /**
     * $column.columnComment
     */
    private String insertUser;

    /**
     * $column.columnComment
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * $column.columnComment
     */
    private String updateUser;

}
