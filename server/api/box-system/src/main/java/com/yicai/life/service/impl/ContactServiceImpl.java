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
import com.yicai.life.domain.bo.ContactBo;
import com.yicai.life.domain.vo.ContactVo;
import com.yicai.life.domain.Contact;
import com.yicai.life.mapper.ContactMapper;
import com.yicai.life.service.IContactService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 联系信息Service业务层处理
 *
 * @author zhixia
 * @date 2022-02-23
 */
@Service
public class ContactServiceImpl extends ServicePlusImpl<ContactMapper, Contact, ContactVo> implements IContactService {

    @Override
    public ContactVo queryById(Long id){
        return getVoById(id);
    }

    @Override
    public TableDataInfo<ContactVo> queryPageList(ContactBo bo) {
        PagePlus<Contact, ContactVo> result = pageVo(PageUtils.buildPagePlus(), buildQueryWrapper(bo));
        return PageUtils.buildDataInfo(result);
    }

    @Override
    public List<ContactVo> queryList(ContactBo bo) {
        return listVo(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<Contact> buildQueryWrapper(ContactBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<Contact> lqw = Wrappers.lambdaQuery();
        lqw.eq(StrUtil.isNotBlank(bo.getWechat()), Contact::getWechat, bo.getWechat());
        lqw.eq(StrUtil.isNotBlank(bo.getPhone()), Contact::getPhone, bo.getPhone());
        return lqw;
    }

    @Override
    public Boolean insertByBo(ContactBo bo) {
        Contact add = BeanUtil.toBean(bo, Contact.class);
        validEntityBeforeSave(add);
        return save(add);
    }

    @Override
    public Boolean updateByBo(ContactBo bo) {
        Contact update = BeanUtil.toBean(bo, Contact.class);
        validEntityBeforeSave(update);
        return updateById(update);
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(Contact entity){
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
