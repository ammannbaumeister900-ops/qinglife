package com.yicai.life.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
public interface QlOfflineReceiptImageMapper {
    @Insert("INSERT INTO ql_offline_receipt_image(id,transaction_id,position,image_mime,image_sha256,image_data) VALUES(#{id},#{receipt},#{position},#{mime},#{sha},#{bytes})")
    int insert(@Param("id") String id,@Param("receipt") String receipt,@Param("position") int position,@Param("mime") String mime,@Param("sha") String sha,@Param("bytes") byte[] bytes);
    @Select("SELECT id,image_mime AS mime,image_sha256 AS sha FROM ql_offline_receipt_image WHERE transaction_id=#{receipt} ORDER BY position")
    List<Map<String,Object>> list(@Param("receipt") String receipt);
    @Select("SELECT i.image_mime AS mime,i.image_data AS data FROM ql_offline_receipt_image i JOIN ql_transaction t ON t.id=i.transaction_id JOIN ql_registration r ON r.id=#{registration} WHERE i.id=#{image} AND (t.registration_id=r.id OR (r.batch_id IS NOT NULL AND t.registration_batch_id=r.batch_id))")
    Map<String,Object> image(@Param("registration") String registration,@Param("image") String image);
}
