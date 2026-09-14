package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 文章标签对象 essay_label
 *
 * @author zhixia
 * @date 2022-02-25
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("essay_label")
public class EssayLabel implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 文章
     */
    private Long essay;

    /**
     * 标签
     */
    private Long label;

}
