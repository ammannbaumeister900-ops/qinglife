package com.yicai.life.service;

import com.yicai.life.mapper.QlMiniAppMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QlSessionPricing {
    private final QlMiniAppMapper mapper;
    public BigDecimal price(String participantId, BigDecimal newPrice, BigDecimal returningPrice) {
        return (BigDecimal) quote(participantId, newPrice, returningPrice).get("unitPrice");
    }
    public Map<String,Object> quote(String participantId, BigDecimal newPrice, BigDecimal returningPrice) {
        // Same participant-specific history for preview and persisted registration prices.
        boolean returning = participantId != null && mapper.countCompletedSessions(participantId) > 0;
        BigDecimal amount = returning ? (returningPrice == null ? new BigDecimal("2500") : returningPrice)
                : (newPrice == null ? new BigDecimal("3800") : newPrice);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("friendType", returning ? "returning" : "new"); result.put("unitPrice",amount);
        return result;
    }
}
