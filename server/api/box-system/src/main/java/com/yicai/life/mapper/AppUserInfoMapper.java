package com.yicai.life.mapper;

import com.yicai.life.domain.AppUserInfo;
import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;

/**
 * 注册用户信息Mapper接口
 *
 * @author zhixia
 * @date 2022-02-23
 */
public interface AppUserInfoMapper extends BaseMapperPlus<AppUserInfo> {
    @org.apache.ibatis.annotations.Insert("INSERT INTO app_user_info(open_id,nick_name,status,insert_time,last_login_time) " +
        "VALUES(#{openId},'微信轻友',1,#{now},#{now}) ON DUPLICATE KEY UPDATE id=id")
    int ensureLoginUser(@org.apache.ibatis.annotations.Param("openId") String openId,
                        @org.apache.ibatis.annotations.Param("now") java.util.Date now);
    @org.apache.ibatis.annotations.Select("SELECT * FROM app_user_info WHERE open_id=#{openId} FOR UPDATE")
    AppUserInfo selectForLogin(@org.apache.ibatis.annotations.Param("openId") String openId);

    @org.apache.ibatis.annotations.Select("SELECT * FROM app_user_info WHERE id=#{id} FOR UPDATE")
    AppUserInfo selectForIdentity(@org.apache.ibatis.annotations.Param("id") Long id);

    @org.apache.ibatis.annotations.Update("UPDATE app_user_info SET last_login_time=#{now} WHERE id=#{id}")
    int updateLoginTime(@org.apache.ibatis.annotations.Param("id") Long id,
                        @org.apache.ibatis.annotations.Param("now") java.util.Date now);


}
