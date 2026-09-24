package com.yicai.life.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yicai.common.annotation.Excel;
import java.util.Date;
import java.util.List;

import lombok.Data;


/**
 * 文章视图对象 essay
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class EssayVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	@JsonSerialize(using = ToStringSerializer.class)
	private Long id;

    /**
     * 标题
     */
	@Excel(name = "标题")
	private String title;

    /**
     * 内容
     */
	@Excel(name = "内容")
	private String content;

    /**
     * 介绍
     */
	@Excel(name = "介绍")
	@JsonProperty(value = "Introduction")
	private String Introduction;

    /**
     * 1使用
     */
	@Excel(name = "1使用")
	private Integer status;

	private Integer homeFeatured;

    /**
     * 排序，正序
     */
	@Excel(name = "排序，正序")
	private Long orderNum;

    /**
     * 创建时间
     */
	@Excel(name = "创建时间" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * 作者
     */
	@Excel(name = "作者")
	private Long author;

	private String authorNickName;

    /**
     * 封面
     */
	@Excel(name = "封面")
	private String titleUrl;

    /**
     * $column.columnComment
     */
	@Excel(name = "封面")
	private Long updateUser;

    /**
     * 是否有视频
     */
	@Excel(name = "是否有视频")
	private Integer isVideo;

	private String label;

	/**
	 * 标签数组
	 */
	private List<Long> labels;


}
