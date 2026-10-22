package com.yicai.web.controller.life;
import com.yicai.life.service.*;
import com.yicai.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QlAssessmentSharePermissionTest {
    @Test void sharingRequiresOperationalGrantAndDoesNotReadAnyHealthHistory() {
        QlStaffWorkspaceService staff=mock(QlStaffWorkspaceService.class);
        QlFriendAssessmentService forms=mock(QlFriendAssessmentService.class);
        QlStaffWorkspaceController controller=new QlStaffWorkspaceController(staff,mock(IQlRegistrationService.class),forms);
        Map<String,Object> access=new HashMap<>();access.put("can_payment",1);
        when(staff.access("token")).thenReturn(access);
        doCallRealMethod().when(staff).permit(anyMap(),anyString());
        assertThrows(CustomException.class,()->controller.assessmentSessions("token"));
        access.put("can_operate",0);
        assertThrows(CustomException.class,()->controller.assessmentShare("token","session"));
        verifyNoInteractions(forms);
        access.put("can_operate",1);when(staff.operator(access)).thenReturn(9L);
        controller.assessmentSessions("token");controller.assessmentShare("token","session");
        verify(forms).assessmentSessions();verify(forms).createAssessmentInvitation("session",9L);verifyNoMoreInteractions(forms);
    }
    @Test void missingOrRevokedStaffIdentityCannotCreateAShare() {
        QlStaffWorkspaceService staff=mock(QlStaffWorkspaceService.class);QlFriendAssessmentService forms=mock(QlFriendAssessmentService.class);
        QlStaffWorkspaceController controller=new QlStaffWorkspaceController(staff,mock(IQlRegistrationService.class),forms);
        when(staff.access(any())).thenThrow(new CustomException("未获得工作人员权限",403));
        assertThrows(CustomException.class,()->controller.assessmentShare(null,"session"));verifyNoInteractions(forms);
    }
}
