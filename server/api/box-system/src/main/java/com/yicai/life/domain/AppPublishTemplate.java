package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 动态模板对象 app_publish_template
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("app_publish_template")
public class AppPublishTemplate implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * 主键id
     */
    @TableId(value = "id")
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
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * $column.columnComment
     */
    private String updateUser;

}
