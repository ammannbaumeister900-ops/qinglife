package com.yicai.life.domain.vo;

import com.yicai.common.annotation.Excel;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


/**
 * 状态模板项目视图对象 app_publish_template_project
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Data
public class AppPublishTemplateProjectVo {

	private static final long serialVersionUID = 1L;

	/**
     *  主键id
     */
	private Long id;

    /**
     * 模板id
     */
	@Excel(name = "模板id")
	private Long templateId;

    /**
     * 项目名称
     */
	@Excel(name = "项目名称")
	private String projectName;

    /**
     * 提示语
     */
	@Excel(name = "提示语")
	private String placeholder;

    /**
     * 字数上限
     */
	@Excel(name = "字数上限")
	private Long maxNum;

    /**
     * 是否必填 0：否 1：是
     */
	@Excel(name = "是否必填 0：否 1：是")
	private Long required;

    /**
     * 项目状态  1：可用  -1：禁用
     */
	@Excel(name = "项目状态  1：可用  -1：禁用")
	private Long status;

    /**
     * $column.columnComment
     */
	@Excel(name = "项目状态  1：可用  -1：禁用" , width = 30, dateFormat = "yyyy-MM-dd")
	private Date insertTime;

    /**
     * $column.columnComment
     */
	@Excel(name = "项目状态  1：可用  -1：禁用")
	private String insertUser;

    /**
     * $column.columnComment
     */
	@Excel(name = "项目状态  1：可用  -1：禁用")
	private String updateUser;

	private String templateName;


}
