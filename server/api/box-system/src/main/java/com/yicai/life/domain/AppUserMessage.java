package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 用户消息对象 app_user_message
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("app_user_message")
public class AppUserMessage implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 消息所属用户
     */
    private Long userId;

    /**
     * 消息类型：0点赞1回复
     */
    private Long msgType;

    /**
     * 消息来源：0动态1评论
     */
    private Long msgFrom;

    /**
     * 来源用户
     */
    private Long fromUserId;

    /**
     * 来源用户昵称
     */
    private String fromNickName;

    /**
     * 来源用户头像
     */
    private String fromHead;

    /**
     * $column.columnComment
     */
    private Integer deleted;

    /**
     * 是否已读
     */
    private Integer readed;

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
