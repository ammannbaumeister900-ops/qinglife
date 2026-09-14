package com.yicai.life.service;

import com.yicai.life.domain.Essay;
import com.yicai.life.domain.vo.EssayVo;
import com.yicai.life.domain.bo.EssayBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 文章Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface IEssayService extends IServicePlus<Essay, EssayVo> {
	/**
	 * 查询单个
	 * @return
	 */
	EssayVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<EssayVo> queryPageList(EssayBo bo);

	/**
	 * 查询列表
	 */
	List<EssayVo> queryList(EssayBo bo);

	/**
	 * 根据新增业务对象插入文章
	 * @param bo 文章新增业务对象
	 * @return
	 */
	Boolean insertByBo(EssayBo bo);

	/**
	 * 根据编辑业务对象修改文章
	 * @param bo 文章编辑业务对象
	 * @return
	 */
	Boolean updateByBo(EssayBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    boolean changeStatus(EssayBo bo);
}
