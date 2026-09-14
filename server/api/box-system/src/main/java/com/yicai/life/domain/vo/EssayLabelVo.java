package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import lombok.Data;


/**
 * 文章标签视图对象 essay_label
 *
 * @author zhixia
 * @date 2022-02-25
 */
@Data
public class EssayLabelVo {

	private static final long serialVersionUID = 1L;

	/**
     *  $pkColumn.columnComment
     */
	private Long id;

    /**
     * 文章
     */
	@Excel(name = "文章")
	private Long essay;

    /**
     * 标签
     */
	@Excel(name = "标签")
	private Long label;


}
