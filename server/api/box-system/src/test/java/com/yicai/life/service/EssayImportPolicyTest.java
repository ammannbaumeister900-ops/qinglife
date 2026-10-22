package com.yicai.life.service;
import com.yicai.life.domain.bo.EssayImportBo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EssayImportPolicyTest {
    static EssayImportBo article() {
        EssayImportBo i=new EssayImportBo();i.setSourceId("9007199254741000");i.setSourceUrl("https://legacy.invalid/essay/9007199254741000");
        i.setTitle("合成原题");i.setIntroduction("合成简介");i.setContent("<p>原文一</p><p>原文二</p>");
        i.setAuthorName("原署名");i.setPublishedAt("2020-05-13 07:59:37");i.setTimeBasis("legacy_insert_time");
        i.setStatus(1);i.setOrderNum(2);i.setCover("");i.setApprovalRef("synthetic-only");return i;
    }
    @Test void preservesOriginalBylineTimeAndLargeStringId() {
        EssayImportBo i=article();EssayImportPolicy.Prepared p=EssayImportPolicy.prepare("synthetic",i);
        assertEquals("原署名",p.input.getAuthorName());assertEquals("9007199254741000",p.input.getSourceId());
        assertEquals("2020-05-13T07:59:37",p.publishedAt.toString());assertTrue(p.content.contains("原文二"));
    }
    @Test void requiresAnActualSourceIdAndExplicitUseApproval() {
        EssayImportBo i=article();i.setSourceId(null);
        assertEquals("MISSING_SOURCE_ID",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
        i.setSourceId("1");i.setApprovalRef(null);
        assertEquals("MISSING_TEST_APPROVAL",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
    }
    @Test void exactMediaMappingPreservesMixedOrderAndCleansExecutableMarkup() {
        EssayImportBo i=article();i.setContent("<p>前</p><video src='http://legacy.invalid/v.mp4' autoplay onerror='bad()'></video><p>后</p><img src='http://legacy.invalid/i.jpg'><script>bad()</script>");
        i.getMediaMap().put("http://legacy.invalid/v.mp4","https://media.invalid/v.mp4");
        i.getMediaMap().put("http://legacy.invalid/i.jpg","https://media.invalid/i.jpg");
        String html=EssayImportPolicy.prepare("synthetic",i).content;
        assertTrue(html.indexOf("前")<html.indexOf("<video"));assertTrue(html.indexOf("<video")<html.indexOf("后"));
        assertTrue(html.contains("https://media.invalid/v.mp4"));assertFalse(html.contains("autoplay"));
        assertFalse(html.contains("onerror"));assertFalse(html.contains("<script"));
    }
    @Test void remoteHttpAndUnmappedAdminCoverAreBlocked() {
        EssayImportBo i=article();i.setContent("<img src='http://legacy.invalid/a.jpg'>");
        assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i));
        i.setContent("<p>正文</p>");i.setCover("/admin/data/static/c.jpg");
        assertEquals("COVER_NEEDS_VERIFIED_MAPPING",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
    }
    @Test void embeddedPlayersAndOversizedUtf8ContentFailBeforePersistence() {
        EssayImportBo i=article();i.setContent("<iframe src='https://video.invalid/player'></iframe>");
        assertEquals("UNSUPPORTED_MEDIA",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
        i.setContent("<p>"+String.join("",Collections.nCopies(23000,"文"))+"</p>");
        assertEquals("CONTENT_EXCEEDS_SCHEMA",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
    }
    @Test void emptySanitizedBodiesAndMediaWithoutSourcesAreRejected() {
        EssayImportBo i=article();i.setContent("<script>bad()</script>");
        assertEquals("EMPTY_SANITIZED_CONTENT",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
        i.setContent("<video></video>");
        assertEquals("MISSING_MEDIA_SOURCE",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
    }
    @Test void labelAmpersandRemainsOneNameAndDuplicateSourceLabelsFail() {
        EssayImportBo i=article();EssayImportBo.LabelInput l=new EssayImportBo.LabelInput();
        l.setSourceId("73");l.setName("复食计划&好转反应");l.setStatus(1);l.setLevel(2);i.getLabels().add(l);
        assertEquals(1,EssayImportPolicy.prepare("synthetic",i).input.getLabels().size());
        i.getLabels().add(l);assertEquals("DUPLICATE_LABEL_ID",assertThrows(EssayImportPolicy.Invalid.class,()->EssayImportPolicy.prepare("synthetic",i)).code);
    }
    @Test void sourceAndMediaChangesChangeFingerprintWhileMapInsertionOrderDoesNot() {
        EssayImportBo a=article(),b=article();a.getMediaMap().put("x","https://media.invalid/x");a.getMediaMap().put("y","https://media.invalid/y");
        b.getMediaMap().put("y","https://media.invalid/y");b.getMediaMap().put("x","https://media.invalid/x");
        assertEquals(EssayImportPolicy.prepare("synthetic",a).sourceHash,EssayImportPolicy.prepare("synthetic",b).sourceHash);
        b.setAuthorName("修订署名");assertNotEquals(EssayImportPolicy.prepare("synthetic",a).sourceHash,EssayImportPolicy.prepare("synthetic",b).sourceHash);
    }
}
