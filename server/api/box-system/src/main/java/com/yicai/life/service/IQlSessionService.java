package com.yicai.life.service;

import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.life.domain.QlSession;
import com.yicai.life.domain.bo.QlSessionBo;
import com.yicai.life.domain.vo.QlSessionVo;

public interface IQlSessionService extends IServicePlus<QlSession, QlSessionVo> {
    TableDataInfo<QlSessionVo> queryPageList(QlSessionBo bo);
    QlSessionVo queryById(String id);
    boolean insertByBo(QlSessionBo bo, Long operatorId);
    boolean updateByBo(QlSessionBo bo, Long operatorId);
}
