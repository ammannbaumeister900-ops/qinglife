package com.yicai.life.service;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.yicai.life.domain.bo.QlReflectionVoiceBo;
import com.yicai.common.exception.CustomException;
import org.redisson.api.RedissonClient;
import org.redisson.api.RAtomicLong;
import org.junit.jupiter.api.Test;
import java.net.HttpURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class QlReflectionVoiceServiceTest {
 private QlReflectionVoiceBo audio(){QlReflectionVoiceBo bo=new QlReflectionVoiceBo();bo.setAudio(Base64.getEncoder().encodeToString(new byte[32]));bo.setDurationMs(1200);return bo;}
 private RedissonClient quota(RAtomicLong monthly){RedissonClient redis=mock(RedissonClient.class);RAtomicLong user=mock(RAtomicLong.class);when(user.incrementAndGet()).thenReturn(1L);when(redis.getAtomicLong(anyString())).thenAnswer(invocation->invocation.getArgument(0).toString().startsWith("ql:asr:month:")?monthly:user);return redis;}
 private QlReflectionVoiceService service(RedissonClient redis,int budget,HttpURLConnection connection,AtomicInteger opened){return new QlReflectionVoiceService(redis,true,"synthetic-id","synthetic-key",budget){@Override HttpURLConnection openConnection(){opened.incrementAndGet();return connection;}};}
 @Test void unconfiguredServiceFailsClosedBeforeAnyNetworkOrQuota(){RedissonClient redis=mock(RedissonClient.class);QlReflectionVoiceService service=new QlReflectionVoiceService(redis,false,"","",4500);assertFalse(service.available());assertThrows(CustomException.class,()->service.transcribe("synthetic",new QlReflectionVoiceBo()));verifyNoInteractions(redis);}
 @Test void invalidOrMissingAudioNeverReachesProvider(){RedissonClient redis=mock(RedissonClient.class);QlReflectionVoiceService service=new QlReflectionVoiceService(redis,true,"synthetic", "synthetic",4500);QlReflectionVoiceBo bo=audio();bo.setAudio("invalid!");assertThrows(CustomException.class,()->service.transcribe("synthetic",bo));assertThrows(CustomException.class,()->service.transcribe("synthetic",new QlReflectionVoiceBo()));verifyNoInteractions(redis);}
 @Test void freeBudgetCannotBeConfiguredAboveThePublishedAllowance(){assertThrows(IllegalArgumentException.class,()->new QlReflectionVoiceService(mock(RedissonClient.class),true,"synthetic","synthetic",5001));}
 @Test void monthlyBudgetStopsBeforeProviderAndDoesNotResetAnExistingCounter(){RAtomicLong monthly=mock(RAtomicLong.class);when(monthly.incrementAndGet()).thenReturn(4501L);AtomicInteger opened=new AtomicInteger();QlReflectionVoiceService service=service(quota(monthly),4500,null,opened);CustomException error=assertThrows(CustomException.class,()->service.transcribe("synthetic",audio()));assertTrue(error.getMessage().contains("本月语音次数"));assertEquals(0,opened.get());verify(monthly,never()).expire(anyLong(),any(TimeUnit.class));}
 @Test void currentApiReceivesSignedShortMp3AndOnlyReturnsTheTranscript()throws Exception{
  RAtomicLong monthly=mock(RAtomicLong.class);when(monthly.incrementAndGet()).thenReturn(1L);
  HttpURLConnection connection=mock(HttpURLConnection.class);ByteArrayOutputStream body=new ByteArrayOutputStream();when(connection.getOutputStream()).thenReturn(body);when(connection.getResponseCode()).thenReturn(200);
  when(connection.getInputStream()).thenReturn(new ByteArrayInputStream("{\"Response\":{\"Result\":\"今天慢慢来\",\"AudioDuration\":1200}}".getBytes(StandardCharsets.UTF_8)));
  AtomicInteger opened=new AtomicInteger();java.util.Map<String,Object> result=service(quota(monthly),4500,connection,opened).transcribe("synthetic",audio());
  assertEquals("今天慢慢来",result.get("text"));assertEquals(1,result.size());assertEquals(1,opened.get());
  JSONObject request=JSONUtil.parseObj(new String(body.toByteArray(),StandardCharsets.UTF_8));assertEquals("16k_zh",request.getStr("EngSerViceType"));assertEquals("mp3",request.getStr("VoiceFormat"));assertEquals(1,request.getInt("SourceType"));assertEquals(32,request.getInt("DataLen"));
  verify(connection).setRequestProperty("X-TC-Action","SentenceRecognition");verify(connection).setRequestProperty(eq("Authorization"),startsWith("TC3-HMAC-SHA256 Credential=synthetic-id/"));verify(connection).disconnect();verify(monthly).expire(33,TimeUnit.DAYS);
 }
 @Test void providerFailureDoesNotRefundTheConservativeBudgetOrExposeProviderDetails()throws Exception{
  RAtomicLong monthly=mock(RAtomicLong.class);when(monthly.incrementAndGet()).thenReturn(2L);HttpURLConnection connection=mock(HttpURLConnection.class);when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());when(connection.getResponseCode()).thenReturn(500);
  CustomException error=assertThrows(CustomException.class,()->service(quota(monthly),4500,connection,new AtomicInteger()).transcribe("synthetic",audio()));assertTrue(error.getMessage().contains("改用文字"));verify(monthly,never()).decrementAndGet();verify(connection).disconnect();
 }
}
