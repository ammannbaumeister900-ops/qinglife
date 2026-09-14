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
 * 用户动态点赞记录业务对象 app_user_publish_praise
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserPublishPraiseBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 用户
     */
    @NotNull(message = "用户不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long userId;

    /**
     * 动态
     */
    @NotNull(message = "动态不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long publishId;

    /**
     * $column.columnComment
     */
    private Date insertTime;

    /**
     * $column.columnComment
     */
    private String insertBy;

    /**
     * 用户昵称
     */
    private String nickName;


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
