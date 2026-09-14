package com.yicai.life.domain.bo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yicai.common.core.domain.BaseEntity;
import com.yicai.common.core.validate.AddGroup;
import com.yicai.common.core.validate.EditGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 文章业务对象 essay
 *
 * @author zhixia
 * @date 2022-02-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class EssayBo extends BaseEntity {

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 介绍
     */
    @JsonProperty(value = "Introduction")
    private String Introduction;

    /**
     * 1使用
     */
    private Integer status;

    /**
     * 排序，正序
     */
    private Long orderNum;

    /**
     * 创建时间
     */
    private Date insertTime;

    /**
     * 作者
     */
    private Long author;

    /**
     * 封面
     */
    private String titleUrl;

    /**
     * $column.columnComment
     */
    private Long updateUser;

    /**
     * 是否有视频
     */
    private Integer isVideo;


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

    /**
     * 标签数组
     */
    private Long labels[];

    private boolean updateNotRead;
}
