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
import com.yicai.life.domain.bo.AppUserMessageBo;
import com.yicai.life.domain.vo.AppUserMessageVo;
import com.yicai.life.domain.AppUserMessage;
import com.yicai.life.mapper.AppUserMessageMapper;
import com.yicai.life.service.IAppUserMessageService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 用户消息Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class AppUserMessageServiceImpl extends ServicePlusImpl<AppUserMessageMapper, AppUserMessage, AppUserMessageVo> implements IAppUserMessageService {

    @Override
    public AppUserMessageVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<AppUserMessageVo> queryPageList(AppUserMessageBo bo) {
        PagePlus<AppUserMessage, AppUserMessageVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<AppUserMessageVo> queryList(AppUserMessageBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppUserMessage> buildQueryWrapper(AppUserMessageBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppUserMessage> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getUserId() != null, AppUserMessage::getUserId, bo.getUserId());
        lqw.eq(bo.getMsgType() != null, AppUserMessage::getMsgType, bo.getMsgType());
        lqw.eq(bo.getMsgFrom() != null, AppUserMessage::getMsgFrom, bo.getMsgFrom());
        lqw.eq(bo.getFromUserId() != null, AppUserMessage::getFromUserId, bo.getFromUserId());
        lqw.like(StrUtil.isNotBlank(bo.getFromNickName()), AppUserMessage::getFromNickName, bo.getFromNickName());
        lqw.eq(StrUtil.isNotBlank(bo.getFromHead()), AppUserMessage::getFromHead, bo.getFromHead());
        lqw.eq(bo.getReaded() != null, AppUserMessage::getReaded, bo.getReaded());
        lqw.eq(bo.getInsertTime() != null, AppUserMessage::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getInsertUser()), AppUserMessage::getInsertUser, bo.getInsertUser());
        lqw.eq(StrUtil.isNotBlank(bo.getUpdateUser()), AppUserMessage::getUpdateUser, bo.getUpdateUser());
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppUserMessageBo bo) {
        AppUserMessage add = BeanUtil.toBean(bo, AppUserMessage.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppUserMessageBo bo) {
        AppUserMessage update = BeanUtil.toBean(bo, AppUserMessage.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppUserMessage entity){
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
