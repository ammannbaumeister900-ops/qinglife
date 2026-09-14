package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.utils.PageUtils;
import cn.hutool.core.util.StrUtil;
import com.yicai.common.core.page.PagePlus;
import com.yicai.common.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.life.domain.bo.CollectBo;
import com.yicai.life.domain.vo.CollectVo;
import com.yicai.life.domain.Collect;
import com.yicai.life.mapper.CollectMapper;
import com.yicai.life.service.ICollectService;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 用户收藏文章Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class CollectServiceImpl extends ServicePlusImpl<CollectMapper, Collect, CollectVo> implements ICollectService {

    @Resource
    private CollectMapper collectMapper;

    @Override
    public CollectVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<CollectVo> queryPageList(CollectBo bo) {
        PagePlus<Collect, CollectVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<CollectVo> queryList(CollectBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<Collect> buildQueryWrapper(CollectBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<Collect> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getEssay() != null, Collect::getEssay, bo.getEssay());
        lqw.eq(bo.getUserId() != null, Collect::getUserId, bo.getUserId());
        lqw.eq(bo.getInsertTime() != null, Collect::getInsertTime, bo.getInsertTime());
        return lqw;
    }

    @Override
    public Boolean insertByBo(CollectBo bo) {
        Collect add = BeanUtil.toBean(bo, Collect.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(CollectBo bo) {
        Collect update = BeanUtil.toBean(bo, Collect.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(Collect entity){
        //TODO 做一些数据校验,如唯一约束
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return removeByIds(ids);
    }

    @Override
    public TableDataInfo<CollectVo> selectPageList(CollectBo bo) {
        TableDataInfo<CollectVo> t = PageUtils.buildDataInfo(collectMapper.selectPageList(PageUtils.buildPage(),bo));
        for(CollectVo vo : t.getRows()){
            vo.setTitleUrl(RuoYiConfig.getImagePath() + vo.getTitleUrl());
        }
        return t;
    }
}
