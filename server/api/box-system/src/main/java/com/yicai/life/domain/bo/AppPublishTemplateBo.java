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
 * 动态模板业务对象 app_publish_template
 *
 * @author zhixia
 * @date 2022-09-27
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AppPublishTemplateBo extends BaseEntity {

    /**
     * 主键id
     */
    private Long id;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板状态  1可用  -1禁用
     */
    private Long status;

    /**
     * 是否默认  0：否 1：是
     */
    private Long isDefault;

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
