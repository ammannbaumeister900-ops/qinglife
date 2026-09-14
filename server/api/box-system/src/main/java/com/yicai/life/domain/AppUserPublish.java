package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 用户动态对象 app_user_publish
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("app_user_publish")
public class AppUserPublish implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 用户
     */
    private Long userId;

    /**
     * 动态内容
     */
    private String content;

    /**
     * 标签
     */
    private String tag;

    /**
     * 配图，分号隔开
     */
    private String images;

    /**
     * 逻辑删除
     */
    private Integer deleted;

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

    /**
     * 点赞数
     */
    private Long praiseNum;

    /**
     * 评论数
     */
    private Long commentNum;

    /**
     * $column.columnComment
     */
    private Long revision;

    private Integer templateId;

}
