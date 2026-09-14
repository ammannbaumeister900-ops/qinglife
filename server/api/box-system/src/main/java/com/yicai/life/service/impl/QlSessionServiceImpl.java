package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.yicai.common.core.page.PagePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.PageUtils;
import com.yicai.life.domain.QlSession;
import com.yicai.life.domain.bo.QlSessionBo;
import com.yicai.life.domain.vo.QlSessionVo;
import com.yicai.life.mapper.QlSessionMapper;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.IQlSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QlSessionServiceImpl extends ServicePlusImpl<QlSessionMapper, QlSession, QlSessionVo>
        implements IQlSessionService {
    private final QlMiniAppMapper miniAppMapper;

    @Override
    public TableDataInfo<QlSessionVo> queryPageList(QlSessionBo bo) {
        PagePlus<QlSession, QlSessionVo> page = pageVo(PageUtils.buildPagePlus(), buildQuery(bo));
        return PageUtils.buildDataInfo(page);
    }

    @Override
    public QlSessionVo queryById(String id) {
        return getVoById(id);
    }

    @Override
    @Transactional
    public boolean insertByBo(QlSessionBo bo, Long operatorId) {
        normalizeBookingDays(bo);
        validateDates(bo);
        validateSettings(bo);
        QlSession entity = BeanUtil.toBean(bo, QlSession.class);
        Date now = new Date();
        entity.setId(UUID.randomUUID().toString());
        entity.setCapacity(entity.getCapacity() == null ? 1 : entity.getCapacity());
        entity.setStandardPrice(entity.getStandardPrice() == null ? new BigDecimal("3800") : entity.getStandardPrice());
        entity.setReturningPrice(entity.getReturningPrice() == null ? new BigDecimal("2500") : entity.getReturningPrice());
        entity.setStatus(StrUtil.blankToDefault(entity.getStatus(), "draft"));
        entity.setRegistrationConfirmMode(StrUtil.blankToDefault(entity.getRegistrationConfirmMode(), "manual"));
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        boolean saved = save(entity);
        if (saved) {
            syncSessionDays(entity.getId(), bo.getStartDate(), bo.getEndDate(), operatorId, now);
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean updateByBo(QlSessionBo bo, Long operatorId) {
        normalizeBookingDays(bo);
        validateDates(bo);
        validateSettings(bo);
        QlSession entity = BeanUtil.toBean(bo, QlSession.class);
        entity.setCreatedAt(null);
        entity.setCreatedBy(null);
        entity.setUpdatedBy(operatorId);
        Date now = new Date();
        entity.setUpdatedAt(now);
        boolean updated = updateById(entity);
        if (updated) {
            syncSessionDays(entity.getId(), bo.getStartDate(), bo.getEndDate(), operatorId, now);
        }
        return updated;
    }

    private LambdaQueryWrapper<QlSession> buildQuery(QlSessionBo bo) {
        LambdaQueryWrapper<QlSession> query = Wrappers.lambdaQuery();
        query.eq(bo.getSessionNumber() != null, QlSession::getSessionNumber, bo.getSessionNumber());
        query.like(StrUtil.isNotBlank(bo.getName()), QlSession::getName, bo.getName());
        query.eq(StrUtil.isNotBlank(bo.getStatus()), QlSession::getStatus, bo.getStatus());
        query.orderByDesc(QlSession::getSessionNumber);
        return query;
    }

    private void normalizeBookingDays(QlSessionBo bo) {
        Calendar date = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        if (bo.getRegistrationOpenAt() != null) {
            date.setTime(bo.getRegistrationOpenAt()); date.set(Calendar.HOUR_OF_DAY,0); date.set(Calendar.MINUTE,0); date.set(Calendar.SECOND,0); date.set(Calendar.MILLISECOND,0);
            bo.setRegistrationOpenAt(date.getTime());
        }
        if (bo.getRegistrationCloseAt() != null) {
            date.setTime(bo.getRegistrationCloseAt()); date.set(Calendar.HOUR_OF_DAY,23); date.set(Calendar.MINUTE,59); date.set(Calendar.SECOND,59); date.set(Calendar.MILLISECOND,0);
            bo.setRegistrationCloseAt(date.getTime());
        }
    }

    private void validateSettings(QlSessionBo bo) {
        if (bo.getStandardPrice() != null && (bo.getStandardPrice().signum() < 0 || bo.getStandardPrice().compareTo(new BigDecimal("99999999.99")) > 0)
                || bo.getReturningPrice() != null && (bo.getReturningPrice().signum() < 0 || bo.getReturningPrice().compareTo(new BigDecimal("99999999.99")) > 0)) {
            throw new CustomException("价格必须在0至99999999.99元之间");
        }
        if (StrUtil.isNotBlank(bo.getRegistrationConfirmMode()) && !java.util.Arrays.asList("manual", "auto").contains(bo.getRegistrationConfirmMode())) throw new CustomException("报名确认方式无效");
        if (StrUtil.isNotBlank(bo.getLeaderName())) {
            QlSession current = StrUtil.isBlank(bo.getId()) ? null : getById(bo.getId());
            if (current == null || !bo.getLeaderName().equals(current.getLeaderName())) {
                for (String guide : bo.getLeaderName().split("[,，、]")) {
                    if (!java.util.Arrays.asList("公主", "大海", "军军").contains(guide.trim())) throw new CustomException("请选择已有导游标签");
                }
            }
        }
    }

    private void validateDates(QlSessionBo bo) {
        if (bo.getStartDate() != null && bo.getEndDate() != null && bo.getEndDate().before(bo.getStartDate())) {
            throw new CustomException("结束日期不能早于开始日期");
        }
        if (bo.getStartDate() != null && bo.getEndDate() != null
                && TimeUnit.MILLISECONDS.toDays(bo.getEndDate().getTime() - bo.getStartDate().getTime()) + 1 > 31) {
            throw new CustomException("单个期次最多31个活动日");
        }
        if (bo.getRegistrationOpenAt() != null && bo.getRegistrationCloseAt() != null
                && bo.getRegistrationCloseAt().before(bo.getRegistrationOpenAt())) {
            throw new CustomException("报名截止时间不能早于开放时间");
        }
    }

    private void syncSessionDays(String sessionId, Date startDate, Date endDate, Long operatorId, Date now) {
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(startDate);
        int dayNo = 1;
        while (!cursor.getTime().after(endDate)) {
            miniAppMapper.upsertSessionDay(UUID.randomUUID().toString(), sessionId, dayNo,
                    cursor.getTime(), operatorId, now);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
            dayNo++;
        }
        miniAppMapper.cancelExtraSessionDays(sessionId, dayNo - 1, operatorId, now);
    }
}
