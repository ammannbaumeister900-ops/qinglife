package com.yicai.life.service;

import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.domain.vo.AppUserInfoVo;
import com.yicai.life.domain.bo.AppUserInfoBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 注册用户信息Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface IAppUserInfoService extends IServicePlus<AppUserInfo, AppUserInfoVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppUserInfoVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<AppUserInfoVo> queryPageList(AppUserInfoBo bo);

	/**
	 * 查询列表
	 */
	List<AppUserInfoVo> queryList(AppUserInfoBo bo);

	/**
	 * 根据新增业务对象插入注册用户信息
	 * @param bo 注册用户信息新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppUserInfoBo bo);

	/**
	 * 根据编辑业务对象修改注册用户信息
	 * @param bo 注册用户信息编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppUserInfoBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
