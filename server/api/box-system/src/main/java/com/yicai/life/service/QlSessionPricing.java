package com.yicai.life.service;

import com.yicai.life.mapper.QlMiniAppMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class QlSessionPricing {
    private final QlMiniAppMapper mapper;

    public BigDecimal price(String participantId, BigDecimal newPrice, BigDecimal returningPrice) {
        // Participation belongs to the person, not to the buyer or the inviter.
        boolean returning = mapper.countCompletedSessions(participantId) > 0;
        return returning ? (returningPrice == null ? new BigDecimal("2500") : returningPrice)
                : (newPrice == null ? new BigDecimal("3800") : newPrice);
    }
}
