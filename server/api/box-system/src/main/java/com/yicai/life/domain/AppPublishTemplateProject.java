package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 状态模板项目对象 app_publish_template_project
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("app_publish_template_project")
public class AppPublishTemplateProject implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * 主键id
     */
    @TableId(value = "id")
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
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * $column.columnComment
     */
    private String updateUser;

}
