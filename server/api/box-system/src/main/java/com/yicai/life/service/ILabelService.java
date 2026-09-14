package com.yicai.life.service;

import com.yicai.life.domain.Label;
import com.yicai.life.domain.vo.LabelVo;
import com.yicai.life.domain.bo.LabelBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 文章标签Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface ILabelService extends IServicePlus<Label, LabelVo> {
	/**
	 * 查询单个
	 * @return
	 */
	LabelVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<LabelVo> queryPageList(LabelBo bo);

	/**
	 * 查询列表
	 */
	List<LabelVo> queryList(LabelBo bo);

	/**
	 * 根据新增业务对象插入文章标签
	 * @param bo 文章标签新增业务对象
	 * @return
	 */
	Boolean insertByBo(LabelBo bo);

	/**
	 * 根据编辑业务对象修改文章标签
	 * @param bo 文章标签编辑业务对象
	 * @return
	 */
	Boolean updateByBo(LabelBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
