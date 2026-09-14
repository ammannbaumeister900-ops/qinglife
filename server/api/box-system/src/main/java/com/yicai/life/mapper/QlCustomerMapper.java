package com.yicai.life.mapper;

import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;
import com.yicai.life.domain.QlCustomer;
import com.yicai.life.domain.bo.QlCustomerBo;
import com.yicai.life.domain.vo.QlCustomerVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface QlCustomerMapper extends BaseMapperPlus<QlCustomer> {
    Page<QlCustomerVo> selectPageList(@Param("page") Page<QlCustomerVo> page,
                                      @Param("bo") QlCustomerBo bo);
    QlCustomerVo selectVoById(@Param("id") String id);
    List<Map<String, Object>> selectTimeline(@Param("id") String id);
}
