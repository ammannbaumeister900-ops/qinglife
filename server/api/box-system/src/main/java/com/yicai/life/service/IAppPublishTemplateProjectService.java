package com.yicai.life.service;

import com.yicai.life.domain.AppPublishTemplateProject;
import com.yicai.life.domain.vo.AppPublishTemplateProjectVo;
import com.yicai.life.domain.bo.AppPublishTemplateProjectBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 状态模板项目Service接口
 *
 * @author zhixia
 * @date 2022-09-27
 */
public interface IAppPublishTemplateProjectService extends IServicePlus<AppPublishTemplateProject, AppPublishTemplateProjectVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppPublishTemplateProjectVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<AppPublishTemplateProjectVo> queryPageList(AppPublishTemplateProjectBo bo);

	/**
	 * 查询列表
	 */
	List<AppPublishTemplateProjectVo> queryList(AppPublishTemplateProjectBo bo);

	/**
	 * 根据新增业务对象插入状态模板项目
	 * @param bo 状态模板项目新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppPublishTemplateProjectBo bo);

	/**
	 * 根据编辑业务对象修改状态模板项目
	 * @param bo 状态模板项目编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppPublishTemplateProjectBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
