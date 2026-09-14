package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 动态评论视图对象 app_user_comment
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class AppUserCommentVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * 评论人
     */
	@Excel(name = "评论人")
	private Long userId;

    /**
     * 评论人昵称
     */
	@Excel(name = "评论人昵称")
	private String nickName;

    /**
     * 动态
     */
	@Excel(name = "动态")
	private Long publishId;

    /**
     * 回复的评论
     */
	@Excel(name = "回复的评论")
	private Long commentId;

    /**
     * 评论内容
     */
	@Excel(name = "评论内容")
	private String content;

    /**
     * $column.columnComment
     */
	@Excel(name = "评论内容")
	private Long revesion;

    /**
     * $column.columnComment
     */
	@Excel(name = "评论内容" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * $column.columnComment
     */
	@Excel(name = "评论内容")
	private String insertUser;

    /**
     * $column.columnComment
     */
	@Excel(name = "评论内容")
	private String updateUser;


}
