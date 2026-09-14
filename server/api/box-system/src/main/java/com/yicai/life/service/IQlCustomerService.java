package com.yicai.life.service;

import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.life.domain.QlCustomer;
import com.yicai.life.domain.bo.QlCustomerBo;
import com.yicai.life.domain.vo.QlCustomerVo;

import java.util.List;
import java.util.Map;

public interface IQlCustomerService extends IServicePlus<QlCustomer, QlCustomerVo> {
    TableDataInfo<QlCustomerVo> queryPageList(QlCustomerBo bo);
    QlCustomerVo queryById(String id);
    List<Map<String, Object>> queryTimeline(String id);
    boolean insertByBo(QlCustomerBo bo, Long operatorId);
    boolean updateByBo(QlCustomerBo bo, Long operatorId);
}
