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
 * 用户收藏文章业务对象 collect
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class CollectBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 文章id
     */
    private Long essay;

    /**
     * $column.columnComment
     */
    private Long userId;

    /**
     * $column.columnComment
     */
    private Date insertTime;


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

    private String nickName;
    private String essayTitle;

}
