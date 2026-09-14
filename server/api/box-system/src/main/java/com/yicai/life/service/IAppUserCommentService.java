package com.yicai.life.service;

import com.yicai.life.domain.AppUserComment;
import com.yicai.life.domain.vo.AppUserCommentVo;
import com.yicai.life.domain.bo.AppUserCommentBo;
import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 动态评论Service接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface IAppUserCommentService extends IServicePlus<AppUserComment, AppUserCommentVo> {
	/**
	 * 查询单个
	 * @return
	 */
	AppUserCommentVo queryById(Long id);

	/**
	 * 查询列表
	 */
    TableDataInfo<AppUserCommentVo> queryPageList(AppUserCommentBo bo);

	/**
	 * 查询列表
	 */
	List<AppUserCommentVo> queryList(AppUserCommentBo bo);

	/**
	 * 根据新增业务对象插入动态评论
	 * @param bo 动态评论新增业务对象
	 * @return
	 */
	Boolean insertByBo(AppUserCommentBo bo);

	/**
	 * 根据编辑业务对象修改动态评论
	 * @param bo 动态评论编辑业务对象
	 * @return
	 */
	Boolean updateByBo(AppUserCommentBo bo);

	/**
	 * 校验并删除数据
	 * @param ids 主键集合
	 * @param isValid 是否校验,true-删除前校验,false-不校验
	 * @return
	 */
	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
