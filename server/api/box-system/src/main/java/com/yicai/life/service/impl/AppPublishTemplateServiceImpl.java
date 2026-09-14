package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
    import com.yicai.common.utils.PageUtils;
import cn.hutool.core.util.StrUtil;
import com.yicai.common.core.page.PagePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.life.domain.bo.AppPublishTemplateBo;
import com.yicai.life.domain.vo.AppPublishTemplateVo;
import com.yicai.life.domain.AppPublishTemplate;
import com.yicai.life.mapper.AppPublishTemplateMapper;
import com.yicai.life.service.IAppPublishTemplateService;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 动态模板Service业务层处理
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Service
public class AppPublishTemplateServiceImpl extends ServicePlusImpl<AppPublishTemplateMapper, AppPublishTemplate, AppPublishTemplateVo> implements IAppPublishTemplateService {

    @Override
    public AppPublishTemplateVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<AppPublishTemplateVo> queryPageList(AppPublishTemplateBo bo) {
        PagePlus<AppPublishTemplate, AppPublishTemplateVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<AppPublishTemplateVo> queryList(AppPublishTemplateBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppPublishTemplate> buildQueryWrapper(AppPublishTemplateBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppPublishTemplate> lqw = Wrappers.lambdaQuery();
        lqw.like(StrUtil.isNotBlank(bo.getTemplateName()), AppPublishTemplate::getTemplateName, bo.getTemplateName());
        lqw.eq(bo.getStatus() != null, AppPublishTemplate::getStatus, bo.getStatus());
        lqw.eq(bo.getIsDefault() != null, AppPublishTemplate::getIsDefault, bo.getIsDefault());
        lqw.eq(bo.getInsertTime() != null, AppPublishTemplate::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getInsertUser()), AppPublishTemplate::getInsertUser, bo.getInsertUser());
        lqw.eq(StrUtil.isNotBlank(bo.getUpdateUser()), AppPublishTemplate::getUpdateUser, bo.getUpdateUser());
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppPublishTemplateBo bo) {
        AppPublishTemplate add = BeanUtil.toBean(bo, AppPublishTemplate.class);
        add.setInsertTime(new Date());
        add.setInsertUser(SecurityUtils.getUsername());
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppPublishTemplateBo bo) {
        AppPublishTemplate update = BeanUtil.toBean(bo, AppPublishTemplate.class);
        update.setUpdateTime(new Date());
        update.setUpdateUser(SecurityUtils.getUsername());
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppPublishTemplate entity){
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
