package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 动态标签视图对象 tag
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class TagVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * 动态tag
     */
	@Excel(name = "动态tag")
	private String name;

    /**
     * 排序
     */
	@Excel(name = "排序")
	private Long sort;

    /**
     * 是否默认
     */
	@Excel(name = "是否默认")
	private Integer defaulted;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否默认" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否默认")
	private String insertBy;


}
