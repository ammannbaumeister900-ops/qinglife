package com.yicai.life.domain.bo;

import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;


/**
 * 文章标签业务对象 essay_label
 *
 * @author zhixia
 * @date 2022-02-25
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class EssayLabelBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 文章
     */
    private Long essay;

    /**
     * 标签
     */
    private Long label;


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
