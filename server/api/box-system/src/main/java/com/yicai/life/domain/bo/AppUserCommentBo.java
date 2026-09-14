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
 * 动态评论业务对象 app_user_comment
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserCommentBo extends BaseEntity {

    /**
     * $column.columnComment
     */
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
