package com.yicai.life.domain.bo;

import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 状态模板项目业务对象 app_publish_template_project
 *
 * @author zhixia
 * @date 2022-09-27
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppPublishTemplateProjectBo extends BaseEntity {

    /**
     * 主键id
     */
    private Long id;

    /**
     * 模板id
     */
    private Long templateId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 提示语
     */
    private String placeholder;

    /**
     * 字数上限
     */
    private Long maxNum;

    /**
     * 是否必填 0：否 1：是
     */
    private Long required;

    /**
     * 项目状态  1：可用  -1：禁用
     */
    private Long status;

    /**
     * $column.columnComment
     */
    private Date insertTime;

    /**
     * $column.columnComment
     */
    private String insertUser;

    /**
     * $column.columnComment
     */
    private String updateUser;


    /**
     * 分页大小
     */
    private Integer pageSize;

    /**
     * 当前页数
     */
    private Integer pageNum;

    /**
     * 排序列
     */
    private String orderByColumn;

    /**
     * 排序的方向desc或者asc
     */
    private String isAsc;

}
