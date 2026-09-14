package com.yicai.life.service;

import com.yicai.life.domain.AppUserPublish;
import com.yicai.life.domain.vo.AppUserPublishVo;
import com.yicai.life.domain.bo.AppUserPublishBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 用户动态Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface IAppUserPublishService extends IServicePlus<AppUserPublish, AppUserPublishVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppUserPublishVo queryById(Long id);

	/**
	 * 查询列表
	 */
	List<AppUserPublishVo> queryList(AppUserPublishBo bo);

	/**
	 * 根据新增业务对象插入用户动态
	 * @param bo 用户动态新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppUserPublishBo bo);

	/**
	 * 根据编辑业务对象修改用户动态
	 * @param bo 用户动态编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppUserPublishBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    TableDataInfo<AppUserPublishVo> selectPageList(AppUserPublishBo bo);
}
