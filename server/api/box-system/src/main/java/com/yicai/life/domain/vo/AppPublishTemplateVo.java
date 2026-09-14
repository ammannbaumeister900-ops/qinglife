package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 动态模板视图对象 app_publish_template
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Data
public class AppPublishTemplateVo {

	private static final long serialVersionUID = 1L;

	/**
     *  主键id
     */
	private Long id;

    /**
     * 模板名称
     */
	@Excel(name = "模板名称")
	private String templateName;

    /**
     * 模板状态  1可用  -1禁用
     */
	@Excel(name = "模板状态  1可用  -1禁用")
	private Long status;

    /**
     * 是否默认  0：否 1：是
     */
	@Excel(name = "是否默认  0：否 1：是")
	private Long isDefault;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否默认  0：否 1：是" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否默认  0：否 1：是")
	private String insertUser;

    /**
     * $column.columnComment
     */
	@Excel(name = "是否默认  0：否 1：是")
	private String updateUser;


}
