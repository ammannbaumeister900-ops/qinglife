package com.yicai.life.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yicai.common.annotation.Excel;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 用户动态视图对象 app_user_publish
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class AppUserPublishVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	@JsonSerialize(using = ToStringSerializer.class)
	private Long id;

    /**
     * 用户
     */
	@Excel(name = "用户")
	private Long userId;

	private String nickName;

    /**
     * 动态内容
     */
	@Excel(name = "动态内容")
	private String content;

    /**
     * 标签
     */
	@Excel(name = "标签")
	private String tag;

    /**
     * 配图，分号隔开
     */
	@Excel(name = "配图，分号隔开")
	private String images;

    /**
     * $column.columnComment
     */
	@Excel(name = "配图，分号隔开" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * $column.columnComment
     */
	@Excel(name = "配图，分号隔开")
	private String insertUser;

    /**
     * $column.columnComment
     */
	@Excel(name = "配图，分号隔开")
	private String updateUser;

    /**
     * 点赞数
     */
	@Excel(name = "点赞数")
	private Long praiseNum;

    /**
     * 评论数
     */
	@Excel(name = "评论数")
	private Long commentNum;

	private List<String> imageList;

	private Integer templateId;

}
