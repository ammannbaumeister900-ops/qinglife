package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
    import com.yicai.common.utils.PageUtils;
import cn.hutool.core.util.StrUtil;
import com.yicai.common.core.page.PagePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.AppPublishTemplate;
import com.yicai.life.mapper.AppPublishTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.life.domain.bo.AppPublishTemplateProjectBo;
import com.yicai.life.domain.vo.AppPublishTemplateProjectVo;
import com.yicai.life.domain.AppPublishTemplateProject;
import com.yicai.life.mapper.AppPublishTemplateProjectMapper;
import com.yicai.life.service.IAppPublishTemplateProjectService;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 状态模板项目Service业务层处理
 *
 * @author zhixia
 * @date 2022-09-27
 */
@Service
public class AppPublishTemplateProjectServiceImpl extends ServicePlusImpl<AppPublishTemplateProjectMapper, AppPublishTemplateProject, AppPublishTemplateProjectVo> implements IAppPublishTemplateProjectService {

    @Resource
    private AppPublishTemplateMapper appPublishTemplateMapper;

    @Override
    public AppPublishTemplateProjectVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<AppPublishTemplateProjectVo> queryPageList(AppPublishTemplateProjectBo bo) {
        PagePlus<AppPublishTemplateProject, AppPublishTemplateProjectVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        List<AppPublishTemplateProjectVo> list = result.getRecordsVo();
        for(AppPublishTemplateProjectVo vo : list){
            AppPublishTemplate appPublishTemplate = appPublishTemplateMapper.selectById(vo.getTemplateId());
            vo.setTemplateName(appPublishTemplate.getTemplateName());
        }
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<AppPublishTemplateProjectVo> queryList(AppPublishTemplateProjectBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppPublishTemplateProject> buildQueryWrapper(AppPublishTemplateProjectBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppPublishTemplateProject> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getTemplateId() != null, AppPublishTemplateProject::getTemplateId, bo.getTemplateId());
        lqw.like(StrUtil.isNotBlank(bo.getProjectName()), AppPublishTemplateProject::getProjectName, bo.getProjectName());
        lqw.eq(StrUtil.isNotBlank(bo.getPlaceholder()), AppPublishTemplateProject::getPlaceholder, bo.getPlaceholder());
        lqw.eq(bo.getMaxNum() != null, AppPublishTemplateProject::getMaxNum, bo.getMaxNum());
        lqw.eq(bo.getRequired() != null, AppPublishTemplateProject::getRequired, bo.getRequired());
        lqw.eq(bo.getStatus() != null, AppPublishTemplateProject::getStatus, bo.getStatus());
        lqw.eq(bo.getInsertTime() != null, AppPublishTemplateProject::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getInsertUser()), AppPublishTemplateProject::getInsertUser, bo.getInsertUser());
        lqw.eq(StrUtil.isNotBlank(bo.getUpdateUser()), AppPublishTemplateProject::getUpdateUser, bo.getUpdateUser());
        lqw.orderByDesc(AppPublishTemplateProject::getInsertTime);
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppPublishTemplateProjectBo bo) {
        AppPublishTemplateProject add = BeanUtil.toBean(bo, AppPublishTemplateProject.class);
        validEntityBeforeSave(add);
        add.setInsertTime(new Date());
        add.setInsertUser(SecurityUtils.getUsername());
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppPublishTemplateProjectBo bo) {
        AppPublishTemplateProject update = BeanUtil.toBean(bo, AppPublishTemplateProject.class);
        validEntityBeforeSave(update);
        update.setInsertTime(new Date());
        update.setInsertUser(SecurityUtils.getUsername());
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppPublishTemplateProject entity){
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
