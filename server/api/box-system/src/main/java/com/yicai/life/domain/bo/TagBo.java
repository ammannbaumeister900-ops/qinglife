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
 * 动态标签业务对象 tag
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class TagBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 动态tag
     */
    private String name;

    /**
     * 排序
     */
    private Long sort;

    /**
     * 是否默认
     */
    private Integer defaulted;

    /**
     * $column.columnComment
     */
    private Date insertTime;

    /**
     * $column.columnComment
     */
    private String insertBy;


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
