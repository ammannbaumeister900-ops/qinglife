package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
    import com.yicai.common.utils.PageUtils;
import cn.hutool.core.util.StrUtil;
import com.yicai.common.core.page.PagePlus;
import com.yicai.common.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.life.domain.bo.AppUserPublishPraiseBo;
import com.yicai.life.domain.vo.AppUserPublishPraiseVo;
import com.yicai.life.domain.AppUserPublishPraise;
import com.yicai.life.mapper.AppUserPublishPraiseMapper;
import com.yicai.life.service.IAppUserPublishPraiseService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 用户动态点赞记录Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class AppUserPublishPraiseServiceImpl extends ServicePlusImpl<AppUserPublishPraiseMapper, AppUserPublishPraise, AppUserPublishPraiseVo> implements IAppUserPublishPraiseService {

    @Override
    public AppUserPublishPraiseVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<AppUserPublishPraiseVo> queryPageList(AppUserPublishPraiseBo bo) {
        PagePlus<AppUserPublishPraise, AppUserPublishPraiseVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<AppUserPublishPraiseVo> queryList(AppUserPublishPraiseBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppUserPublishPraise> buildQueryWrapper(AppUserPublishPraiseBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppUserPublishPraise> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getUserId() != null, AppUserPublishPraise::getUserId, bo.getUserId());
        lqw.eq(bo.getPublishId() != null, AppUserPublishPraise::getPublishId, bo.getPublishId());
        lqw.eq(bo.getInsertTime() != null, AppUserPublishPraise::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getInsertBy()), AppUserPublishPraise::getInsertBy, bo.getInsertBy());
        lqw.like(StrUtil.isNotBlank(bo.getNickName()), AppUserPublishPraise::getNickName, bo.getNickName());
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppUserPublishPraiseBo bo) {
        AppUserPublishPraise add = BeanUtil.toBean(bo, AppUserPublishPraise.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppUserPublishPraiseBo bo) {
        AppUserPublishPraise update = BeanUtil.toBean(bo, AppUserPublishPraise.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppUserPublishPraise entity){
        //TODO 做一些数据校验,如唯一约束
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return removeByIds(ids);
    }
}
