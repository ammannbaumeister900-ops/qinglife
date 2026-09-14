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
 * 文章用户关联业务对象 app_essay_user
 *
 * @author zhixia
 * @date 2022-05-16
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppEssayUserBo extends BaseEntity {

    /**
     * $column.columnComment
     */
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
