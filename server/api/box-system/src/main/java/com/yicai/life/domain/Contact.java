package com.yicai.life.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 联系信息对象 contact
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("contact")
public class Contact implements Serializable {

    private static final long serialVersionUID=1L;


    /**
     * $column.columnComment
     */
    private String wechat;

    /**
     * $column.columnComment
     */
    private String phone;

    /**
     * $column.columnComment
     */
    @TableId(value = "id")
    private Long id;

}
