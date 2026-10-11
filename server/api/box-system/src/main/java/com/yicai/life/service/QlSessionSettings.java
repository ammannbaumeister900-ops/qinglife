package com.yicai.life.service;

import cn.hutool.json.JSONUtil;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.QlSession;
import com.yicai.life.domain.bo.QlSessionBo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Existing dictionaries and active staff IDs; historical free text is never inferred as an identity. */
@Service @RequiredArgsConstructor
public class QlSessionSettings {
    private final JdbcTemplate db;
    private static final List<String> DEFAULT_GUIDES = Arrays.asList("公主", "大海", "军军");

    public List<Map<String,Object>> guides() {
        return db.queryForList("SELECT CAST(user_id AS CHAR) AS id,nick_name AS name FROM sys_user WHERE status='0' AND del_flag='0' AND nick_name IN ('公主','大海','军军') ORDER BY user_id");
    }

    public List<Long> defaultGuides() {
        List<Map<String,Object>> options = guides();
        List<Long> ids = new ArrayList<>();
        for (String name : DEFAULT_GUIDES) {
            List<Long> matches = new ArrayList<>();
            for (Map<String,Object> option : options) if (name.equals(option.get("name"))) matches.add(Long.valueOf(String.valueOf(option.get("id"))));
            if (matches.size() != 1) throw new CustomException("默认导游“" + name + "”未唯一匹配现有人员，请核对人员ID", 400);
            ids.add(matches.get(0));
        }
        return ids;
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
        List<Long> ids = bo.getGuideIds();
        if (ids == null && current != null) {
            // No ID selection means preserve the old free-text attribution, including unknown names.
            entity.setLeaderName(current.getLeaderName());
            entity.setGuideUserIds(current.getGuideUserIds());
            return;
        }
        if (ids == null) ids = defaultGuides();
        if (ids.size() > 3 || new HashSet<>(ids).size() != ids.size()) throw new CustomException("导游选择无效", 400);
        if (current != null && ids.equals(parseIds(current.getGuideUserIds()))) {
            entity.setLeaderName(current.getLeaderName()); entity.setGuideUserIds(current.getGuideUserIds()); return;
        }
        List<Map<String,Object>> options = guides();
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            Map<String,Object> person = options.stream().filter(row -> Objects.equals(Long.valueOf(String.valueOf(row.get("id"))), id)).findFirst().orElseThrow(() -> new CustomException("导游人员不存在或已停用，请重新选择", 400));
            names.add(String.valueOf(person.get("name")));
        }
        entity.setGuideUserIds(JSONUtil.toJsonStr(ids));
        entity.setLeaderName(String.join("、", names));
    }

    public static List<Long> parseIds(String json) {
        return json == null ? null : JSONUtil.toList(json, Long.class);
    }
}
