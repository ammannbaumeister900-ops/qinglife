package com.yicai.life.service;

import com.yicai.life.domain.Contact;
import com.yicai.life.domain.vo.ContactVo;
import com.yicai.life.domain.bo.ContactBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 联系信息Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface IContactService extends IServicePlus<Contact, ContactVo> {
	/**
	 * 查询单个
	 * @return
	 */
	ContactVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<ContactVo> queryPageList(ContactBo bo);

	/**
	 * 查询列表
	 */
	List<ContactVo> queryList(ContactBo bo);

	/**
	 * 根据新增业务对象插入联系信息
	 * @param bo 联系信息新增业务对象
	 * @return
	 */
	Boolean insertByBo(ContactBo bo);

	/**
	 * 根据编辑业务对象修改联系信息
	 * @param bo 联系信息编辑业务对象
	 * @return
	 */
	Boolean updateByBo(ContactBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
