package com.yicai.life.service;

import com.yicai.life.domain.AppEssayUser;
import com.yicai.life.domain.vo.AppEssayUserVo;
import com.yicai.life.domain.bo.AppEssayUserBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 文章用户关联Service接口
 *
 * @author zhixia
 * @date 2022-05-16
 */
public interface IAppEssayUserService extends IServicePlus<AppEssayUser, AppEssayUserVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppEssayUserVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<AppEssayUserVo> queryPageList(AppEssayUserBo bo);

	/**
	 * 查询列表
	 */
	List<AppEssayUserVo> queryList(AppEssayUserBo bo);

	/**
	 * 根据新增业务对象插入文章用户关联
	 * @param bo 文章用户关联新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppEssayUserBo bo);

	/**
	 * 根据编辑业务对象修改文章用户关联
	 * @param bo 文章用户关联编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppEssayUserBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
