package com.yicai.life.domain.bo;

import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;


/**
 * 联系信息业务对象 contact
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class ContactBo extends BaseEntity {

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
    private Long id;


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
