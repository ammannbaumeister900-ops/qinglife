package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.yicai.common.core.page.PagePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.utils.PageUtils;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.domain.*;
import com.yicai.life.domain.bo.EssayBo;
import com.yicai.life.domain.vo.EssayVo;
import com.yicai.life.mapper.*;
import com.yicai.life.service.IEssayService;
import com.yicai.system.mapper.SysUserMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

/**
 * 文章Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class EssayServiceImpl extends ServicePlusImpl<EssayMapper, Essay, EssayVo> implements IEssayService {
    @Resource
    private EssayLabelMapper essayLabelMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private LabelMapper labelMapper;
    @Resource
    private AppEssayUserMapper essayUserMapper;
    @Resource
    private AppUserInfoMapper userInfoMapper;

    @Override
    public EssayVo queryById(Long id){
        EssayVo vo = getVoById(id);
        LambdaQueryWrapper<EssayLabel> lqw = new LambdaQueryWrapper();
        lqw.eq(EssayLabel::getEssay, vo.getId());
        lqw.orderByAsc(EssayLabel::getId);
        List<EssayLabel> list = essayLabelMapper.selectList(lqw);
        List<Long> labels = new ArrayList<>();
        for(int i=0; i<list.size(); i++){
            labels.add(list.get(i).getLabel());
        }
        vo.setLabels(labels);
        return vo;
    }

    @Override
    public TableDataInfo<EssayVo> queryPageList(EssayBo bo) {
        PagePlus<Essay, EssayVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        for(EssayVo vo : result.getRecordsVo()){
            if(StringUtils.isNotBlank(vo.getTitleUrl())){
                vo.setTitleUrl(RuoYiConfig.getImagePath() + vo.getTitleUrl());
            }
            SysUser user = sysUserMapper.selectUserById(vo.getAuthor());
            vo.setAuthorNickName(user.getNickName());

            String tags = "";
            LambdaQueryWrapper<EssayLabel> lqw = new LambdaQueryWrapper();
            lqw.eq(EssayLabel::getEssay, vo.getId());
            lqw.orderByAsc(EssayLabel::getId);
            List<EssayLabel> list = essayLabelMapper.selectList(lqw);
            for(int i=0; i< list.size(); i++ ){
                Label label = labelMapper.selectById(list.get(i).getLabel());
                if(i + 1 != list.size()){
                    tags += label.getName() + "&";
                }else{
                    tags += label.getName();
                }
            }
            vo.setLabel(tags);
        }
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<EssayVo> queryList(EssayBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<Essay> buildQueryWrapper(EssayBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<Essay> lqw = Wrappers.lambdaQuery();
        lqw.eq(StrUtil.isNotBlank(bo.getTitle()), Essay::getTitle, bo.getTitle());
        lqw.eq(StrUtil.isNotBlank(bo.getContent()), Essay::getContent, bo.getContent());
        lqw.eq(StrUtil.isNotBlank(bo.getIntroduction()), Essay::getIntroduction, bo.getIntroduction());
        lqw.eq(bo.getStatus() != null, Essay::getStatus, bo.getStatus());
        lqw.eq(bo.getHomeFeatured() != null, Essay::getHomeFeatured, bo.getHomeFeatured());
        lqw.eq(bo.getOrderNum() != null, Essay::getOrderNum, bo.getOrderNum());
        lqw.eq(bo.getInsertTime() != null, Essay::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getTitleUrl()), Essay::getTitleUrl, bo.getTitleUrl());
        lqw.eq(bo.getUpdateUser() != null, Essay::getUpdateUser, bo.getUpdateUser());
        lqw.eq(bo.getIsVideo() != null, Essay::getIsVideo, bo.getIsVideo());
        lqw.orderByAsc(Essay::getOrderNum,Essay::getId);
        return lqw;
    }

    @Override
    @Transactional
    public Boolean insertByBo(EssayBo bo) {
        boolean result = true;
        Essay add = BeanUtil.toBean(bo, Essay.class);
        add.setInsertTime(LocalDateTimeUtil.now());
        add.setAuthor(SecurityUtils.getLoginUser().getUser().getUserId());
        validEntityBeforeSave(add);
        int row = baseMapper.insert(add);
        if(row > 0){
            if(bo.getLabels() != null && bo.getLabels().length > 0){
                for(Long labelId : bo.getLabels()){
                    EssayLabel essayLabel = new EssayLabel();
                    essayLabel.setEssay(add.getId());
                    essayLabel.setLabel(labelId);
                    essayLabelMapper.insert(essayLabel);
                }
            }
            List<AppEssayUser> essayUsers = new ArrayList<>();
            // 获取appUserList
            LambdaQueryWrapper<AppUserInfo> lqw = new LambdaQueryWrapper<>();
            lqw.orderByAsc(AppUserInfo::getInsertTime);
            List<AppUserInfo> users = userInfoMapper.selectList(lqw);
            for(AppUserInfo user : users){
                AppEssayUser essayUser = new AppEssayUser();
                essayUser.setEssayId(add.getId());
                essayUser.setAppUserId(user.getId());
                essayUser.setCreateTime(new Date());
                essayUser.setReadState(0l);
                essayUser.setCreateBy(SecurityUtils.getLoginUser().getUser().getUserName());
                essayUsers.add(essayUser);
            }
            essayUserMapper.insertAll(essayUsers);
        }else{
            result = false;
        }
        return result;
    }

    @Override
    @Transactional
    public Boolean updateByBo(EssayBo bo) {
        boolean result = true;
        Essay update = BeanUtil.toBean(bo, Essay.class);
        update.setUpdateUser(SecurityUtils.getLoginUser().getUser().getUserId());
        update.setUpdateTime(LocalDateTimeUtil.now());
        validEntityBeforeSave(update);
        result = updateById(update);

        if(result){
            if(bo.isUpdateNotRead()){
                LambdaQueryWrapper<AppEssayUser> essayUserLqw = new LambdaQueryWrapper<>();
                essayUserLqw.eq(AppEssayUser::getEssayId, update.getId());
                List<AppEssayUser> essayUsers = essayUserMapper.selectList(essayUserLqw);
                if(essayUsers != null){
                    for(AppEssayUser essayUser : essayUsers){
                        essayUser.setReadState(0l);
                        essayUser.setReadTime(null);
                        essayUserMapper.updateById(essayUser);
                    }
                }else{
                    // 获取appUserList
                    LambdaQueryWrapper<AppUserInfo> lqw = new LambdaQueryWrapper<>();
                    lqw.orderByAsc(AppUserInfo::getInsertTime);
                    List<AppUserInfo> users = userInfoMapper.selectList(lqw);
                    for(AppUserInfo user : users){
                        AppEssayUser essayUser = new AppEssayUser();
                        essayUser.setEssayId(update.getId());
                        essayUser.setAppUserId(user.getId());
                        essayUser.setCreateTime(new Date());
                        essayUser.setReadState(0l);
                        essayUser.setCreateBy(SecurityUtils.getLoginUser().getUser().getUserName());
                        essayUsers.add(essayUser);
                    }
                    essayUserMapper.insertAll(essayUsers);
                }
            }
            LambdaQueryWrapper<EssayLabel> lqw = new LambdaQueryWrapper<>();
            lqw.eq(EssayLabel::getEssay, bo.getId());
            List<EssayLabel> list = essayLabelMapper.selectList(lqw);
            if(bo.getLabels().length == 0){
                if(list != null){
                    for(EssayLabel label : list){
                        essayLabelMapper.deleteById(label.getId());
                    }
                }
            }else{
                // 判断新标签在旧标签中是否存在
                for(Long labelId : bo.getLabels()){
                    boolean exist = false;
                    for(EssayLabel essayLabel : list){
                        if(labelId.equals(essayLabel.getLabel())){
                            exist = true;
                        }
                    }
                    //不存在，添加数据
                    if(!exist){
                        EssayLabel essayLabel = new EssayLabel();
                        essayLabel.setEssay(update.getId());
                        essayLabel.setLabel(labelId);
                        essayLabelMapper.insert(essayLabel);
                    }
                }
                // 判断已有数据是否存在新数据
                for(EssayLabel essayLabel : list){
                    boolean exist = false;
                    for(Long labelId : bo.getLabels()){
                        if(essayLabel.getLabel().equals(labelId)){
                            exist = true;
                        }
                    }
                    if(!exist){
                        //删除已有数据
                        essayLabelMapper.deleteById(essayLabel.getId());
                    }
                }
            }
        }else{
            result = false;
        }
        return result;
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(Essay entity){
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
    public boolean changeStatus(EssayBo bo) {
        Essay update = BeanUtil.toBean(bo, Essay.class);
        update.setUpdateUser(SecurityUtils.getLoginUser().getUser().getUserId());
        update.setUpdateTime(LocalDateTimeUtil.now());
        validEntityBeforeSave(update);
        return updateById(update);
    }
}
