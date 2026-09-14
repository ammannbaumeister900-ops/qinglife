package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 用户动态点赞记录视图对象 app_user_publish_praise
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class AppUserPublishPraiseVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * 用户
     */
	@Excel(name = "用户")
	private Long userId;

    /**
     * 动态
     */
	@Excel(name = "动态")
	private Long publishId;

    /**
     * $column.columnComment
     */
	@Excel(name = "动态" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * $column.columnComment
     */
	@Excel(name = "动态")
	private String insertBy;

    /**
     * 用户昵称
     */
	@Excel(name = "用户昵称")
	private String nickName;


}
