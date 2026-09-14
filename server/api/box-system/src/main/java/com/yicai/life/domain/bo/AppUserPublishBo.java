package com.yicai.life.domain.bo;

import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户动态业务对象 app_user_publish
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserPublishBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 用户
     */
    private Long userId;

    private String nickName;

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

    private String beginTime;
    private String endTime;

//    @Override
//    private Map<String, Object> params = new HashMap<>();

}
