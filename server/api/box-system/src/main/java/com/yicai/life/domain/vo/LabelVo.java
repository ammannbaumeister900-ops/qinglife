package com.yicai.life.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yicai.common.annotation.Excel;
import lombok.Data;


/**
 * 文章标签视图对象 label
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class LabelVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	@JsonSerialize(using = ToStringSerializer.class)
	private Long id;

    /**
     * 名字
     */
	@Excel(name = "名字")
	private String name;

    /**
     * 状态 1可用
     */
	@Excel(name = "状态 1可用")
	private Integer status;

    /**
     * 备注
     */
	@Excel(name = "备注")
	private String remark;

    /**
     * 排序
     */
	@Excel(name = "排序")
	private Long level;


}
