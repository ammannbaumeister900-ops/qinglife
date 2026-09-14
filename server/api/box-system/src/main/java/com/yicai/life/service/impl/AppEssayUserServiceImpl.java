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
import com.yicai.life.domain.bo.AppEssayUserBo;
import com.yicai.life.domain.vo.AppEssayUserVo;
import com.yicai.life.domain.AppEssayUser;
import com.yicai.life.mapper.AppEssayUserMapper;
import com.yicai.life.service.IAppEssayUserService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 文章用户关联Service业务层处理
 *
 * @author zhixia
 * @date 2022-05-16
 */
@Service
public class AppEssayUserServiceImpl extends ServicePlusImpl<AppEssayUserMapper, AppEssayUser, AppEssayUserVo> implements IAppEssayUserService {

    @Override
    public AppEssayUserVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<AppEssayUserVo> queryPageList(AppEssayUserBo bo) {
        PagePlus<AppEssayUser, AppEssayUserVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<AppEssayUserVo> queryList(AppEssayUserBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppEssayUser> buildQueryWrapper(AppEssayUserBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppEssayUser> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getEssayId() != null, AppEssayUser::getEssayId, bo.getEssayId());
        lqw.eq(bo.getAppUserId() != null, AppEssayUser::getAppUserId, bo.getAppUserId());
        lqw.eq(bo.getReadState() != null, AppEssayUser::getReadState, bo.getReadState());
        lqw.eq(bo.getReadTime() != null, AppEssayUser::getReadTime, bo.getReadTime());
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppEssayUserBo bo) {
        AppEssayUser add = BeanUtil.toBean(bo, AppEssayUser.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppEssayUserBo bo) {
        AppEssayUser update = BeanUtil.toBean(bo, AppEssayUser.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppEssayUser entity){
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
