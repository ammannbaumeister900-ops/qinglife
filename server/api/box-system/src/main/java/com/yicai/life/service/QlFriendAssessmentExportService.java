package com.yicai.life.service;

import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.file.ExportFileAccess;
import com.yicai.life.domain.bo.QlCustomerBo;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QlFriendAssessmentExportService {
    private final JdbcTemplate db;

    public AjaxResult export(QlCustomerBo bo) {
        Filter filter=filter(bo);
        List<Map<String,Object>> friends=db.queryForList("SELECT c.id,c.real_name AS name,c.nickname,c.birth_date AS birthDate,"+
                "(SELECT identifier_value FROM ql_customer_identifier i WHERE i.customer_id=c.id AND i.identifier_type='phone' AND i.valid_to IS NULL ORDER BY i.is_primary DESC,i.created_at DESC LIMIT 1) AS phone,"+
                "c.city,c.height_cm AS heightCm,COUNT(a.id) AS assessmentCount,MIN(a.submitted_at) AS firstAssessmentAt,MAX(a.submitted_at) AS latestAssessmentAt "+
                "FROM ql_customer c LEFT JOIN ql_friend_assessment a ON a.customer_id=c.id WHERE c.deleted_at IS NULL"+filter.customerSql+
                " GROUP BY c.id,c.real_name,c.nickname,c.birth_date,c.city,c.height_cm ORDER BY latestAssessmentAt DESC,c.updated_at DESC",filter.args.toArray());
        Filter assessmentFilter=assessmentFilter(bo);
        List<Map<String,Object>> assessments=db.queryForList("SELECT a.id,a.customer_id AS customerId,a.form_version AS formVersion,a.weight_kg AS weightKg,"+
                "a.clean_body_goals AS cleanBodyGoals,a.diet_preference AS dietPreference,a.water_intake_ml AS waterIntakeMl,TIME_FORMAT(a.wake_time,'%H:%i') AS wakeTime,"+
                "TIME_FORMAT(a.sleep_time,'%H:%i') AS sleepTime,a.bowel_status AS bowelStatus,a.energy_status AS energyStatus,a.exercise_status AS exerciseStatus,"+
                "a.emotional_status AS emotionalStatus,a.health_conditions AS healthConditions,a.other_health_condition AS otherHealthCondition,a.medications,a.pregnancy_status AS pregnancyStatus,"+
                "a.referral_source AS referralSource,a.retraining_reason AS retrainingReason,a.source,a.submitted_at AS submittedAt "+
                "FROM ql_friend_assessment a JOIN ql_customer c ON c.id=a.customer_id WHERE c.deleted_at IS NULL"+assessmentFilter.customerSql+
                " ORDER BY a.submitted_at DESC,a.id DESC",assessmentFilter.args.toArray());
        String filename=System.currentTimeMillis()+"_friend_assessments.xlsx";
        Path dir=Paths.get(RuoYiConfig.getDownloadPath()).toAbsolutePath().normalize(),file=dir.resolve(filename).normalize();
        if(!file.getParent().equals(dir))throw new CustomException("导出路径无效");
        try(SXSSFWorkbook wb=new SXSSFWorkbook(200)){
            CellStyle header=wb.createCellStyle();Font font=wb.createFont();font.setBold(true);header.setFont(font);header.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            write(wb.createSheet("轻友"),header,new String[]{"轻友ID","姓名","小名","出生日期","手机号","城市","身高(cm)","累计登记次数","首次登记时间","最近登记时间"},friends,
                    new String[]{"id","name","nickname","birthDate","phone","city","heightCm","assessmentCount","firstAssessmentAt","latestAssessmentAt"});
            write(wb.createSheet("登记记录"),header,new String[]{"登记ID","轻友ID","表单版本","体重(kg)","清体目的","饮食偏好","饮水量(ml)","起床时间","睡觉时间","排便","精力","运动","情绪","身体情况","其他身体情况","药物/保健品","孕期","了解途径/朋友姓名","复训原因","来源","提交时间"},assessments,
                    new String[]{"id","customerId","formVersion","weightKg","cleanBodyGoals","dietPreference","waterIntakeMl","wakeTime","sleepTime","bowelStatus","energyStatus","exerciseStatus","emotionalStatus","healthConditions","otherHealthCondition","medications","pregnancyStatus","referralSource","retrainingReason","source","submittedAt"});
            Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(file)){wb.write(out);}ExportFileAccess.record(file);wb.dispose();
            return AjaxResult.success(filename);
        }catch(Exception e){throw new CustomException("导出失败，请稍后重试");}
    }

    private void write(Sheet sheet,CellStyle header,String[] titles,List<Map<String,Object>> rows,String[] keys){
        Row top=sheet.createRow(0);for(int i=0;i<titles.length;i++){Cell c=top.createCell(i);c.setCellValue(titles[i]);c.setCellStyle(header);sheet.setColumnWidth(i,Math.min(60,Math.max(12,titles[i].length()*3))*256);}
        for(int i=0;i<rows.size();i++){Row row=sheet.createRow(i+1);for(int j=0;j<keys.length;j++){Object value=rows.get(i).get(keys[j]);row.createCell(j).setCellValue(display(value));}}
    }
    private String display(Object value){
        if(value==null)return "";String s=value instanceof Date?new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value):String.valueOf(value);
        if(s.startsWith("[")&&s.endsWith("]"))return s.substring(1,s.length()-1).replace("\"","").replace(",","；");return s;
    }
    private Filter filter(QlCustomerBo b){
        StringBuilder q=new StringBuilder();List<Object> a=new ArrayList<>();
        if(has(b.getNickname())){q.append(" AND (c.nickname LIKE ? OR c.real_name LIKE ? OR CONVERT(c.customer_no USING utf8mb4) LIKE ? OR EXISTS(SELECT 1 FROM ql_customer_identifier pi WHERE pi.customer_id=c.id AND pi.identifier_type='phone' AND pi.valid_to IS NULL AND pi.normalized_hash=SHA2(?,256)))");String k="%"+b.getNickname()+"%";a.add(k);a.add(k);a.add(k);a.add(b.getNickname().replaceAll("\\D",""));}
        if(has(b.getPhone())){q.append(" AND EXISTS(SELECT 1 FROM ql_customer_identifier pi WHERE pi.customer_id=c.id AND pi.identifier_type='phone' AND pi.valid_to IS NULL AND pi.normalized_hash=SHA2(?,256))");a.add(b.getPhone().replaceAll("\\D",""));}
        if(has(b.getCity())){q.append(" AND c.city=?");a.add(b.getCity());}
        if(has(b.getStatus())){q.append(" AND c.status=?");a.add(b.getStatus());}
        if(has(b.getAssessmentStart())||has(b.getAssessmentEnd())||has(b.getCleanBodyGoal())||has(b.getDietPreference())||has(b.getEnergyStatus())||has(b.getExerciseStatus())||b.getHasHealthCondition()!=null){
            q.append(" AND EXISTS(SELECT 1 FROM ql_friend_assessment f WHERE f.customer_id=c.id");
            if(has(b.getAssessmentStart())){q.append(" AND f.submitted_at>=?");a.add(b.getAssessmentStart()+" 00:00:00");}
            if(has(b.getAssessmentEnd())){q.append(" AND f.submitted_at<DATE_ADD(?,INTERVAL 1 DAY)");a.add(b.getAssessmentEnd());}
            if(has(b.getCleanBodyGoal())){q.append(" AND JSON_CONTAINS(f.clean_body_goals,JSON_QUOTE(?))");a.add(b.getCleanBodyGoal());}
            if(has(b.getDietPreference())){q.append(" AND f.diet_preference=?");a.add(b.getDietPreference());}
            if(has(b.getEnergyStatus())){q.append(" AND f.energy_status=?");a.add(b.getEnergyStatus());}
            if(has(b.getExerciseStatus())){q.append(" AND f.exercise_status=?");a.add(b.getExerciseStatus());}
            if(b.getHasHealthCondition()!=null)q.append(b.getHasHealthCondition()?" AND NOT (JSON_LENGTH(f.health_conditions)=1 AND JSON_CONTAINS(f.health_conditions,JSON_QUOTE('无')))":" AND JSON_LENGTH(f.health_conditions)=1 AND JSON_CONTAINS(f.health_conditions,JSON_QUOTE('无'))");
            q.append(")");
        }
        return new Filter(q.toString(),a);
    }
    private Filter assessmentFilter(QlCustomerBo b){
        StringBuilder q=new StringBuilder();List<Object> a=new ArrayList<>();
        if(has(b.getNickname())){q.append(" AND (c.nickname LIKE ? OR c.real_name LIKE ? OR CONVERT(c.customer_no USING utf8mb4) LIKE ? OR EXISTS(SELECT 1 FROM ql_customer_identifier pi WHERE pi.customer_id=c.id AND pi.identifier_type='phone' AND pi.valid_to IS NULL AND pi.normalized_hash=SHA2(?,256)))");String k="%"+b.getNickname()+"%";a.add(k);a.add(k);a.add(k);a.add(b.getNickname().replaceAll("\\D",""));}
        if(has(b.getPhone())){q.append(" AND EXISTS(SELECT 1 FROM ql_customer_identifier pi WHERE pi.customer_id=c.id AND pi.identifier_type='phone' AND pi.valid_to IS NULL AND pi.normalized_hash=SHA2(?,256))");a.add(b.getPhone().replaceAll("\\D",""));}
        if(has(b.getCity())){q.append(" AND c.city=?");a.add(b.getCity());}
        if(has(b.getStatus())){q.append(" AND c.status=?");a.add(b.getStatus());}
        if(has(b.getAssessmentStart())){q.append(" AND a.submitted_at>=?");a.add(b.getAssessmentStart()+" 00:00:00");}
        if(has(b.getAssessmentEnd())){q.append(" AND a.submitted_at<DATE_ADD(?,INTERVAL 1 DAY)");a.add(b.getAssessmentEnd());}
        if(has(b.getCleanBodyGoal())){q.append(" AND JSON_CONTAINS(a.clean_body_goals,JSON_QUOTE(?))");a.add(b.getCleanBodyGoal());}
        if(has(b.getDietPreference())){q.append(" AND a.diet_preference=?");a.add(b.getDietPreference());}
        if(has(b.getEnergyStatus())){q.append(" AND a.energy_status=?");a.add(b.getEnergyStatus());}
        if(has(b.getExerciseStatus())){q.append(" AND a.exercise_status=?");a.add(b.getExerciseStatus());}
        if(b.getHasHealthCondition()!=null)q.append(b.getHasHealthCondition()?" AND NOT (JSON_LENGTH(a.health_conditions)=1 AND JSON_CONTAINS(a.health_conditions,JSON_QUOTE('无')))":" AND JSON_LENGTH(a.health_conditions)=1 AND JSON_CONTAINS(a.health_conditions,JSON_QUOTE('无'))");
        return new Filter(q.toString(),a);
    }
    private boolean has(String s){return s!=null&&!s.trim().isEmpty();}
    private static final class Filter{final String customerSql;final List<Object> args;Filter(String sql,List<Object> args){this.customerSql=sql;this.args=args;}}
}
