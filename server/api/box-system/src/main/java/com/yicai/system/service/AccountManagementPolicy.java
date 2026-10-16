package com.yicai.system.service;

import com.yicai.common.core.domain.entity.SysMenu;
import com.yicai.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Validates administration targets, in addition to endpoint permissions. */
@Service
@RequiredArgsConstructor
public class AccountManagementPolicy {
    private final JdbcTemplate db;
    private final AccountPagePermissionService pages;

    public void user(Long id, Long actor) {
        if (id == null || id <= 0 || id == 1L) throw new CustomException("不能操作超级管理员或无效账号", 400);
        if (db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND del_flag='0'", Integer.class, id) != 1)
            throw new CustomException("后台账号不存在", 400);
        if (!Long.valueOf(1).equals(actor)) {
            for (Long role : db.queryForList("SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, id)) role(role, actor, false);
        }
    }
    public void role(Long id, Long actor, boolean active) {
        if (id == null || id <= 0 || id == 1L) throw new CustomException("超级管理员角色不可分配或修改", 400);
        List<Map<String,Object>> rows = db.queryForList("SELECT role_key,status FROM sys_role WHERE role_id=? AND del_flag='0'", id);
        if (rows.isEmpty() || "admin".equals(rows.get(0).get("role_key")) || (active && !"0".equals(rows.get(0).get("status"))))
            throw new CustomException("角色不存在、已停用或为保留角色", 400);
        menus(db.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=?", Long.class, id).toArray(new Long[0]), actor);
    }
    public void roles(Long[] ids, Long actor) {
        if (ids != null) for (Long id : ids) role(id, actor, true);
    }
    public void menus(Long[] ids, Long actor) {
        if (ids == null) throw new CustomException("请选择角色菜单权限", 400);
        List<SysMenu> available = db.query("SELECT DISTINCT m.menu_id FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id=m.menu_id JOIN sys_user_role ur ON ur.role_id=rm.role_id JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=? AND r.status='0' AND r.del_flag='0' AND m.status='0'",
            (row, n) -> new SysMenu().setMenuId(row.getLong(1)), actor);
        Set<Long> allowed = pages.filterMenus(actor, available).stream().map(SysMenu::getMenuId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (id == null || db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE menu_id=?", Integer.class, id) != 1)
                throw new CustomException("菜单权限不存在", 400);
            if (!Long.valueOf(1).equals(actor) && !allowed.contains(id))
                throw new CustomException("不能管理或授予超出自己权限的角色", 403);
        }
    }
    public void members(Long roleId, Long[] users, Long actor, boolean active) {
        role(roleId, actor, active);
        if (users == null || users.length == 0) throw new CustomException("请选择后台账号", 400);
        for (Long id : users) user(id, actor);
    }
    public void roleKey(String key) {
        if (key == null || key.trim().isEmpty() || "admin".equalsIgnoreCase(key.trim()))
            throw new CustomException("admin 为超级管理员保留标识", 400);
    }
    public void status(String status) {
        if (!"0".equals(status) && !"1".equals(status)) throw new CustomException("账号或角色状态无效", 400);
    }
    public void password(String password) {
        if (password == null || password.length() < 5 || password.length() > 20)
            throw new CustomException("密码长度必须介于 5 和 20 之间", 400);
    }
}