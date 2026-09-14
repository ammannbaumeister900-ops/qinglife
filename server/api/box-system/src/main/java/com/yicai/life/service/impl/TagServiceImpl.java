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
import com.yicai.life.domain.bo.TagBo;
import com.yicai.life.domain.vo.TagVo;
import com.yicai.life.domain.Tag;
import com.yicai.life.mapper.TagMapper;
import com.yicai.life.service.ITagService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 动态标签Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class TagServiceImpl extends ServicePlusImpl<TagMapper, Tag, TagVo> implements ITagService {

    @Override
    public TagVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<TagVo> queryPageList(TagBo bo) {
        PagePlus<Tag, TagVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<TagVo> queryList(TagBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<Tag> buildQueryWrapper(TagBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<Tag> lqw = Wrappers.lambdaQuery();
        lqw.like(StrUtil.isNotBlank(bo.getName()), Tag::getName, bo.getName());
        lqw.orderByAsc(Tag::getSort);
        return lqw;
    }

    @Override
    @Transactional
    public Boolean insertByBo(TagBo bo) {
        Tag add = BeanUtil.toBean(bo, Tag.class);
        add.setInsertTime(new Date());
        add.setInsertBy(SecurityUtils.getUsername());
        // 该标签是否默认
        if(add.getDefaulted() == 1){
            updateDefaulted(add);
        }
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    @Transactional
    public Boolean updateByBo(TagBo bo) {
        Tag update = BeanUtil.toBean(bo, Tag.class);
        update.setUpdateTime(new Date());
        update.setUpdateBy(SecurityUtils.getUsername());
        if(update.getDefaulted() == 1){
            updateDefaulted(update);
        }
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 修改默认标签为非默认
     * @param tag
     * @return
     */
    public Boolean updateDefaulted(Tag tag){
        LambdaQueryWrapper<Tag> lqw =  new LambdaQueryWrapper<>();
        lqw.eq(Tag::getDefaulted, 1);
        Tag dTag = baseMapper.selectOne(lqw);
        boolean success = true;
        if(dTag != null){
            if(dTag.getId() != tag.getId()){
                dTag.setDefaulted(0);
                dTag.setUpdateTime(new Date());
                dTag.setUpdateBy(SecurityUtils.getUsername());
                success = updateById(dTag);
            }
        }
        return success;
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(Tag entity){
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
