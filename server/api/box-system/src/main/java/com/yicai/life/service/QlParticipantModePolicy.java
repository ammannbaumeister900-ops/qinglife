package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.QlRegistration;
import com.yicai.life.mapper.QlParticipantFinanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** One uncached, fail-closed switch and one immutable mode choice at order creation. */
@Service @RequiredArgsConstructor
public class QlParticipantModePolicy {
    private final QlParticipantFinanceMapper mapper;
    public boolean enabled() { List<String> flags=mapper.flags();return flags.size()==1&&"true".equals(flags.get(0)); }
    public void requireEnabled() { if(!enabled())throw new CustomException("逐人资金功能尚未开放",423); }
    public void requireParticipant(String mode) { if(!"participant".equals(mode))throw new CustomException("历史订单请使用原整单流程",409); }
    public void requireLegacyRegistration(String id) { rejectParticipant(mapper.registrationMode(id)); }
    public void requireLegacyBatch(String id) { rejectParticipant(mapper.batchMode(id)); }
    private void rejectParticipant(String mode) {
        if("participant".equals(mode)) { requireEnabled();throw new CustomException("逐人订单不能使用旧整单操作，请升级操作入口",409); }
    }
    public void initializeNewBatch(String id) { if(enabled())mapper.initializeBatch(id); }
    public String newSingleBatch(QlRegistration r) {
        if(!enabled())return null;
        String id=UUID.randomUUID().toString();mapper.singleBatch(id,QlOrderNumber.create(new Date()),r);return id;
    }
}
