package com.yicai.life.service;

import com.yicai.life.domain.EssayLabel;
import com.yicai.life.domain.vo.EssayLabelVo;
import com.yicai.life.domain.bo.EssayLabelBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 文章标签Service接口
 *
 * @author zhixia
 * @date 2022-02-25
 */
public interface IEssayLabelService extends IServicePlus<EssayLabel, EssayLabelVo> {
	/**
	 * 查询单个
	 * @return
	 */
	EssayLabelVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<EssayLabelVo> queryPageList(EssayLabelBo bo);

	/**
	 * 查询列表
	 */
	List<EssayLabelVo> queryList(EssayLabelBo bo);

	/**
	 * 根据新增业务对象插入文章标签
	 * @param bo 文章标签新增业务对象
	 * @return
	 */
	Boolean insertByBo(EssayLabelBo bo);

	/**
	 * 根据编辑业务对象修改文章标签
	 * @param bo 文章标签编辑业务对象
	 * @return
	 */
	Boolean updateByBo(EssayLabelBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
