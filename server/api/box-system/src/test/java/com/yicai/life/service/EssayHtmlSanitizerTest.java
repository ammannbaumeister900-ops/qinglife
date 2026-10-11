package com.yicai.life.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EssayHtmlSanitizerTest {
    @Test void legalArticleKeepsTextStructureImagesLinksAndEditorFormatting() {
        String html="<h2>标题</h2><p class='ql-align-center' style='color: rgb(10, 20, 30);font-size:18px'>原文<strong>加粗</strong><u>下划线</u></p><blockquote>引文</blockquote><ul><li>清单</li></ul><img src='/media/photo.jpg' alt='照片' width='640'><a href='https://example.invalid/story'>出处</a><table><tr><td>表格</td></tr></table>";
        Document safe=Jsoup.parseBodyFragment(EssayHtmlSanitizer.clean(html));
        assertEquals("标题",safe.select("h2").text());assertEquals("加粗",safe.select("strong").text());
        assertEquals("下划线",safe.select("u").text());assertEquals("引文",safe.select("blockquote").text());
        assertEquals("清单",safe.select("li").text());assertEquals("表格",safe.select("td").text());
        assertEquals("/media/photo.jpg",safe.select("img").attr("src"));assertEquals("照片",safe.select("img").attr("alt"));
        assertEquals("https://example.invalid/story",safe.select("a").attr("href"));
        assertTrue(safe.select("p").hasClass("ql-align-center"));assertTrue(safe.select("p").attr("style").contains("font-size:18px"));
    }
    @Test void scriptsEventsForeignNamespacesEmbedsAndExecutableCssAreRemoved() {
        String html="<script>alert(1)</script><style>body{display:none}</style><iframe src='https://example.invalid/'></iframe><svg><a xlink:href='javascript:alert(1)'>坏</a></svg><math><mtext>坏</mtext></math><p id='location' onclick='alert(1)' class='hidden ql-indent-2' style='position:fixed;background:url(javascript:alert(1));width:expression(alert(1));color:#123456'>保留原文</p><img src='data:image/svg+xml,bad' onerror='alert(1)'>";
        String cleaned=EssayHtmlSanitizer.clean(html);Document safe=Jsoup.parseBodyFragment(cleaned);
        assertEquals("保留原文",safe.text());assertTrue(safe.select("script,style,iframe,svg,math,[onclick],[onerror],[id]").isEmpty());
        assertEquals("color:#123456",safe.select("p").attr("style"));assertEquals("ql-indent-2",safe.select("p").attr("class"));assertFalse(safe.select("img").hasAttr("src"));
    }
    @Test void encodedAndMalformedDangerousUrlsFailClosedWithoutLosingLinkText() {
        for(String url:new String[]{"javascript:alert(1)","jav&#x61;script:alert(1)","java&#10;script:alert(1)","vbscript:msgbox(1)","data:text/html,bad"}) {
            Document safe=Jsoup.parseBodyFragment(EssayHtmlSanitizer.clean("<a href='"+url+"'>出处</a><img src='"+url+"'>"));
            assertEquals("出处",safe.select("a").text());assertFalse(safe.select("a").hasAttr("href"));assertFalse(safe.select("img").hasAttr("src"));
        }
    }
    @Test void existingUploadedVideoKeepsSafeMediaButNeverAutoplaysOrExecutes() {
        Document safe=Jsoup.parseBodyFragment(EssayHtmlSanitizer.clean("<video src='https://example.invalid/movie.mp4' poster='/media/poster.jpg' autoplay onplay='bad()' width='640' playsinline><source src='/media/movie.mp4' type='video/mp4'></video>"));
        assertEquals("https://example.invalid/movie.mp4",safe.select("video").attr("src"));assertEquals("/media/movie.mp4",safe.select("source").attr("src"));
        assertTrue(safe.select("video").hasAttr("controls"));assertFalse(safe.select("video").hasAttr("autoplay"));assertFalse(safe.select("video").hasAttr("onplay"));
    }
    @Test void cleaningIsIdempotentAndDoesNotTruncateLongUnicodeOriginals() {
        String html="<p>"+String.join("",java.util.Collections.nCopies(2000,"🌿原文"))+" &amp; 结尾</p>";
        assertEquals(html,EssayHtmlSanitizer.clean(html));assertEquals(html,EssayHtmlSanitizer.clean(EssayHtmlSanitizer.clean(html)));
        assertNull(EssayHtmlSanitizer.clean(null));assertEquals("",EssayHtmlSanitizer.clean(""));
    }
}
