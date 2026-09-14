package com.yicai.life.service;

import com.yicai.life.domain.Collect;
import com.yicai.life.domain.vo.CollectVo;
import com.yicai.life.domain.bo.CollectBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 用户收藏文章Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface ICollectService extends IServicePlus<Collect, CollectVo> {
	/**
	 * 查询单个
	 * @return
	 */
	CollectVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<CollectVo> queryPageList(CollectBo bo);

	/**
	 * 查询列表
	 */
	List<CollectVo> queryList(CollectBo bo);

	/**
	 * 根据新增业务对象插入用户收藏文章
	 * @param bo 用户收藏文章新增业务对象
	 * @return
	 */
	Boolean insertByBo(CollectBo bo);

	/**
	 * 根据编辑业务对象修改用户收藏文章
	 * @param bo 用户收藏文章编辑业务对象
	 * @return
	 */
	Boolean updateByBo(CollectBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

	TableDataInfo<CollectVo> selectPageList(CollectBo bo);
}
