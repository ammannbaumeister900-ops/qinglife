package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.utils.PageUtils;
import com.yicai.life.domain.QlCustomer;
import com.yicai.life.domain.bo.QlCustomerBo;
import com.yicai.life.domain.vo.QlCustomerVo;
import com.yicai.life.mapper.QlCustomerMapper;
import com.yicai.life.service.IQlCustomerService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QlCustomerServiceImpl extends ServicePlusImpl<QlCustomerMapper, QlCustomer, QlCustomerVo>
        implements IQlCustomerService {

    @Override
    public TableDataInfo<QlCustomerVo> queryPageList(QlCustomerBo bo) {
        Page<QlCustomerVo> page = baseMapper.selectPageList(PageUtils.buildPage(), bo);
        return PageUtils.buildDataInfo(page);
    }

    @Override
    public QlCustomerVo queryById(String id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public List<Map<String, Object>> queryTimeline(String id) {
        return baseMapper.selectTimeline(id);
    }

    @Override
    public boolean insertByBo(QlCustomerBo bo, Long operatorId) {
        QlCustomer entity = BeanUtil.toBean(bo, QlCustomer.class);
        Date now = new Date();
        entity.setId(UUID.randomUUID().toString());
        entity.setCustomerNo(nextCustomerNo());
        entity.setGender(StrUtil.blankToDefault(entity.getGender(), "unknown"));
        entity.setFirstSource(StrUtil.blankToDefault(entity.getFirstSource(), "unknown"));
        entity.setDataSource(StrUtil.blankToDefault(entity.getDataSource(), "manual_entry"));
        entity.setDataConfidence(StrUtil.blankToDefault(entity.getDataConfidence(), "unknown"));
        entity.setStatus(StrUtil.blankToDefault(entity.getStatus(), "active"));
        entity.setRevision(0);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return save(entity);
    }

    @Override
    public boolean updateByBo(QlCustomerBo bo, Long operatorId) {
        QlCustomer entity = BeanUtil.toBean(bo, QlCustomer.class);
        entity.setCustomerNo(null);
        entity.setCreatedAt(null);
        entity.setCreatedBy(null);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(new Date());
        return updateById(entity);
    }

    private LambdaQueryWrapper<QlCustomer> buildQuery(QlCustomerBo bo) {
        LambdaQueryWrapper<QlCustomer> query = Wrappers.lambdaQuery();
        query.and(StrUtil.isNotBlank(bo.getNickname()), wrapper -> wrapper
                .like(QlCustomer::getNickname, bo.getNickname())
                .or().like(QlCustomer::getRealName, bo.getNickname())
                .or().like(QlCustomer::getCustomerNo, bo.getNickname()));
        query.eq(StrUtil.isNotBlank(bo.getStatus()), QlCustomer::getStatus, bo.getStatus());
        query.eq(StrUtil.isNotBlank(bo.getCity()), QlCustomer::getCity, bo.getCity());
        query.orderByDesc(QlCustomer::getUpdatedAt);
        return query;
    }

    private String nextCustomerNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        return "QY" + System.currentTimeMillis() + suffix;
    }
}
