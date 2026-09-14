package com.yicai.life.service;

import com.yicai.life.domain.AppUserPublishPraise;
import com.yicai.life.domain.vo.AppUserPublishPraiseVo;
import com.yicai.life.domain.bo.AppUserPublishPraiseBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 用户动态点赞记录Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface IAppUserPublishPraiseService extends IServicePlus<AppUserPublishPraise, AppUserPublishPraiseVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppUserPublishPraiseVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<AppUserPublishPraiseVo> queryPageList(AppUserPublishPraiseBo bo);

	/**
	 * 查询列表
	 */
	List<AppUserPublishPraiseVo> queryList(AppUserPublishPraiseBo bo);

	/**
	 * 根据新增业务对象插入用户动态点赞记录
	 * @param bo 用户动态点赞记录新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppUserPublishPraiseBo bo);

	/**
	 * 根据编辑业务对象修改用户动态点赞记录
	 * @param bo 用户动态点赞记录编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppUserPublishPraiseBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
