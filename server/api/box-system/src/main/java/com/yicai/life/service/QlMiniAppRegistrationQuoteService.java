package com.yicai.life.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlMiniAppQuoteBo;
import com.yicai.life.domain.bo.QlMiniAppRegistrationBo;
import com.yicai.life.mapper.QlMiniAppMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Read-only preview: uses the registration identity boundary without creating participants. */
@Service
@RequiredArgsConstructor
public class QlMiniAppRegistrationQuoteService {
    private final QlMiniAppMapper mapper;
    private final QlSessionPricing pricing;
    private final QlSessionAdmissionPolicy admission;
    @Transactional(readOnly = true)
    public Map<String,Object> quote(String buyerId, QlMiniAppQuoteBo bo) {
        if (bo == null || StrUtil.isBlank(bo.getSessionId()) || bo.getParticipants() == null
                || bo.getParticipants().isEmpty() || bo.getParticipants().size() > 10)
            throw new CustomException("请选择1至10位参与者",400);
        Map<String,Object> session = mapper.selectSessionDetail(bo.getSessionId());
        if (session == null) throw new CustomException("期次不存在或未发布",404);
        admission.requireNewRegistrationOpen(session);
        BigDecimal standard = decimal(session.get("standardPrice")), returning = decimal(session.get("returningPrice"));
        List<Map<String,Object>> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>(), phones = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO; boolean selfUsed = false;
        for (QlMiniAppRegistrationBo.Participant p : bo.getParticipants()) {
            if (p == null || StrUtil.isBlank(p.getName())) throw new CustomException("请填写参与者称呼",400);
            String phone = StrUtil.blankToDefault(p.getPhone(),"").replaceAll("\\s+","");
            if (!phone.isEmpty() && (!phone.matches("^1\\d{10}$") || !phones.add(phone)))
                throw new CustomException("请检查参与者手机号，不能重复使用",400);
            String id = null;
            if (Boolean.TRUE.equals(p.getSelf())) {
                if (selfUsed) throw new CustomException("本人只能选择一次",400);
                selfUsed = true; id = buyerId;
                if (!phone.isEmpty() && mapper.selectPhoneCandidates(DigestUtil.sha256Hex(phone)).stream().anyMatch(candidate -> !candidate.equals(buyerId)))
                    throw new CustomException("手机号涉及已有档案，请联系工作人员核验",409);
            } else if (StrUtil.isNotBlank(p.getCustomerId())) {
                id = mapper.selectOwnedParticipant(p.getCustomerId(),buyerId);
                if (id == null) throw new CustomException("无权使用该参与者",403);
            } else if (!phone.isEmpty()) {
                List<String> candidates = mapper.selectPhoneCandidates(DigestUtil.sha256Hex(phone));
                if (candidates.size() == 1 && mapper.selectOwnedParticipant(candidates.get(0),buyerId) != null) id = candidates.get(0);
                else if (!candidates.isEmpty()) throw new CustomException("参与者档案需工作人员核对",409);
            }
            if (id != null && !ids.add(id)) throw new CustomException("不能重复选择同一参与者",400);
            Map<String,Object> row = new LinkedHashMap<>(pricing.quote(id,standard,returning));
            row.put("index",rows.size()); rows.add(row);
            total = total.add((BigDecimal)row.get("unitPrice"));
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("participants",rows); result.put("quotedAmount",total); return result;
    }
    private BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
}
