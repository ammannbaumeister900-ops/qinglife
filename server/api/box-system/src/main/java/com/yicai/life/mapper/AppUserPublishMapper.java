package com.yicai.life.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yicai.life.domain.AppUserPublish;
import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;
import com.yicai.life.domain.bo.AppUserPublishBo;
import com.yicai.life.domain.vo.AppUserPublishVo;
import org.apache.ibatis.annotations.Param;

/**
 * 用户动态Mapper接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface AppUserPublishMapper extends BaseMapperPlus<AppUserPublish> {

    Page<AppUserPublishVo> selectPageList(@Param("page")Page<AppUserPublishVo> buildPage, @Param("bo") AppUserPublishBo bo);
}
