package com.yicai.life.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.yicai.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import com.yicai.life.mapper.QlExperienceOriginalMapper;
import org.springframework.stereotype.Service;
import java.util.*;

/** Owner-scoped after original; callers own the transaction and eligibility policy. */
@Service @RequiredArgsConstructor
public class QlExperienceOriginalService {
 private final QlExperienceOriginalMapper mapper;
 public void lockRegistration(String customer,String registration) {
  if(mapper.lockRegistration(customer,registration)==null)
   throw new CustomException("只能保存本人参加期次的感受",403);
 }
 public Map<String,Object> read(String customer,String registration,boolean lock) {
  return lock?mapper.readForUpdate(customer,registration):mapper.read(customer,registration);
 }
 public String version(Map<String,Object> row) {
  if(row==null)return "absent";
  StringBuilder value=new StringBuilder();
  for(String key:Arrays.asList("id","note","energy","relaxation","recordStamp")) {
   Object item=row.get(key);String s=Objects.toString(item,"");value.append(item==null?-1:s.length()).append(':').append(s);
  }
  return DigestUtil.sha256Hex(value.toString());
 }
 public void save(String customer,String registration,String session,String note,Date now) {
  // Existing ID and ratings survive a body-only edit. No legacy reflection draft is touched.
  mapper.save(UUID.randomUUID().toString(),customer,registration,session,note,now);
 }
 public static void validateNewNote(String note) {
  if(note!=null&&note.codePointCount(0,note.length())>150)throw new CustomException("新保存的感受最多150字；历史原文不会截断",400);
 }
}
