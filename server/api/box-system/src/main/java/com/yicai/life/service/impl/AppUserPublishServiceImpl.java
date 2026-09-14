package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.utils.PageUtils;
import com.yicai.life.domain.AppUserPublish;
import com.yicai.life.domain.bo.AppUserPublishBo;
import com.yicai.life.domain.vo.AppUserPublishVo;
import com.yicai.life.mapper.AppUserPublishMapper;
import com.yicai.life.service.IAppUserPublishService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户动态Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class AppUserPublishServiceImpl extends ServicePlusImpl<AppUserPublishMapper, AppUserPublish, AppUserPublishVo> implements IAppUserPublishService {

    @Resource
    private AppUserPublishMapper appUserPublishMapper;

    @Override
    public AppUserPublishVo queryById(Long id){
        return getVoById(id);
    }


    @Override
    public TableDataInfo<AppUserPublishVo> selectPageList(AppUserPublishBo bo) {
        Map<String, Object> params = bo.getParams();
        if(params.get("beginTime") != null){
            bo.setBeginTime(params.get("beginTime").toString());
        }
        if(params.get("endTime") != null){
            bo.setEndTime(params.get("endTime").toString());
        }
        TableDataInfo<AppUserPublishVo> t = PageUtils.buildDataInfo(appUserPublishMapper.selectPageList(PageUtils.buildPage(),bo));
        for(AppUserPublishVo vo : t.getRows()){
            if(StringUtils.isNotBlank(vo.getImages())){
                String imgs[] = vo.getImages().split(",");
                List<String> imgPathList = new ArrayList<>();
                for(String img : imgs){
                    img = RuoYiConfig.getImagePath() + img;
                    imgPathList.add(img);
                }
                vo.setImageList(imgPathList);
                vo.setImages(vo.getImageList().get(0));
            }
        }
        return t;
    }

    @Override
    public List<AppUserPublishVo> queryList(AppUserPublishBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<AppUserPublish> buildQueryWrapper(AppUserPublishBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<AppUserPublish> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getUserId() != null, AppUserPublish::getUserId, bo.getUserId());
        lqw.eq(StrUtil.isNotBlank(bo.getContent()), AppUserPublish::getContent, bo.getContent());
        lqw.eq(StrUtil.isNotBlank(bo.getTag()), AppUserPublish::getTag, bo.getTag());
        lqw.eq(StrUtil.isNotBlank(bo.getImages()), AppUserPublish::getImages, bo.getImages());
        lqw.eq(bo.getInsertTime() != null, AppUserPublish::getInsertTime, bo.getInsertTime());
        lqw.eq(StrUtil.isNotBlank(bo.getInsertUser()), AppUserPublish::getInsertUser, bo.getInsertUser());
        lqw.eq(StrUtil.isNotBlank(bo.getUpdateUser()), AppUserPublish::getUpdateUser, bo.getUpdateUser());
        lqw.eq(bo.getPraiseNum() != null, AppUserPublish::getPraiseNum, bo.getPraiseNum());
        lqw.eq(bo.getCommentNum() != null, AppUserPublish::getCommentNum, bo.getCommentNum());
        return lqw;
    }

    @Override
    public Boolean insertByBo(AppUserPublishBo bo) {
        AppUserPublish add = BeanUtil.toBean(bo, AppUserPublish.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(AppUserPublishBo bo) {
        AppUserPublish update = BeanUtil.toBean(bo, AppUserPublish.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(AppUserPublish entity){
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
