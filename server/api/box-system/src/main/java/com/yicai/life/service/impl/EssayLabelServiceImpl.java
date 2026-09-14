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
import com.yicai.life.domain.bo.EssayLabelBo;
import com.yicai.life.domain.vo.EssayLabelVo;
import com.yicai.life.domain.EssayLabel;
import com.yicai.life.mapper.EssayLabelMapper;
import com.yicai.life.service.IEssayLabelService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 文章标签Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-25
 */
@Service
public class EssayLabelServiceImpl extends ServicePlusImpl<EssayLabelMapper, EssayLabel, EssayLabelVo> implements IEssayLabelService {

    @Override
    public EssayLabelVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<EssayLabelVo> queryPageList(EssayLabelBo bo) {
        PagePlus<EssayLabel, EssayLabelVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<EssayLabelVo> queryList(EssayLabelBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<EssayLabel> buildQueryWrapper(EssayLabelBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EssayLabel> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getEssay() != null, EssayLabel::getEssay, bo.getEssay());
        lqw.eq(bo.getLabel() != null, EssayLabel::getLabel, bo.getLabel());
        return lqw;
    }

    @Override
    public Boolean insertByBo(EssayLabelBo bo) {
        EssayLabel add = BeanUtil.toBean(bo, EssayLabel.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(EssayLabelBo bo) {
        EssayLabel update = BeanUtil.toBean(bo, EssayLabel.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(EssayLabel entity){
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
