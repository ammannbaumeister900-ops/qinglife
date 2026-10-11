package com.yicai.web.controller.life;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlCampReflectionBo;
import com.yicai.life.service.QlCampReflectionService;
import org.junit.jupiter.api.Test;
import javax.validation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QlReflectionSubmissionContractTest {
 QlCampReflectionBo post(){QlCampReflectionBo b=new QlCampReflectionBo();b.setRegistrationId("reg");b.setPhase("after");b.setRevision(0);b.setNote("文字");b.setAction("submit");b.setAnonymous(false);b.setShareConsent(true);b.setOriginalVersion("absent");b.setSource("new");return b;}
 @Test void everyMissingPublicContractFieldReturnsUpgradeBeforeGenericValidationAndBeforeServiceWrite(){
  QlCampReflectionService service=mock(QlCampReflectionService.class);
  try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
   QlCampReflectionController controller=new QlCampReflectionController(service,factory.getValidator());
   for(int field=0;field<10;field++){QlCampReflectionBo b=post();switch(field){case 0:b.setRegistrationId(null);break;case 1:b.setPhase(null);break;case 2:b.setRevision(null);break;case 3:b.setNote(null);break;case 4:b.setAction(null);break;case 5:b.setAnonymous(null);break;case 6:b.setAnonymous(true);break;case 7:b.setShareConsent(null);break;case 8:b.setOriginalVersion(null);break;case 9:b.setSource(null);break;}
    assertEquals(426,assertThrows(CustomException.class,()->controller.save("isolated",b)).getCode());}
   verifyNoInteractions(service);
  }
 }
 @Test void validPublicAndLegacyAnonymousPrivateRequestsKeepTheirNormalValidatedPath(){
  QlCampReflectionService service=mock(QlCampReflectionService.class);
  try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
   QlCampReflectionController controller=new QlCampReflectionController(service,factory.getValidator());QlCampReflectionBo publicPost=post();controller.save("isolated",publicPost);verify(service).save("isolated",publicPost);
   QlCampReflectionBo privateDraft=post();privateDraft.setAction("private");privateDraft.setAnonymous(null);privateDraft.setShareConsent(null);privateDraft.setOriginalVersion(null);privateDraft.setSource(null);controller.save("isolated",privateDraft);verify(service).save("isolated",privateDraft);
  }
 }
 @Test void privateValidationStillRejectsInvalidPhaseRevisionAndOverlongBody(){
  QlCampReflectionService service=mock(QlCampReflectionService.class);
  try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
   QlCampReflectionController controller=new QlCampReflectionController(service,factory.getValidator());for(int field=0;field<3;field++){QlCampReflectionBo b=post();b.setAction("private");if(field==0)b.setPhase("during");if(field==1)b.setRevision(-1);if(field==2)b.setNote(String.join("",java.util.Collections.nCopies(301,"字")));assertThrows(ConstraintViolationException.class,()->controller.save("isolated",b));}verifyNoInteractions(service);
  }
 }
}
