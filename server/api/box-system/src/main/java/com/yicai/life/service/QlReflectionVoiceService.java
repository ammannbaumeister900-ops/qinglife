package com.yicai.life.service;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlReflectionVoiceBo;
import org.redisson.api.RedissonClient;
import org.redisson.api.RAtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed Tencent endpoint, short in-memory audio, no persistent raw recording. */
@Service
public class QlReflectionVoiceService {
 private final RedissonClient redis;
 private final boolean enabled;
 private final String secretId,secretKey;
 private final int monthlyBudget;
 public QlReflectionVoiceService(RedissonClient redis,@Value("${qinglife.asr.enabled:false}") boolean enabled,@Value("${qinglife.asr.secret-id:}") String secretId,@Value("${qinglife.asr.secret-key:}") String secretKey,@Value("${qinglife.asr.monthly-budget:4500}") int monthlyBudget){
  if(monthlyBudget<1||monthlyBudget>5000)throw new IllegalArgumentException("Free ASR monthly budget must be between 1 and 5000");
  this.redis=redis;this.enabled=enabled;this.secretId=secretId;this.secretKey=secretKey;this.monthlyBudget=monthlyBudget;
 }
 public boolean available(){return enabled&&!secretId.trim().isEmpty()&&!secretKey.trim().isEmpty();}
 public Map<String,Object> transcribe(String customer,QlReflectionVoiceBo bo) {
  if(!available())throw new CustomException("语音转文字暂未开通，请先用文字记录",503);
  if(bo==null||bo.getAudio()==null||bo.getDurationMs()==null)throw new CustomException("录音格式无效",400);
  byte[] audio;
  try{audio=Base64.getDecoder().decode(bo.getAudio());}catch(IllegalArgumentException e){throw new CustomException("录音格式无效",400);}
  if(audio.length<16||bo.getAudio().length()>2800000||bo.getDurationMs()<1||bo.getDurationMs()>45000)throw new CustomException("请录制45秒以内的短语音",400);
  long slot=Instant.now().getEpochSecond()/600;RAtomicLong quota=redis.getAtomicLong("ql:asr:"+customer+":"+slot);long used=quota.incrementAndGet();if(used==1)quota.expire(11,TimeUnit.MINUTES);if(used>6)throw new CustomException("语音较频繁，请稍后再试",429);
  RAtomicLong daily=redis.getAtomicLong("ql:asr:day:"+customer+":"+LocalDate.now(ZoneId.of("Asia/Shanghai")));long dailyUsed=daily.incrementAndGet();if(dailyUsed==1)daily.expire(25,TimeUnit.HOURS);if(dailyUsed>30)throw new CustomException("今天已记录许多声音，可以先用文字补充",429);
  RAtomicLong monthly=redis.getAtomicLong("ql:asr:month:"+YearMonth.now(ZoneId.of("Asia/Shanghai")));
  long monthlyUsed=monthly.incrementAndGet();if(monthlyUsed==1)monthly.expire(33,TimeUnit.DAYS);
  if(monthlyUsed>monthlyBudget){Arrays.fill(audio,(byte)0);throw new CustomException("本月语音次数已达上限，可以继续用文字记录",429);}
  long timestamp=Instant.now().getEpochSecond();
  String date=Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate().toString();
  JSONObject payload=new JSONObject();payload.set("EngSerViceType","16k_zh");payload.set("SourceType",1);payload.set("VoiceFormat","mp3");payload.set("Data",bo.getAudio());payload.set("DataLen",audio.length);
  String body=payload.toString(),scope=date+"/asr/tc3_request";
  String canonical="POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:asr.tencentcloudapi.com\n\ncontent-type;host\n"+DigestUtil.sha256Hex(body);
  String signing="TC3-HMAC-SHA256\n"+timestamp+"\n"+scope+"\n"+DigestUtil.sha256Hex(canonical);
  HttpURLConnection connection=null;
  try {
   byte[] key=hmac(hmac(hmac(("TC3"+secretKey).getBytes(StandardCharsets.UTF_8),date),"asr"),"tc3_request");
   String signature=cn.hutool.core.util.HexUtil.encodeHexStr(hmac(key,signing));
   connection=openConnection();connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(8000);connection.setReadTimeout(30000);connection.setDoOutput(true);
   connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("X-TC-Action","SentenceRecognition");connection.setRequestProperty("X-TC-Version","2019-06-14");connection.setRequestProperty("X-TC-Timestamp",String.valueOf(timestamp));
   connection.setRequestProperty("Authorization","TC3-HMAC-SHA256 Credential="+secretId+"/"+scope+", SignedHeaders=content-type;host, Signature="+signature);
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
   if(connection.getResponseCode()!=200)throw new IOException("ASR unavailable");
   ByteArrayOutputStream output=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(output.size()+n>65536)throw new IOException("ASR response too large");output.write(buffer,0,n);}}
   JSONObject response=JSONUtil.parseObj(new String(output.toByteArray(),StandardCharsets.UTF_8)).getJSONObject("Response");
   if(response==null||response.containsKey("Error"))throw new IOException("ASR failed");
   String text=response.getStr("Result","");if(text.trim().isEmpty())throw new CustomException("没有听清这段语音，可以再试一次",400);
   if(response.getInt("AudioDuration",0)>45000)throw new CustomException("这段语音超过45秒，请分段记录",400);
   Map<String,Object> result=new LinkedHashMap<>();result.put("text",text);return result;
  }catch(CustomException e){throw e;}catch(Exception e){throw new CustomException("语音转文字暂时未成功，你可以重试或改用文字",503);}finally{Arrays.fill(audio,(byte)0);if(connection!=null)connection.disconnect();}
 }
 HttpURLConnection openConnection()throws IOException{return (HttpURLConnection)new URL("https://asr.tencentcloudapi.com/").openConnection();}
 private static byte[] hmac(byte[] key,String message)throws Exception {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));}
}
