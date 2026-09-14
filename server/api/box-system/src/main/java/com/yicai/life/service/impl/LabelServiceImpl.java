package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.PageUtils;
import cn.hutool.core.util.StrUtil;
import com.yicai.common.core.page.PagePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.life.domain.EssayLabel;
import com.yicai.life.mapper.EssayLabelMapper;
import org.springframework.stereotype.Service;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.life.domain.bo.LabelBo;
import com.yicai.life.domain.vo.LabelVo;
import com.yicai.life.domain.Label;
import com.yicai.life.mapper.LabelMapper;
import com.yicai.life.service.ILabelService;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 文章标签Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class LabelServiceImpl extends ServicePlusImpl<LabelMapper, Label, LabelVo> implements ILabelService {
    @Resource
    private EssayLabelMapper essayLabelMapper;
    @Override
    public LabelVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<LabelVo> queryPageList(LabelBo bo) {
        PagePlus<Label, LabelVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<LabelVo> queryList(LabelBo bo) {
        LambdaQueryWrapper<Label> lqw = Wrappers.lambdaQuery();
        lqw.eq(Label::getStatus, 1);
        lqw.orderByAsc(Label::getLevel);
        return listVo(lqw);
    }

    private LambdaQueryWrapper<Label> buildQueryWrapper(LabelBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<Label> lqw = Wrappers.lambdaQuery();
        lqw.like(StrUtil.isNotBlank(bo.getName()), Label::getName, bo.getName());
        lqw.eq(bo.getStatus() != null, Label::getStatus, bo.getStatus());
        lqw.orderByAsc(Label::getLevel);
        return lqw;
    }

    @Override
    public Boolean insertByBo(LabelBo bo) {
        Label add = BeanUtil.toBean(bo, Label.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(LabelBo bo) {
        Label update = BeanUtil.toBean(bo, Label.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(Label entity){
        //TODO 做一些数据校验,如唯一约束
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        for(Long id : ids){
            LambdaQueryWrapper<EssayLabel> lqw = new LambdaQueryWrapper<>();
            lqw.eq(EssayLabel::getLabel, id);
            int count = essayLabelMapper.selectCount(lqw);
            if(count > 0){
                throw  new CustomException("该标签正在使用中，不可删除!");
            }
        }
        return removeByIds(ids);
    }
}
