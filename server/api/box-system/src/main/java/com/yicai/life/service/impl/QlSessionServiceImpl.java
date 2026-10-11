package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
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
    private final com.yicai.life.service.QlSessionSettings settings;

    @Override
    public TableDataInfo<QlSessionVo> queryPageList(QlSessionBo bo) {
        Page<QlSessionVo> page = baseMapper.selectPageList(PageUtils.buildPage(), bo);
        TableDataInfo<QlSessionVo> result=PageUtils.buildDataInfo(page);
        for(QlSessionVo session:result.getRows()) present(session);
        return result;
    }

    @Override
    public QlSessionVo queryById(String id) {
        QlSessionVo session=baseMapper.selectSessionVo(id);
        if(session!=null) present(session);
        return session;
    }

    @Override
    @Transactional
    public boolean insertByBo(QlSessionBo bo, Long operatorId) {
        applyNewDefaults(bo);
        settings.validateOptions(bo, null);
        normalizeBookingDays(bo);
        validateDates(bo);
        validateSettings(bo);
        QlSession entity = BeanUtil.toBean(bo, QlSession.class);
        settings.applyGuides(bo, entity, null);
        Date now = new Date();
        entity.setId(UUID.randomUUID().toString());
        entity.setCapacity(entity.getCapacity() == null ? 50 : entity.getCapacity());
        entity.setStandardPrice(entity.getStandardPrice() == null ? new BigDecimal("3800") : entity.getStandardPrice());
        entity.setReturningPrice(entity.getReturningPrice() == null ? new BigDecimal("2500") : entity.getReturningPrice());
        entity.setStatus(StrUtil.blankToDefault(entity.getStatus(), "draft"));
        entity.setRegistrationConfirmMode(StrUtil.blankToDefault(entity.getRegistrationConfirmMode(), "auto"));
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
        QlSession current = getById(bo.getId());
        if (current == null) throw new CustomException("期次不存在", 404);
        settings.validateOptions(bo, current);
        normalizeBookingDays(bo);
        validateDates(bo);
        validateSettings(bo);
        QlSession entity = BeanUtil.toBean(bo, QlSession.class);
        settings.applyGuides(bo, entity, current);
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

    private void present(QlSessionVo session) {
        session.setCoverUrl(com.yicai.common.utils.file.PublicMediaUpload.url(session.getCoverUrl()));
        java.util.List<Long> ids = com.yicai.life.service.QlSessionSettings.parseIds(session.getGuideUserIds());
        session.setGuideIds(ids == null ? null : ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()));
    }

    private void applyNewDefaults(QlSessionBo bo) {
        if (StrUtil.isBlank(bo.getName())) bo.setName("生机清体营");
        if (StrUtil.isBlank(bo.getPublicVenue())) bo.setPublicVenue("上海市安亭驿岛酒店");
        if (bo.getStartDate() != null) {
            Calendar day = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            if (bo.getRegistrationOpenAt() == null && !Boolean.TRUE.equals(bo.getRegistrationOpenManual())) {
                day.setTime(bo.getStartDate()); day.add(Calendar.DAY_OF_MONTH, -14); bo.setRegistrationOpenAt(day.getTime());
            }
            if (bo.getRegistrationCloseAt() == null && !Boolean.TRUE.equals(bo.getRegistrationCloseManual())) {
                day.setTime(bo.getStartDate()); day.add(Calendar.DAY_OF_MONTH, -1); bo.setRegistrationCloseAt(day.getTime());
            }
        }
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
        if (bo.getTheme() != null && bo.getTheme().length() > 200) throw new CustomException("本期主题最多200字");
        if (bo.getCoverUrl()!=null && bo.getCoverUrl().length()>500) throw new CustomException("请配置有效的本期封面");
        if (com.yicai.life.service.QlSessionCover.isAutomatic(bo.getCoverUrl())) {
            QlSession existing = StrUtil.isBlank(bo.getId()) ? null : getById(bo.getId());
            boolean sameCover = existing != null && StrUtil.isNotBlank(existing.getCoverUrl())
                    && (java.util.Objects.equals(bo.getCoverUrl(), existing.getCoverUrl())
                    || java.util.Objects.equals(bo.getCoverUrl(), com.yicai.common.utils.file.PublicMediaUpload.url(existing.getCoverUrl())));
            if (sameCover && java.util.Objects.equals(bo.getSessionNumber(), existing.getSessionNumber())) {
                bo.setCoverUrl(existing.getCoverUrl());
            } else {
                bo.setCoverUrl(com.yicai.life.service.QlSessionCover.ensure(bo.getSessionNumber()));
            }
        }
        if (bo.getStandardPrice() != null && (bo.getStandardPrice().signum() < 0 || bo.getStandardPrice().compareTo(new BigDecimal("99999999.99")) > 0)
                || bo.getReturningPrice() != null && (bo.getReturningPrice().signum() < 0 || bo.getReturningPrice().compareTo(new BigDecimal("99999999.99")) > 0)) {
            throw new CustomException("价格必须在0至99999999.99元之间");
        }
        if (StrUtil.isNotBlank(bo.getRegistrationConfirmMode()) && !java.util.Arrays.asList("manual", "auto").contains(bo.getRegistrationConfirmMode())) throw new CustomException("报名确认方式无效");

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
