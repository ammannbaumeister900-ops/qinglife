package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import lombok.Data;


/**
 * 联系信息视图对象 contact
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
public class ContactVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * $column.columnComment
     */
	@Excel(name = "${comment}" , readConverterExp = "$column.readConverterExp()")
	private String wechat;

    /**
     * $column.columnComment
     */
	@Excel(name = "${comment}" , readConverterExp = "$column.readConverterExp()")
	private String phone;


}
