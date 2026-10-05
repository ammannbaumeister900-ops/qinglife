package com.yicai.system.service;

import com.yicai.common.core.domain.entity.SysMenu;
import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.exception.CustomException;
import com.yicai.system.domain.bo.AccountPagePermissionBo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** Account restrictions intersect role grants; no button or data permission is added. */
@Service @RequiredArgsConstructor
public class AccountPagePermissionService {
    private final JdbcTemplate db;

    public Map<String,Object> list(String keyword,int page,int size) {
        size=Math.max(1,Math.min(100,size));page=Math.max(1,page);
        String term="%"+(keyword==null?"":keyword.trim())+"%";
        String where=" WHERE u.del_flag='0' AND (u.user_name LIKE ? OR u.nick_name LIKE ?)";
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("total",db.queryForObject("SELECT COUNT(*) FROM sys_user u"+where,Long.class,term,term));
        result.put("rows",db.queryForList("SELECT u.user_id AS userId,u.user_name AS userName,u.nick_name AS nickName,u.status,"+
                "CASE WHEN p.user_id IS NULL THEN 1 ELSE 0 END AS inherit,"+
                "(SELECT GROUP_CONCAT(r.role_name ORDER BY r.role_sort SEPARATOR '、') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id AND r.status='0' WHERE ur.user_id=u.user_id) AS roleNames "+
                "FROM sys_user u LEFT JOIN ql_admin_page_policy p ON p.user_id=u.user_id"+where+" ORDER BY u.user_id LIMIT ? OFFSET ?",term,term,size,(page-1)*size));
        return result;
    }

    public Map<String,Object> detail(Long userId) {
        Map<String,Object> user=user(userId,false);
        List<Map<String,Object>> menus=menus();
        Set<Long> eligible=roleMenus(userId).stream().filter(m->"C".equals(m.get("menuType"))).map(this::id).collect(Collectors.toSet());
        boolean inherit=!configured(userId);
        Set<Long> selected=inherit?eligible:configuredPages(userId);
        Map<Long,Map<String,Object>> byId=new HashMap<>();for(Map<String,Object> m:menus)byId.put(id(m),m);
        List<Map<String,Object>> pages=new ArrayList<>();
        for(Map<String,Object> m:menus)if("C".equals(m.get("menuType"))){
            Map<String,Object> row=new LinkedHashMap<>(m);row.put("eligible",SysUser.isAdmin(userId)||eligible.contains(id(m)));
            Map<String,Object> parent=byId.get(((Number)m.get("parentId")).longValue());row.put("groupName",parent==null?"其他页面":parent.get("menuName"));pages.add(row);
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("user",user);result.put("inherit",inherit);result.put("pages",pages);
        selected.retainAll(eligible);result.put("pageIds",SysUser.isAdmin(userId)?pages.stream().map(this::id).collect(Collectors.toList()):selected);
        result.put("protectedAccount",SysUser.isAdmin(userId));return result;
    }

    @Transactional
    public void save(Long userId,AccountPagePermissionBo bo,Long operatorId) {
        user(userId,true);
        if(SysUser.isAdmin(userId))throw new CustomException("超级管理员保留全部页面权限",400);
        Set<Long> selected=new LinkedHashSet<>(bo.getPageIds());
        Set<Long> eligible=roleMenus(userId).stream().filter(m->"C".equals(m.get("menuType"))).map(this::id).collect(Collectors.toSet());
        if(!Boolean.TRUE.equals(bo.getInherit())&&!eligible.containsAll(selected))throw new CustomException("所选页面超出该账号已有角色权限，请先配置角色",400);
        if(Objects.equals(userId,operatorId)&&!Boolean.TRUE.equals(bo.getInherit())){
            Long management=db.queryForObject("SELECT menu_id FROM sys_menu WHERE component='system/accountPermission/index' AND status='0'",Long.class);
            if(!selected.contains(management))throw new CustomException("不能取消自己当前使用的账号权限页面",400);
        }
        Set<Long> previous=configuredPages(userId);boolean previousInherit=!configured(userId);
        db.update("DELETE FROM ql_admin_page_grant WHERE user_id=?",userId);
        if(Boolean.TRUE.equals(bo.getInherit()))db.update("DELETE FROM ql_admin_page_policy WHERE user_id=?",userId);
        else {
            db.update("INSERT INTO ql_admin_page_policy(user_id,updated_by,updated_at) VALUES(?,?,NOW(3)) ON DUPLICATE KEY UPDATE updated_by=VALUES(updated_by),updated_at=VALUES(updated_at)",userId,operatorId);
            for(Long pageId:selected)db.update("INSERT INTO ql_admin_page_grant(user_id,menu_id) VALUES(?,?)",userId,pageId);
        }
        db.update("INSERT INTO ql_admin_page_permission_log(id,user_id,previous_inherit,previous_page_ids,inherit_roles,page_ids,operator_id,created_at) VALUES(?,?,?,?,?,?,?,NOW(3))",
                UUID.randomUUID().toString(),userId,previousInherit,join(previous),bo.getInherit(),join(Boolean.TRUE.equals(bo.getInherit())?Collections.emptySet():selected),operatorId);
    }

    public Set<String> filterPermissions(Long userId,Set<String> permissions) {
        if(SysUser.isAdmin(userId)||!configured(userId))return permissions;
        Set<String> allowed=effectivePermissions(userId);
        return permissions.stream().filter(allowed::contains).collect(Collectors.toSet());
    }

    /** Checked on every request so an already-issued token cannot bypass page revocation. */
    public boolean allowsPermission(Long userId,String permission) {
        return SysUser.isAdmin(userId)||!configured(userId)||effectivePermissions(userId).contains(permission.trim());
    }

    public List<SysMenu> filterMenus(Long userId,List<SysMenu> input) {
        if(SysUser.isAdmin(userId)||!configured(userId))return input;
        Set<Long> allowed=effectiveMenuIds(userId,roleMenus(userId));
        return input.stream().filter(m->allowed.contains(m.getMenuId())).collect(Collectors.toList());
    }

    private Set<String> effectivePermissions(Long userId) {
        List<Map<String,Object>> role=roleMenus(userId);Set<Long> allowed=effectiveMenuIds(userId,role);Set<String> result=new HashSet<>();
        for(Map<String,Object> m:role)if(allowed.contains(id(m))){String perms=Objects.toString(m.get("perms"),"");for(String p:perms.split(","))if(!p.trim().isEmpty()&&!"*:*:*".equals(p.trim()))result.add(p.trim());}
        return result;
    }

    private Set<Long> effectiveMenuIds(Long userId,List<Map<String,Object>> role) {
        Set<Long> selected=configuredPages(userId),allowed=new HashSet<>();
        Map<Long,Map<String,Object>> all=new HashMap<>();for(Map<String,Object> m:menus())all.put(id(m),m);
        for(Map<String,Object> m:role)if("C".equals(m.get("menuType"))&&selected.contains(id(m))){
            allowed.add(id(m));Long parent=((Number)m.get("parentId")).longValue();Set<Long> visited=new HashSet<>();
            while(parent!=0&&visited.add(parent)&&all.containsKey(parent)){Map<String,Object> ancestor=all.get(parent);if(!"M".equals(ancestor.get("menuType")))break;allowed.add(parent);parent=((Number)ancestor.get("parentId")).longValue();}
        }
        // Functions inherit exactly the selected parent page, including nested function nodes.
        boolean changed;do{changed=false;for(Map<String,Object> m:role)if("F".equals(m.get("menuType"))&&allowed.contains(((Number)m.get("parentId")).longValue()))changed|=allowed.add(id(m));}while(changed);
        return allowed;
    }

    private List<Map<String,Object>> menus(){return db.queryForList("SELECT menu_id AS menuId,parent_id AS parentId,menu_name AS menuName,menu_type AS menuType,perms,component FROM sys_menu WHERE status='0' ORDER BY parent_id,order_num,menu_id");}
    private List<Map<String,Object>> roleMenus(Long userId){return db.queryForList("SELECT DISTINCT m.menu_id AS menuId,m.parent_id AS parentId,m.menu_type AS menuType,m.perms FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id=m.menu_id JOIN sys_user_role ur ON ur.role_id=rm.role_id JOIN sys_role r ON r.role_id=ur.role_id JOIN sys_user u ON u.user_id=ur.user_id WHERE ur.user_id=? AND m.status='0' AND r.status='0' AND u.status='0' AND u.del_flag='0'",userId);}
    private boolean configured(Long userId){return db.queryForObject("SELECT COUNT(*) FROM ql_admin_page_policy WHERE user_id=?",Integer.class,userId)>0;}
    private Set<Long> configuredPages(Long userId){return new HashSet<>(db.queryForList("SELECT menu_id FROM ql_admin_page_grant WHERE user_id=?",Long.class,userId));}
    private Map<String,Object> user(Long userId,boolean lock){List<Map<String,Object>> users=db.queryForList("SELECT user_id AS userId,user_name AS userName,nick_name AS nickName,status FROM sys_user WHERE user_id=? AND del_flag='0'"+(lock?" FOR UPDATE":""),userId);if(users.isEmpty())throw new CustomException("后台账号不存在",404);return users.get(0);}
    private Long id(Map<String,Object> row){return ((Number)row.get("menuId")).longValue();}
    private String join(Set<Long> ids){return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));}
}
