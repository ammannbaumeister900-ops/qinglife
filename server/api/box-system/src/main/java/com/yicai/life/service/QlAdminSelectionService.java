package com.yicai.life.service;
import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
/** Minimal selectors keep a visible business page independent of other page routes. */
@Service @RequiredArgsConstructor
public class QlAdminSelectionService {
    private final JdbcTemplate db;
    private final QlPassService passes;
    public List<Map<String,Object>> customers(Long userId) {
        requireRolePermission(userId,"life:customer:list");
        return db.queryForList("SELECT id,nickname,real_name AS realName FROM ql_customer WHERE deleted_at IS NULL AND status='active' ORDER BY created_at DESC,id DESC LIMIT 1000");
    }
    public List<Map<String,Object>> sessions(Long userId) {
        requireRolePermission(userId,"life:session:list");
        return db.queryForList("SELECT id,session_number AS sessionNumber,name FROM ql_session ORDER BY start_date DESC,session_number DESC LIMIT 1000");
    }
    public List<Map<String,Object>> registrationPasses(String registrationId,Long userId) {
        requireRolePermission(userId,"life:pass:list");
        List<String> owners=db.queryForList("SELECT CASE WHEN r.batch_id IS NULL THEN r.customer_id ELSE b.submitted_by_customer_id END FROM ql_registration r LEFT JOIN ql_registration_batch b ON b.id=r.batch_id WHERE r.id=?",String.class,registrationId);
        if(owners.isEmpty()||owners.get(0)==null)throw new CustomException("报名记录不存在或归属缺失",404);
        List<Map<String,Object>> result=passes.list(owners.get(0));
        Set<String> keys=new HashSet<>(Arrays.asList("id","passType","balance","usable","status","validFrom","validUntil","createdAt"));
        for(Map<String,Object> row:result)row.keySet().retainAll(keys);return result;
    }
    private void requireRolePermission(Long userId,String permission) {
        if(SysUser.isAdmin(userId))return;
        Integer count=db.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id JOIN sys_user u ON u.user_id=ur.user_id WHERE ur.user_id=? AND u.status='0' AND u.del_flag='0' AND r.status='0' AND m.status='0' AND FIND_IN_SET(?,m.perms)>0",Integer.class,userId,permission);
        if(count==null||count==0)throw new CustomException("现有角色没有该选项数据的查看权限",403);
    }
}
