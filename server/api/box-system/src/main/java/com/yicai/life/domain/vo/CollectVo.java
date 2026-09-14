package com.yicai.life.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 用户收藏文章视图对象 collect
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class CollectVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * 文章id
     */
	@Excel(name = "文章id")
	private Long essay;

	private String essayTitle;
	private String titleUrl;
	@JsonProperty(value = "Introduction")
	private String Introduction;

    /**
     * $column.columnComment
     */
	@Excel(name = "文章id")
	private Long userId;

	private String nickName;
    /**
     * $column.columnComment
     */
	@Excel(name = "文章id" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;


}
