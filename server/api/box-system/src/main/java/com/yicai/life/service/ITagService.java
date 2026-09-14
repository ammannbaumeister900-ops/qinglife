package com.yicai.life.service;

import com.yicai.life.domain.Tag;
import com.yicai.life.domain.vo.TagVo;
import com.yicai.life.domain.bo.TagBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 动态标签Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface ITagService extends IServicePlus<Tag, TagVo> {
	/**
	 * 查询单个
	 * @return
	 */
	TagVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<TagVo> queryPageList(TagBo bo);

	/**
	 * 查询列表
	 */
	List<TagVo> queryList(TagBo bo);

	/**
	 * 根据新增业务对象插入动态标签
	 * @param bo 动态标签新增业务对象
	 * @return
	 */
	Boolean insertByBo(TagBo bo);

	/**
	 * 根据编辑业务对象修改动态标签
	 * @param bo 动态标签编辑业务对象
	 * @return
	 */
	Boolean updateByBo(TagBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
