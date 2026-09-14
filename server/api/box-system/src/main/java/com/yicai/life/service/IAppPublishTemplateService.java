package com.yicai.life.service;

import com.yicai.life.domain.AppPublishTemplate;
import com.yicai.life.domain.vo.AppPublishTemplateVo;
import com.yicai.life.domain.bo.AppPublishTemplateBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 动态模板Service接口
 *
 * @author zhixia
 * @date 2022-09-27
 */
public interface IAppPublishTemplateService extends IServicePlus<AppPublishTemplate, AppPublishTemplateVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppPublishTemplateVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<AppPublishTemplateVo> queryPageList(AppPublishTemplateBo bo);

	/**
	 * 查询列表
	 */
	List<AppPublishTemplateVo> queryList(AppPublishTemplateBo bo);

	/**
	 * 根据新增业务对象插入动态模板
	 * @param bo 动态模板新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppPublishTemplateBo bo);

	/**
	 * 根据编辑业务对象修改动态模板
	 * @param bo 动态模板编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppPublishTemplateBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
