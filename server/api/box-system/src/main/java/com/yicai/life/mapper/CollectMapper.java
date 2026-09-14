package com.yicai.life.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yicai.life.domain.Collect;
import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;
import com.yicai.life.domain.bo.CollectBo;
import com.yicai.life.domain.vo.CollectVo;
import org.apache.ibatis.annotations.Param;

/**
 * 用户收藏文章Mapper接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface CollectMapper extends BaseMapperPlus<Collect> {

    Page<CollectVo> selectPageList(@Param("page") Page<CollectVo> buildPage, @Param("bo") CollectBo bo);
}
