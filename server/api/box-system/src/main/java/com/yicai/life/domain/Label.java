package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 文章标签对象 label
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("label")
public class Label implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 名字
     */
    private String name;

    /**
     * 状态 1可用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 排序
     */
    private Long level;

}
