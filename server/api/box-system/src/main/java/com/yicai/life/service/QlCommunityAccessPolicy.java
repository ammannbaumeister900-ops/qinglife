package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import org.springframework.stereotype.Component;

/** Keep immediate invitations and final publication separate from submitting for review. */
@Component
public class QlCommunityAccessPolicy {
    public boolean canInvite(String customerId) {
        // Customer codes take effect immediately and have no operational review.
        // Keep this path paused until its minimal safe handling is confirmed.
        return false;
    }

    public void requireInvitationOwner(String customerId) {
        // Staff codes retain their existing controller permissions and admission checks.
        if (customerId != null && !canInvite(customerId))
            throw new CustomException("此轻友邀请暂不可用，请从公开活动入口查看", 403);
    }

    public void requireInvitationCreation(String customerId) {
        if (!canInvite(customerId))
            throw new CustomException("轻友邀请暂未开放，请联系工作人员", 403);
    }

    public void requirePublicationReview() {
        // Old clients without the per-publication review contract remain fail closed.
        throw new CustomException("请更新后台并补充发布核对依据", 409);
    }
}
