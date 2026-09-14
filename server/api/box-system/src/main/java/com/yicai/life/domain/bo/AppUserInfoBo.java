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
 * 注册用户信息业务对象 app_user_info
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserInfoBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * openid
     */
    private String openId;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 创建时间
     */
    private Date insertTime;

    /**
     * 头像
     */
    private String head;

    /**
     * 最后登录时间
     */
    private Date lastLoginTime;

    /**
     * 性别1男 2女
     */
    private Integer gender;

    /**
     * 1启用 0禁用
     */
    private Integer status;


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
