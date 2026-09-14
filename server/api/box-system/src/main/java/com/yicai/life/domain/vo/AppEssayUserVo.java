package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 文章用户关联视图对象 app_essay_user
 *
 * @author zhixia
 * @date 2022-05-16
 */
@Data
public class AppEssayUserVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * 文章id
     */
	@Excel(name = "文章id")
	private Long essayId;

    /**
     * 用户id
     */
	@Excel(name = "用户id")
	private Long appUserId;

    /**
     * 状态 0：未读 1：已读
     */
	@Excel(name = "状态 0：未读 1：已读")
	private Long readState;

    /**
     * 已读时间
     */
	@Excel(name = "已读时间" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date readTime;


}
