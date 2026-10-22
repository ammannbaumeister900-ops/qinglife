package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.QlSession;
import com.yicai.life.domain.bo.QlSessionBo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Existing dictionaries and the fixed guide roster; historical free text is preserved, never reinterpreted. */
@Service @RequiredArgsConstructor
public class QlSessionSettings {
    private final JdbcTemplate db;
    /** The guides are a fixed team, not back-office accounts; the order here is the display order. */
    public static final List<String> GUIDES = Collections.unmodifiableList(Arrays.asList("公主", "大海", "军军"));

    public List<Map<String,Object>> guides() {
        List<Map<String,Object>> options = new ArrayList<>();
        for (String name : GUIDES) { Map<String,Object> option = new LinkedHashMap<>(); option.put("id", name); option.put("name", name); options.add(option); }
        return options;
    }

    public void validateOptions(QlSessionBo bo, QlSession current) {
        if (current == null || !Objects.equals(current.getName(), bo.getName())) validateOption("ql_session_name", bo.getName(), "期次名称");
        if (current == null || !Objects.equals(current.getPublicVenue(), bo.getPublicVenue())) validateOption("ql_session_venue", bo.getPublicVenue(), "公开地点");
    }

    private void validateOption(String type, String value, String label) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.dict_type=d.dict_type WHERE d.dict_type=? AND d.dict_value=? AND d.status='0' AND t.status='0'", Integer.class, type, value);
        if (count == null || count == 0) throw new CustomException("请选择字典中已启用的" + label, 400);
    }

    public void applyGuides(QlSessionBo bo, QlSession entity, QlSession current) {
        List<String> selected = bo.getGuideNames();
        if (selected == null && current != null) {
            // No selection means preserve the old attribution, including unknown historical names.
            entity.setLeaderName(current.getLeaderName());
            entity.setGuideUserIds(current.getGuideUserIds());
            return;
        }
        if (selected == null) selected = GUIDES;
        if (new HashSet<>(selected).size() != selected.size() || !GUIDES.containsAll(selected)) throw new CustomException("请从公主、大海、军军中选择导游", 400);
        List<String> ordered = new ArrayList<>();
        for (String name : GUIDES) if (selected.contains(name)) ordered.add(name);
        entity.setLeaderName(String.join("、", ordered));
        // Account IDs no longer identify guides; clear any stale link.
        entity.setGuideUserIds("[]");
    }
}
