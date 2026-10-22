package com.yicai.life.service;

import com.yicai.life.domain.bo.EssayImportBo;
import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure import preparation. Reuses the existing HTML policy; performs no network or persistence. */
public final class EssayImportPolicy {
    private EssayImportPolicy() {}
    public static final class Invalid extends IllegalArgumentException {
        public final String code;
        public Invalid(String code) { super(code); this.code=code; }
    }
    public static void require(boolean valid,String code) { if(!valid)throw new Invalid(code); }
    public static String text(String value,int max,String code) {
        require(value!=null && !value.trim().isEmpty() && value.length()<=max,code); return value;
    }
    public static final class Prepared {
        public final EssayImportBo input;
        public final String content,cover,sourceHash;
        public final LocalDateTime publishedAt;
        public final int isVideo;
        Prepared(EssayImportBo i,String c,String cover,String hash,LocalDateTime date,int video) {
            input=i;content=c;this.cover=cover;sourceHash=hash;publishedAt=date;isVideo=video;
        }
    }
    public static Prepared prepare(String source,EssayImportBo input) {
        require(source!=null && source.matches("[a-z0-9][a-z0-9_.-]{0,63}"),"INVALID_SOURCE_SYSTEM");
        require(input!=null,"MISSING_ARTICLE");
        text(input.getSourceId(),191,"MISSING_SOURCE_ID");
        require(input.getSourceId().matches("[A-Za-z0-9_.:-]{1,191}"),"INVALID_SOURCE_ID");
        text(input.getSourceUrl(),2048,"MISSING_SOURCE_URL");
        require(https(input.getSourceUrl()),"INVALID_SOURCE_URL");
        text(input.getTitle(),255,"INVALID_TITLE"); text(input.getAuthorName(),128,"INVALID_AUTHOR");
        text(input.getContent(),1000000,"INVALID_CONTENT"); text(input.getApprovalRef(),255,"MISSING_TEST_APPROVAL");
        require(input.getIntroduction()==null || input.getIntroduction().length()<=200,"INVALID_INTRODUCTION");
        require(input.getStatus()!=null && (input.getStatus()==0 || input.getStatus()==1),"INVALID_SOURCE_STATUS");
        require(input.getOrderNum()!=null && input.getOrderNum()>=0,"INVALID_ORDER");
        require("legacy_insert_time".equals(input.getTimeBasis()) || "published_at".equals(input.getTimeBasis()),"UNKNOWN_TIME_BASIS");
        LocalDateTime date;
        try { date=LocalDateTime.parse(input.getPublishedAt().replace(' ','T')); }
        catch(Exception e) { throw new Invalid("INVALID_SOURCE_TIME"); }
        require(date.getNano()==0,"SOURCE_TIME_PRECISION");
        require(input.getLabels()!=null && input.getMediaMap()!=null,"MISSING_COLLECTIONS");
        Set<String> ids=new HashSet<>();
        for(EssayImportBo.LabelInput l:input.getLabels()) {
            require(l!=null,"INVALID_LABEL"); text(l.getSourceId(),191,"MISSING_LABEL_ID");
            require(l.getSourceId().matches("[A-Za-z0-9_.:-]{1,191}"),"INVALID_LABEL_ID");
            text(l.getName(),20,"INVALID_LABEL_NAME");
            require(ids.add(l.getSourceId()),"DUPLICATE_LABEL_ID");
            require(l.getStatus()!=null && (l.getStatus()==0 || l.getStatus()==1),"INVALID_LABEL_STATUS");
            require(l.getLevel()!=null && l.getLevel()>=0,"INVALID_LABEL_ORDER");
        }
        Document original=Jsoup.parseBodyFragment(input.getContent());
        require(original.select("iframe,audio,object,embed").isEmpty(),"UNSUPPORTED_MEDIA");
        for(Element item:original.select("img,source"))require(item.hasAttr("src"),"MISSING_MEDIA_SOURCE");
        for(Element item:original.select("video"))require(item.hasAttr("src") || !item.select("source[src]").isEmpty(),"MISSING_MEDIA_SOURCE");
        for(Element media:original.select("img,video,source")) for(String attr:new String[]{"src","poster"}) {
            if(!media.hasAttr(attr))continue;
            String mapped=input.getMediaMap().getOrDefault(media.attr(attr),media.attr(attr));
            require(mediaUrl(mapped),"MEDIA_NEEDS_VERIFIED_MAPPING");
            media.attr(attr,mapped);
        }
        String cover=Objects.toString(input.getCover(),"");
        if(!cover.isEmpty()) {
            cover=input.getMediaMap().getOrDefault(cover,cover);
            require(mediaUrl(cover) && cover.length()<=300,"COVER_NEEDS_VERIFIED_MAPPING");
        }
        original.outputSettings().prettyPrint(false);
        String clean=EssayHtmlSanitizer.clean(original.body().html());
        Document visible=Jsoup.parseBodyFragment(clean);
        require(!visible.text().trim().isEmpty() || !visible.select("img,video").isEmpty(),"EMPTY_SANITIZED_CONTENT");
        require(clean.getBytes(StandardCharsets.UTF_8).length<=65535,"CONTENT_EXCEEDS_SCHEMA");
        // Stable, explicit source fields. Editorial changes must not be silently overwritten on replay.
        Map<String,Object> signature=new LinkedHashMap<>();
        signature.put("source",source);signature.put("id",input.getSourceId());signature.put("url",input.getSourceUrl());
        signature.put("title",input.getTitle());signature.put("introduction",input.getIntroduction());
        signature.put("content",input.getContent());signature.put("author",input.getAuthorName());
        signature.put("time",input.getPublishedAt());signature.put("timeBasis",input.getTimeBasis());
        signature.put("status",input.getStatus());signature.put("order",input.getOrderNum());signature.put("cover",input.getCover());
        signature.put("labels",input.getLabels());signature.put("mediaMap",new TreeMap<>(input.getMediaMap()));
        return new Prepared(input,clean,cover,DigestUtil.sha256Hex(JSONUtil.toJsonStr(signature)),date,
                original.select("video").isEmpty()?0:1);
    }
    private static boolean https(String value) {
        try { URI u=URI.create(value);return "https".equals(u.getScheme()) && u.getHost()!=null && u.getUserInfo()==null; }
        catch(Exception e){return false;}
    }
    private static boolean mediaUrl(String value) {
        if(https(value))return true;
        try { URI u=URI.create(value);return "http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getUserInfo()==null; }
        catch(Exception e){return false;}
    }
}
