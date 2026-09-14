package com.yicai.life.domain.bo;

import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户消息业务对象 app_user_message
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserMessageBo extends BaseEntity {

    /**
     * $column.columnComment
     */
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
    private String updateUser;


    /**
     * 分页大小
     */
    private Integer pageSize;

    /**
     * 当前页数
     */
    private Integer pageNum;

    /**
     * 排序列
     */
    private String orderByColumn;

    /**
     * 排序的方向desc或者asc
     */
    private String isAsc;

}
