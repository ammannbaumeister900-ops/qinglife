package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 注册用户信息对象 app_user_info
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("app_user_info")
public class AppUserInfo implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
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

}
