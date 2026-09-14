package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 用户消息视图对象 app_user_message
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class AppUserMessageVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * 消息所属用户
     */
	@Excel(name = "消息所属用户")
	private Long userId;

    /**
     * 消息类型：0点赞1回复
     */
	@Excel(name = "消息类型：0点赞1回复")
	private Long msgType;

    /**
     * 消息来源：0动态1评论
     */
	@Excel(name = "消息来源：0动态1评论")
	private Long msgFrom;

    /**
     * 来源用户
     */
	@Excel(name = "来源用户")
	private Long fromUserId;

    /**
     * 来源用户昵称
     */
	@Excel(name = "来源用户昵称")
	private String fromNickName;

    /**
     * 来源用户头像
     */
	@Excel(name = "来源用户头像")
	private String fromHead;

    /**
     * 是否已读
     */
	@Excel(name = "是否已读")
	private Integer readed;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否已读" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否已读")
	private String insertUser;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否已读")
	private String updateUser;


}
