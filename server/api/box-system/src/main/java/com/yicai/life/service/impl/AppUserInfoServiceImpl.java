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
import com.yicai.life.domain.bo.AppUserInfoBo;
import com.yicai.life.domain.vo.AppUserInfoVo;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import com.yicai.life.service.IAppUserInfoService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 注册用户信息Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class AppUserInfoServiceImpl extends ServicePlusImpl<AppUserInfoMapper, AppUserInfo, AppUserInfoVo> implements IAppUserInfoService {

    @Override
    public AppUserInfoVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<AppUserInfoVo> queryPageList(AppUserInfoBo bo) {
        PagePlus<AppUserInfo, AppUserInfoVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<AppUserInfoVo> queryList(AppUserInfoBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppUserInfo> buildQueryWrapper(AppUserInfoBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppUserInfo> lqw = Wrappers.lambdaQuery();
        lqw.eq(StrUtil.isNotBlank(bo.getOpenId()), AppUserInfo::getOpenId, bo.getOpenId());
        lqw.like(StrUtil.isNotBlank(bo.getNickName()), AppUserInfo::getNickName, bo.getNickName());
        lqw.eq(bo.getInsertTime() != null, AppUserInfo::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getHead()), AppUserInfo::getHead, bo.getHead());
        lqw.eq(bo.getLastLoginTime() != null, AppUserInfo::getLastLoginTime, bo.getLastLoginTime());
        lqw.eq(bo.getGender() != null, AppUserInfo::getGender, bo.getGender());
        lqw.eq(bo.getStatus() != null, AppUserInfo::getStatus, bo.getStatus());
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppUserInfoBo bo) {
        AppUserInfo add = BeanUtil.toBean(bo, AppUserInfo.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppUserInfoBo bo) {
        AppUserInfo update = BeanUtil.toBean(bo, AppUserInfo.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppUserInfo entity){
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
