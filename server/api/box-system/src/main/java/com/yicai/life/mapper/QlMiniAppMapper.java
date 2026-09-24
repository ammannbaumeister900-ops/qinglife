package com.yicai.life.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface QlMiniAppMapper {

    @Select("SELECT customer_id FROM ql_customer_identifier WHERE legacy_app_user_id = #{legacyUserId} " +
            "AND verification_status <> 'conflict' ORDER BY is_primary DESC, created_at ASC LIMIT 1")
    String selectCustomerIdByLegacyUserId(@Param("legacyUserId") Long legacyUserId);

    @Insert("INSERT INTO ql_customer(id, customer_no, nickname, gender, first_source, data_source, " +
            "data_confidence, status, revision, created_at, updated_at) VALUES(#{id}, #{customerNo}, " +
            "#{nickname}, #{gender}, 'wechat', 'legacy_app', 'verified', 'active', 0, #{now}, #{now})")
    int insertCustomer(@Param("id") String id, @Param("customerNo") String customerNo,
                       @Param("nickname") String nickname, @Param("gender") String gender,
                       @Param("now") Date now);

    @Insert("INSERT INTO ql_customer(id, customer_no, nickname, real_name, gender, first_source, data_source, " +
            "data_confidence, status, revision, created_at, updated_at) VALUES(#{id}, #{customerNo}, #{name}, " +
            "#{name}, 'unknown', 'mini_program_registration', 'mini_program', 'unknown', 'active', 0, #{now}, #{now})")
    int insertParticipantCustomer(@Param("id") String id, @Param("customerNo") String customerNo,
                                  @Param("name") String name, @Param("now") Date now);

    @Insert("INSERT INTO ql_customer_identifier(id, customer_id, identifier_type, identifier_value, normalized_hash, " +
            "display_hint, app_scope, is_primary, verification_status, valid_from, legacy_app_user_id, created_at, updated_at) " +
            "VALUES(#{id}, #{customerId}, #{type}, #{value}, #{hash}, #{hint}, #{appScope}, #{primary}, #{status}, " +
            "#{now}, #{legacyUserId}, #{now}, #{now})")
    int insertIdentifier(@Param("id") String id, @Param("customerId") String customerId,
                         @Param("type") String type, @Param("value") String value,
                         @Param("hash") String hash, @Param("hint") String hint,
                         @Param("appScope") String appScope, @Param("primary") boolean primary,
                         @Param("status") String status, @Param("legacyUserId") Long legacyUserId,
                         @Param("now") Date now);

    @Select("SELECT COALESCE(c.real_name,c.nickname) AS name, (c.birth_date IS NOT NULL AND TIMESTAMPDIFF(YEAR,c.birth_date,CURRENT_DATE())<18) AS minor, " +
            "(SELECT i.identifier_value FROM ql_customer_identifier i WHERE i.customer_id=c.id AND i.identifier_type='phone' AND i.valid_to IS NULL ORDER BY i.is_primary DESC,i.created_at LIMIT 1) AS phone FROM ql_customer c WHERE c.id=#{id}")
    Map<String,Object> selectCustomerProfile(@Param("id") String id);

    @Select("SELECT id FROM ql_customer WHERE id=#{id} FOR UPDATE")
    String lockCustomer(@Param("id") String id);

    @Select("SELECT s.id, s.session_number AS sessionNumber, s.name, s.theme, s.cover_url AS coverUrl, s.intro, s.start_date AS startDate, " +
            "s.end_date AS endDate, s.capacity, s.standard_price AS standardPrice, s.returning_price AS returningPrice, s.public_venue AS venue, " +
            "s.city, s.status, s.registration_confirm_mode AS confirmMode, s.registration_open_at AS registrationOpenAt, " +
            "s.registration_close_at AS registrationCloseAt, s.leader_name AS leaderName, s.cancel_policy AS cancelPolicy, " +
            "(SELECT COUNT(*) FROM ql_registration r WHERE r.session_id=s.id AND r.registration_status IN ('pending','confirmed')) AS registeredCount " +
            "FROM ql_session s WHERE s.status IN ('open','in_progress') ORDER BY s.start_date ASC")
    List<Map<String, Object>> selectPublicSessions();

    @Select("SELECT id, day_no AS dayNo, activity_date AS activityDate, start_time AS startTime, " +
            "end_time AS endTime, theme, status FROM ql_session_day WHERE session_id=#{sessionId} " +
            "AND status<>'cancelled' ORDER BY day_no")
    List<Map<String, Object>> selectSessionDays(@Param("sessionId") String sessionId);

    @Select("<script>SELECT id, session_id AS sessionId, day_no AS dayNo, activity_date AS activityDate, " +
            "start_time AS startTime, end_time AS endTime, theme, status FROM ql_session_day " +
            "WHERE status&lt;&gt;'cancelled' AND session_id IN " +
            "<foreach collection='sessionIds' item='sessionId' open='(' separator=',' close=')'>#{sessionId}</foreach> " +
            "ORDER BY session_id, day_no</script>")
    List<Map<String, Object>> selectSessionDaysBySessionIds(@Param("sessionIds") List<String> sessionIds);

    @Insert("INSERT INTO ql_session_day(id, session_id, day_no, activity_date, status, created_by, created_at, updated_by, updated_at) " +
            "VALUES(#{id}, #{sessionId}, #{dayNo}, #{activityDate}, 'scheduled', #{operatorId}, #{now}, #{operatorId}, #{now}) " +
            "ON DUPLICATE KEY UPDATE activity_date=VALUES(activity_date), updated_by=VALUES(updated_by), updated_at=VALUES(updated_at), " +
            "status=IF(status='cancelled','scheduled',status)")
    int upsertSessionDay(@Param("id") String id, @Param("sessionId") String sessionId,
                         @Param("dayNo") int dayNo, @Param("activityDate") Date activityDate,
                         @Param("operatorId") Long operatorId, @Param("now") Date now);

    @Update("UPDATE ql_session_day SET status='cancelled', updated_by=#{operatorId}, updated_at=#{now} " +
            "WHERE session_id=#{sessionId} AND day_no>#{dayCount} AND status<>'cancelled'")
    int cancelExtraSessionDays(@Param("sessionId") String sessionId, @Param("dayCount") int dayCount,
                               @Param("operatorId") Long operatorId, @Param("now") Date now);

    @Select("SELECT id, start_date AS startDate, end_date AS endDate, session_number AS sessionNumber, name, theme, cover_url AS coverUrl, standard_price AS standardPrice, returning_price AS returningPrice, " +
            "registration_confirm_mode AS confirmMode, registration_open_at AS registrationOpenAt, " +
            "registration_close_at AS registrationCloseAt, status, capacity, " +
            "(SELECT COUNT(*) FROM ql_registration r WHERE r.session_id=ql_session.id AND r.registration_status IN ('pending','confirmed')) AS registeredCount " +
            "FROM ql_session WHERE id=#{id} FOR UPDATE")
    Map<String, Object> selectSessionForRegistration(@Param("id") String id);

    @Select("SELECT id, order_no AS orderNo, payment_status AS paymentStatus, participant_count AS participantCount, quoted_amount AS quotedAmount, final_amount AS finalAmount, settlement_status AS settlementStatus, settlement_type AS settlementType, pass_units AS passUnits, payable_amount AS payableAmount, request_fingerprint AS requestFingerprint " +
            "FROM ql_registration_batch WHERE source='mini_program' AND client_request_id=#{clientRequestId} LIMIT 1")
    Map<String, Object> selectBatchByClientRequestId(@Param("clientRequestId") String clientRequestId);

    @Select("SELECT id FROM ql_registration WHERE session_id=#{sessionId} AND registration_status IN ('pending','confirmed') FOR UPDATE")
    List<String> selectOccupiedRegistrations(@Param("sessionId") String sessionId);

    @Insert("INSERT INTO ql_registration_batch(id, order_no, session_id, submitted_by_customer_id, contact_name, contact_phone, source, " +
            "client_request_id, participant_count, quoted_amount, payable_amount, payment_status, settlement_status, submitted_at, created_at) " +
            "VALUES(#{id}, #{orderNo}, #{sessionId}, #{buyerId}, #{contactName}, #{contactPhone}, 'mini_program', #{clientRequestId}, #{count}, " +
            "#{quotedAmount}, 0, 'unpaid', 'pending', #{now}, #{now})")
    int insertRegistrationBatch(@Param("id") String id, @Param("orderNo") String orderNo,
                                @Param("sessionId") String sessionId, @Param("buyerId") String buyerId,
                                 @Param("contactName") String contactName, @Param("contactPhone") String contactPhone,
                                 @Param("clientRequestId") String clientRequestId, @Param("count") int count,
                                 @Param("quotedAmount") BigDecimal quotedAmount, @Param("now") Date now);

    @Insert("INSERT INTO ql_registration(id, batch_id, customer_id, session_id, registration_status, payment_status, " +
            "registration_source, motivation, relation_snapshot, is_minor_snapshot, registered_at, revision, created_at, updated_at) " +
            "VALUES(#{id}, #{batchId}, #{customerId}, #{sessionId}, #{status}, 'unpaid', 'mini_program', #{motivation}, " +
            "#{relation}, #{minor}, #{now}, 0, #{now}, #{now})")
    int insertRegistration(@Param("id") String id, @Param("batchId") String batchId,
                           @Param("customerId") String customerId, @Param("sessionId") String sessionId,
                           @Param("status") String status, @Param("motivation") String motivation,
                           @Param("relation") String relation, @Param("minor") boolean minor,
                           @Param("now") Date now);

    @Insert("INSERT INTO ql_registration_status_log(id, registration_id, status_type, from_status, to_status, " +
            "change_reason, actor_customer_id, changed_at) VALUES(#{id}, #{registrationId}, 'registration', NULL, " +
            "#{status}, #{reason}, #{actorCustomerId}, #{now})")
    int insertRegistrationStatusLog(@Param("id") String id, @Param("registrationId") String registrationId,
                                    @Param("status") String status, @Param("reason") String reason,
                                    @Param("actorCustomerId") String actorCustomerId, @Param("now") Date now);

    @Insert("INSERT INTO ql_participation_day(id, registration_id, session_day_id, customer_id, attendance_status, " +
            "revision, created_at, updated_at) VALUES(#{id}, #{registrationId}, #{sessionDayId}, #{customerId}, " +
            "'not_arrived', 0, #{now}, #{now})")
    int insertParticipationDay(@Param("id") String id, @Param("registrationId") String registrationId,
                               @Param("sessionDayId") String sessionDayId, @Param("customerId") String customerId,
                               @Param("now") Date now);

    @Insert("INSERT INTO ql_consent_record(id, customer_id, consent_type, status, policy_version, granted_at, source, created_at) " +
            "VALUES(#{id}, #{customerId}, 'service_required', 'granted', #{policyVersion}, #{now}, 'mini_program', #{now})")
    int insertServiceConsent(@Param("id") String id, @Param("customerId") String customerId,
                             @Param("policyVersion") String policyVersion, @Param("now") Date now);

    @Select("SELECT r.id AS registrationId, r.registration_status AS registrationStatus, r.payment_status AS paymentStatus, " +
            "r.registered_at AS registeredAt, b.id AS batchId, b.order_no AS orderNo, b.contact_name AS contactName, b.contact_phone AS contactPhone, b.payment_status AS orderPaymentStatus, b.quoted_amount AS quotedAmount, COALESCE(b.final_amount,r.final_amount) AS finalAmount, COALESCE(b.settlement_status,r.settlement_status) AS settlementStatus, COALESCE(b.settlement_type,r.settlement_type) AS settlementType, COALESCE(b.pass_units,r.pass_units) AS passUnits, COALESCE(b.pass_account_id,r.pass_account_id) AS passAccountId, b.payable_amount AS payableAmount, r.unit_price AS unitPrice, " +
            "r.customer_id AS customerId, c.nickname AS participantName, (r.customer_id=#{customerId}) AS isSelf, r.is_minor_snapshot AS minor, r.relation_snapshot AS relation, s.status AS sessionStatus, s.id AS sessionId, s.session_number AS sessionNumber, s.name AS sessionName, s.start_date AS startDate, s.end_date AS endDate, " +
            "EXISTS(SELECT 1 FROM ql_participation_day pd WHERE pd.registration_id=r.id AND pd.attendance_status IN ('checked_in','late','left_early')) OR EXISTS(SELECT 1 FROM ql_participation p WHERE p.customer_id=r.customer_id AND p.session_id=r.session_id AND p.attendance_status IN ('checked_in','late','left_early','completed') AND NOT EXISTS(SELECT 1 FROM ql_participation_day existing_day JOIN ql_session_day existing_session_day ON existing_session_day.id=existing_day.session_day_id WHERE existing_day.customer_id=r.customer_id AND existing_session_day.session_id=r.session_id)) AS participated " +
            "FROM ql_registration r JOIN ql_customer c ON c.id=r.customer_id JOIN ql_session s ON s.id=r.session_id LEFT JOIN ql_registration_batch b ON b.id=r.batch_id " +
            "WHERE r.customer_id=#{customerId} OR b.submitted_by_customer_id=#{customerId} ORDER BY s.start_date DESC")
    List<Map<String, Object>> selectCustomerRegistrations(@Param("customerId") String customerId);

    @Select("SELECT a.id,a.pass_type AS passType,a.status,DATE_FORMAT(a.valid_until,'%Y-%m-%d') AS validUntil," +
            "COALESCE(SUM(l.quantity_delta),0) AS balance,CASE WHEN a.status='active' AND (a.valid_from IS NULL OR a.valid_from<=CURRENT_DATE()) AND (a.valid_until IS NULL OR a.valid_until>=CURRENT_DATE()) THEN 1 ELSE 0 END AS usable " +
            "FROM ql_pass_account a LEFT JOIN ql_pass_ledger l ON l.pass_account_id=a.id WHERE a.customer_id=#{customerId} " +
            "GROUP BY a.id,a.pass_type,a.status,a.valid_from,a.valid_until ORDER BY a.created_at DESC")
    List<Map<String,Object>> selectCustomerPassAccounts(@Param("customerId") String customerId);

    @Select("SELECT l.id,l.pass_account_id AS passAccountId,a.pass_type AS passType,l.registration_id AS registrationId," +
            "l.registration_batch_id AS registrationBatchId,l.entry_type AS entryType,l.quantity_delta AS quantityDelta," +
            "l.balance_after AS balanceAfter,l.reason,DATE_FORMAT(l.occurred_at,'%Y-%m-%d %H:%i:%s') AS occurredAt," +
            "s.session_number AS sessionNumber,s.name AS sessionName FROM ql_pass_ledger l JOIN ql_pass_account a ON a.id=l.pass_account_id " +
            "LEFT JOIN ql_session s ON s.id=l.session_id WHERE a.customer_id=#{customerId} ORDER BY l.occurred_at DESC,l.created_at DESC LIMIT 100")
    List<Map<String,Object>> selectCustomerPassLedger(@Param("customerId") String customerId);

    @Select("SELECT p.id, p.registration_id AS registrationId, p.session_day_id AS sessionDayId, d.day_no AS dayNo, " +
            "d.activity_date AS activityDate, d.theme, p.attendance_status AS attendanceStatus, p.checked_in_at AS checkedInAt " +
            "FROM ql_participation_day p JOIN ql_session_day d ON d.id=p.session_day_id " +
            "WHERE p.customer_id=#{customerId} ORDER BY d.activity_date DESC, d.day_no")
    List<Map<String, Object>> selectCustomerAttendance(@Param("customerId") String customerId);

    @Select("SELECT COUNT(*) FROM ql_registration r JOIN ql_session s ON s.id=r.session_id WHERE r.customer_id=#{customerId} AND r.session_id=#{sessionId} AND r.registration_status='confirmed' AND (EXISTS(SELECT 1 FROM ql_participation_day pd WHERE pd.registration_id=r.id AND pd.attendance_status IN ('checked_in','late','left_early')) OR EXISTS(SELECT 1 FROM ql_participation p WHERE p.customer_id=r.customer_id AND p.session_id=r.session_id AND p.attendance_status IN ('checked_in','late','left_early','completed') AND NOT EXISTS(SELECT 1 FROM ql_participation_day existing_day JOIN ql_session_day existing_session_day ON existing_session_day.id=existing_day.session_day_id WHERE existing_day.customer_id=r.customer_id AND existing_session_day.session_id=r.session_id)))")
    int countAttendedSession(@Param("customerId") String customerId, @Param("sessionId") String sessionId);

    @Update("UPDATE ql_participation_day p JOIN ql_registration r ON r.id=p.registration_id " +
            "SET p.attendance_status='checked_in', p.check_in_source='mini_program', p.checked_in_at=#{now}, " +
            "p.checked_in_by=NULL, p.revision=p.revision+1, p.updated_at=#{now} " +
            "WHERE p.registration_id=#{registrationId} AND p.session_day_id=#{sessionDayId} " +
            "AND p.customer_id=#{customerId} AND r.customer_id=#{customerId} AND r.registration_status='confirmed' " +
            "AND EXISTS(SELECT 1 FROM ql_session_day d WHERE d.id=p.session_day_id AND d.activity_date=CURRENT_DATE()) " +
            "AND p.attendance_status='not_arrived'")
    int checkIn(@Param("registrationId") String registrationId, @Param("sessionDayId") String sessionDayId,
                @Param("customerId") String customerId, @Param("now") Date now);

    @Insert("INSERT INTO ql_daily_record(id, customer_id, session_id, record_date, record_stage, plan_day, choice_value, " +
            "note, visibility_scope, revision, created_at, updated_at) VALUES(#{id}, #{customerId}, #{sessionId}, #{recordDate}, " +
            "#{stage}, #{planDay}, #{choice}, #{note}, 'private', 0, #{now}, #{now}) ON DUPLICATE KEY UPDATE " +
            "choice_value=VALUES(choice_value), note=VALUES(note), session_id=VALUES(session_id), revision=revision+1, updated_at=VALUES(updated_at)")
    int upsertDailyRecord(@Param("id") String id, @Param("customerId") String customerId,
                          @Param("sessionId") String sessionId, @Param("recordDate") Date recordDate,
                          @Param("stage") String stage, @Param("planDay") Integer planDay,
                          @Param("choice") String choice, @Param("note") String note, @Param("now") Date now);

    @Select("SELECT id, session_id AS sessionId, record_date AS recordDate, record_stage AS recordStage, plan_day AS planDay, " +
            "choice_value AS choiceValue, note, updated_at AS updatedAt FROM ql_daily_record " +
            "WHERE customer_id=#{customerId} ORDER BY record_date DESC LIMIT 100")
    List<Map<String, Object>> selectDailyRecords(@Param("customerId") String customerId);

    @Insert("INSERT INTO ql_habit_plan(id, customer_id, session_id, plan_length, current_day, status, started_at, created_at, updated_at) " +
            "VALUES(#{id}, #{customerId}, #{sessionId}, #{length}, 1, 'active', #{startedAt}, #{now}, #{now})")
    int insertHabitPlan(@Param("id") String id, @Param("customerId") String customerId,
                        @Param("sessionId") String sessionId, @Param("length") int length,
                        @Param("startedAt") Date startedAt, @Param("now") Date now);

    @Insert("INSERT INTO ql_habit_day_record(id, habit_plan_id, plan_day, status, created_at) " +
            "VALUES(#{id}, #{planId}, #{day}, 'pending', #{now})")
    int insertHabitDay(@Param("id") String id, @Param("planId") String planId,
                       @Param("day") int day, @Param("now") Date now);

    @Select("SELECT id, session_id AS sessionId, plan_length AS planLength, current_day AS currentDay, status, " +
            "started_at AS startedAt, paused_at AS pausedAt, completed_at AS completedAt FROM ql_habit_plan " +
            "WHERE customer_id=#{customerId} ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE")
    Map<String, Object> selectLatestHabit(@Param("customerId") String customerId);

    @Insert("INSERT INTO ql_post_report(id, legacy_publish_id, reporter_customer_id, reason_code, reason_note, status, created_at) " +
            "VALUES(#{id}, #{publishId}, #{customerId}, #{reasonCode}, #{reasonNote}, 'pending', #{now})")
    int insertPostReport(@Param("id") String id, @Param("publishId") Long publishId,
                         @Param("customerId") String customerId, @Param("reasonCode") String reasonCode,
                         @Param("reasonNote") String reasonNote, @Param("now") Date now);

    @Insert("INSERT INTO ql_subscription_preference(id, customer_id, template_key, status, granted_at, revoked_at, source, created_at, updated_at) " +
            "VALUES(#{id}, #{customerId}, #{templateKey}, #{status}, #{grantedAt}, #{revokedAt}, 'mini_program', #{now}, #{now}) " +
            "ON DUPLICATE KEY UPDATE status=VALUES(status), granted_at=VALUES(granted_at), revoked_at=VALUES(revoked_at), updated_at=VALUES(updated_at)")
    int upsertSubscription(@Param("id") String id, @Param("customerId") String customerId,
                           @Param("templateKey") String templateKey, @Param("status") String status,
                           @Param("grantedAt") Date grantedAt, @Param("revokedAt") Date revokedAt,
                           @Param("now") Date now);

    @Select("SELECT s.id, s.session_number AS sessionNumber, s.name, s.theme, s.cover_url AS coverUrl, s.intro, s.start_date AS startDate, s.end_date AS endDate, s.capacity, s.standard_price AS standardPrice, s.returning_price AS returningPrice, s.public_venue AS venue, s.city, s.status, s.registration_confirm_mode AS confirmMode, s.registration_open_at AS registrationOpenAt, s.registration_close_at AS registrationCloseAt, s.leader_name AS leaderName, s.cancel_policy AS cancelPolicy, (SELECT COUNT(*) FROM ql_registration r WHERE r.session_id=s.id AND r.registration_status IN ('pending','confirmed')) AS registeredCount FROM ql_session s WHERE s.id=#{id} AND s.status<>'draft'")
    Map<String, Object> selectSessionDetail(@Param("id") String id);

    @Insert("INSERT INTO ql_invitation(code,session_id,owner_customer_id,source,created_at) VALUES(#{code},#{sessionId},#{ownerId},#{source},#{now})")
    int insertInvitation(@Param("code") String code, @Param("sessionId") String sessionId, @Param("ownerId") String ownerId, @Param("source") String source, @Param("now") Date now);

    @Select("SELECT code,session_id AS sessionId,owner_customer_id AS ownerId FROM ql_invitation WHERE code=#{code}")
    Map<String,Object> selectInvitation(@Param("code") String code);

    @Select("SELECT COUNT(DISTINCT r.session_id) FROM ql_registration r JOIN ql_session s ON s.id=r.session_id JOIN ql_customer c ON c.id=r.customer_id WHERE r.customer_id=#{customerId} AND r.registration_status='confirmed' AND r.is_minor_snapshot=0 AND (c.birth_date IS NULL OR TIMESTAMPDIFF(YEAR,c.birth_date,CURRENT_DATE())>=18) AND s.status='completed' AND (EXISTS(SELECT 1 FROM ql_participation_day pd WHERE pd.registration_id=r.id AND pd.attendance_status IN ('checked_in','late','left_early')) OR EXISTS(SELECT 1 FROM ql_participation p WHERE p.customer_id=r.customer_id AND p.session_id=r.session_id AND p.attendance_status IN ('checked_in','late','left_early','completed') AND NOT EXISTS(SELECT 1 FROM ql_participation_day existing_day JOIN ql_session_day existing_session_day ON existing_session_day.id=existing_day.session_day_id WHERE existing_day.customer_id=r.customer_id AND existing_session_day.session_id=r.session_id)))")
    int countCompletedExperience(@Param("customerId") String customerId);

    @Select("SELECT COUNT(DISTINCT r.session_id) FROM ql_registration r JOIN ql_session s ON s.id=r.session_id WHERE r.customer_id=#{customerId} AND r.registration_status='confirmed' AND s.status='completed' AND (EXISTS(SELECT 1 FROM ql_participation_day pd WHERE pd.registration_id=r.id AND pd.attendance_status IN ('checked_in','late','left_early')) OR EXISTS(SELECT 1 FROM ql_participation p WHERE p.customer_id=r.customer_id AND p.session_id=r.session_id AND p.attendance_status IN ('checked_in','late','left_early','completed') AND NOT EXISTS(SELECT 1 FROM ql_participation_day existing_day JOIN ql_session_day existing_session_day ON existing_session_day.id=existing_day.session_day_id WHERE existing_day.customer_id=r.customer_id AND existing_session_day.session_id=r.session_id)))")
    int countCompletedSessions(@Param("customerId") String customerId);

    @Update("UPDATE ql_registration SET unit_price=#{price} WHERE id=#{id}")
    int setRegistrationPrice(@Param("id") String id, @Param("price") BigDecimal price);

    @Update("UPDATE ql_registration_batch SET quoted_amount=#{amount} WHERE id=#{id}")
    int setBatchQuote(@Param("id") String id, @Param("amount") BigDecimal amount);

    @Update("UPDATE ql_registration_batch SET request_fingerprint=#{fingerprint} WHERE id=#{id}")
    int setRequestFingerprint(@Param("id") String id, @Param("fingerprint") String fingerprint);

    @Select("SELECT r.id AS registrationId,r.customer_id AS customerId,c.nickname AS name,r.registration_status AS status FROM ql_registration r JOIN ql_customer c ON c.id=r.customer_id WHERE r.batch_id=#{id}")
    List<Map<String,Object>> selectBatchRegistrations(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM ql_registration WHERE customer_id=#{customerId} AND session_id=#{sessionId}")
    int countRegistration(@Param("customerId") String customerId, @Param("sessionId") String sessionId);

    @Select("SELECT DISTINCT r.customer_id FROM ql_registration r JOIN ql_registration_batch b ON b.id=r.batch_id WHERE r.customer_id=#{customerId} AND b.submitted_by_customer_id=#{buyerId}")
    String selectOwnedParticipant(@Param("customerId") String customerId, @Param("buyerId") String buyerId);

    @Select("SELECT DISTINCT i.customer_id FROM ql_customer_identifier i WHERE i.identifier_type='phone' AND i.normalized_hash=#{hash} AND i.valid_to IS NULL")
    List<String> selectPhoneCandidates(@Param("hash") String hash);

    @Update("UPDATE ql_customer SET referrer_customer_id=#{referrerId} WHERE id=#{customerId} AND referrer_customer_id IS NULL AND id<>#{referrerId} AND NOT EXISTS (SELECT 1 FROM ql_registration WHERE customer_id=#{customerId})")
    int setFirstReferrer(@Param("customerId") String customerId, @Param("referrerId") String referrerId);

    @Update("UPDATE ql_registration SET session_referrer_customer_id=#{referrerId},attribution_source='invitation' WHERE id=#{id}")
    int setSessionReferrer(@Param("id") String id, @Param("referrerId") String referrerId);

    @Select("SELECT r.session_id AS sessionId,s.status AS sessionStatus,s.start_date AS startDate,s.end_date AS endDate," +
            "(SELECT d.start_time FROM ql_session_day d WHERE d.session_id=s.id AND d.status<>'cancelled' ORDER BY d.day_no LIMIT 1) AS startTime," +
            "(SELECT d.end_time FROM ql_session_day d WHERE d.session_id=s.id AND d.status<>'cancelled' ORDER BY d.day_no DESC LIMIT 1) AS endTime " +
            "FROM ql_registration r JOIN ql_session s ON s.id=r.session_id WHERE r.id=#{id} AND r.customer_id=#{customerId} AND r.registration_status='confirmed' AND s.status<>'cancelled'")
    Map<String,Object> selectExperienceOwner(@Param("id") String id, @Param("customerId") String customerId);

    @Insert("INSERT INTO ql_experience_record(id,registration_id,customer_id,session_id,phase,node_key,energy,relaxation,note,visibility_scope,created_at,updated_at) VALUES(#{id},#{registrationId},#{customerId},#{sessionId},#{phase},#{nodeKey},#{energy},#{relaxation},#{note},'private',#{now},#{now}) ON DUPLICATE KEY UPDATE energy=VALUES(energy),relaxation=VALUES(relaxation),note=VALUES(note),updated_at=VALUES(updated_at)")
    int upsertExperience(@Param("id") String id, @Param("registrationId") String registrationId, @Param("customerId") String customerId, @Param("sessionId") String sessionId, @Param("phase") String phase, @Param("nodeKey") String nodeKey, @Param("energy") Integer energy, @Param("relaxation") Integer relaxation, @Param("note") String note, @Param("now") Date now);

    @Select("SELECT id,registration_id AS registrationId,session_id AS sessionId,phase,node_key AS nodeKey,energy,relaxation,note,visibility_scope AS visibility,updated_at AS updatedAt FROM ql_experience_record WHERE customer_id=#{customerId} ORDER BY updated_at DESC")
    List<Map<String,Object>> selectExperienceRecords(@Param("customerId") String customerId);

    @Select("SELECT e.id,e.title,e.Introduction AS summary,e.title_url AS cover,e.insert_time AS publishedAt," +
            "COALESCE(u.nick_name,'轻生活') AS author,COALESCE((SELECT GROUP_CONCAT(l.name ORDER BY el.id SEPARATOR ' · ') FROM essay_label el JOIN label l ON l.id=el.label WHERE el.essay=e.id),'轻生活') AS category " +
            "FROM essay e LEFT JOIN sys_user u ON u.user_id=e.author WHERE e.status=1 AND e.home_featured=1 ORDER BY e.order_num,e.id LIMIT 8")
    List<Map<String,Object>> selectFeaturedReadings();

    @Select("SELECT e.id,e.title,e.Introduction AS summary,e.title_url AS cover,e.content,e.insert_time AS publishedAt," +
            "COALESCE(u.nick_name,'轻生活') AS author,COALESCE((SELECT GROUP_CONCAT(l.name ORDER BY el.id SEPARATOR ' · ') FROM essay_label el JOIN label l ON l.id=el.label WHERE el.essay=e.id),'轻生活') AS category " +
            "FROM essay e LEFT JOIN sys_user u ON u.user_id=e.author WHERE e.id=#{id} AND e.status=1")
    Map<String,Object> selectPublicReading(@Param("id") Long id);

    @Select("SELECT config_key AS configKey,config_value AS configValue FROM sys_config WHERE config_key IN ('qinglife.contact.name','qinglife.contact.wechat')")
    List<Map<String,Object>> selectContactConfig();
}
