package com.yicai.system.service;

import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.system.domain.SysOss;
import com.yicai.system.domain.bo.SysOssBo;
import com.yicai.system.domain.vo.SysOssVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;

/**
 * 文件上传 服务层
 *
 * @author Lion Li
 */
public interface ISysOssService extends IServicePlus<SysOss, SysOssVo> {

	TableDataInfo<SysOssVo> queryPageList(SysOssBo sysOss);

	SysOss upload(MultipartFile file);

	Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
