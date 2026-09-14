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
import com.yicai.life.domain.bo.AppUserCommentBo;
import com.yicai.life.domain.vo.AppUserCommentVo;
import com.yicai.life.domain.AppUserComment;
import com.yicai.life.mapper.AppUserCommentMapper;
import com.yicai.life.service.IAppUserCommentService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 动态评论Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class AppUserCommentServiceImpl extends ServicePlusImpl<AppUserCommentMapper, AppUserComment, AppUserCommentVo> implements IAppUserCommentService {

    @Override
    public AppUserCommentVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<AppUserCommentVo> queryPageList(AppUserCommentBo bo) {
        PagePlus<AppUserComment, AppUserCommentVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<AppUserCommentVo> queryList(AppUserCommentBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppUserComment> buildQueryWrapper(AppUserCommentBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppUserComment> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getUserId() != null, AppUserComment::getUserId, bo.getUserId());
        lqw.like(StrUtil.isNotBlank(bo.getNickName()), AppUserComment::getNickName, bo.getNickName());
        lqw.eq(bo.getPublishId() != null, AppUserComment::getPublishId, bo.getPublishId());
        lqw.eq(bo.getCommentId() != null, AppUserComment::getCommentId, bo.getCommentId());
        lqw.eq(StrUtil.isNotBlank(bo.getContent()), AppUserComment::getContent, bo.getContent());
        lqw.eq(bo.getRevesion() != null, AppUserComment::getRevesion, bo.getRevesion());
        lqw.eq(bo.getInsertTime() != null, AppUserComment::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getInsertUser()), AppUserComment::getInsertUser, bo.getInsertUser());
        lqw.eq(StrUtil.isNotBlank(bo.getUpdateUser()), AppUserComment::getUpdateUser, bo.getUpdateUser());
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppUserCommentBo bo) {
        AppUserComment add = BeanUtil.toBean(bo, AppUserComment.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppUserCommentBo bo) {
        AppUserComment update = BeanUtil.toBean(bo, AppUserComment.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppUserComment entity){
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
