package com.yicai.life.service;

import com.yicai.life.domain.AppUserMessage;
import com.yicai.life.domain.vo.AppUserMessageVo;
import com.yicai.life.domain.bo.AppUserMessageBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 用户消息Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface IAppUserMessageService extends IServicePlus<AppUserMessage, AppUserMessageVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppUserMessageVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<AppUserMessageVo> queryPageList(AppUserMessageBo bo);

	/**
	 * 查询列表
	 */
	List<AppUserMessageVo> queryList(AppUserMessageBo bo);

	/**
	 * 根据新增业务对象插入用户消息
	 * @param bo 用户消息新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppUserMessageBo bo);

	/**
	 * 根据编辑业务对象修改用户消息
	 * @param bo 用户消息编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppUserMessageBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
