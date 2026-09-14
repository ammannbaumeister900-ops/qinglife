package com.yicai.life.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yicai.common.annotation.Excel;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 注册用户信息视图对象 app_user_info
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class AppUserInfoVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	@JsonSerialize(using = ToStringSerializer.class)
	private Long id;

    /**
     * openid
     */
	@Excel(name = "openid")
	private String openId;

    /**
     * 昵称
     */
	@Excel(name = "昵称")
	private String nickName;

    /**
     * 创建时间
     */
	@Excel(name = "创建时间" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * 头像
     */
	@Excel(name = "头像")
	private String head;

    /**
     * 最后登录时间
     */
	@Excel(name = "最后登录时间" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date lastLoginTime;

    /**
     * 性别1男 2女
     */
	@Excel(name = "性别1男 2女")
	private Integer gender;

    /**
     * 1启用 0禁用
     */
	@Excel(name = "1启用 0禁用")
	private Integer status;


}
