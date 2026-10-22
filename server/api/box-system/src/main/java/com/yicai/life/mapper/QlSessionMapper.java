package com.yicai.life.mapper;

import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;
import com.yicai.life.domain.QlSession;

public interface QlSessionMapper extends BaseMapperPlus<QlSession> {
    String COLUMNS = "s.id,s.session_number,s.name,s.theme,s.cover_url,s.intro,s.start_date,s.end_date,s.capacity,"
            + "s.standard_price,s.returning_price,s.venue,s.public_venue,s.province,s.city,s.registration_confirm_mode,"
            + "s.registration_open_at,s.registration_close_at,s.leader_name,s.guide_user_ids,s.cancel_policy,s.updated_at,"
            + "s.status AS configured_status,<![CDATA[" + com.yicai.life.service.QlSessionAdmissionPolicy.STATUS_SQL + "]]> AS status";

    @org.apache.ibatis.annotations.Select("<script>SELECT " + COLUMNS + " FROM ql_session s WHERE 1=1 "
            + "<if test='bo.sessionNumber != null'>AND s.session_number=#{bo.sessionNumber} </if>"
            + "<if test='bo.name != null and bo.name != &quot;&quot;'>AND s.name LIKE CONCAT('%',#{bo.name},'%') </if>"
            + "<if test='bo.status != null and bo.status != &quot;&quot;'>AND (<![CDATA[" + com.yicai.life.service.QlSessionAdmissionPolicy.STATUS_SQL + "]]>)=#{bo.status} </if>"
            + "ORDER BY s.session_number DESC</script>")
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.yicai.life.domain.vo.QlSessionVo> selectPageList(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.yicai.life.domain.vo.QlSessionVo> page,
            @org.apache.ibatis.annotations.Param("bo") com.yicai.life.domain.bo.QlSessionBo bo);

    @org.apache.ibatis.annotations.Select("<script>SELECT " + COLUMNS + " FROM ql_session s WHERE s.id=#{id}</script>")
    com.yicai.life.domain.vo.QlSessionVo selectSessionVo(@org.apache.ibatis.annotations.Param("id") String id);
}
