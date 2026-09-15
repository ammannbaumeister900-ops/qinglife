package com.yicai.web.controller.common;

import com.yicai.common.config.RuoYiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CommonControllerTest {
    @TempDir Path temp;

    @Test void downloadReturnsFileAndNeverDeletesIt() throws Exception {
        Path download = Files.createDirectories(temp.resolve("download"));
        Path file = download.resolve("123_report.txt");
        Files.write(file, "synthetic".getBytes(StandardCharsets.UTF_8));
        new RuoYiConfig().setProfile(temp.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CommonController().fileDownload(file.getFileName().toString(), response);

        assertEquals(200, response.getStatus());
        assertEquals("synthetic", response.getContentAsString());
        assertTrue(Files.exists(file));
    }

    @Test void missingAndEscapingPathsReturnNotFound() throws Exception {
        new RuoYiConfig().setProfile(temp.toString());
        CommonController controller = new CommonController();
        MockHttpServletResponse missing = new MockHttpServletResponse();
        MockHttpServletResponse escaping = new MockHttpServletResponse();

        controller.fileDownload("missing.txt", missing);
        controller.fileDownload("../secret.txt", escaping);

        assertEquals(404, missing.getStatus());
        assertEquals(404, escaping.getStatus());
    }

    @Test void resourceRequiresProfilePrefixAndStaysInsideProfile() throws Exception {
        new RuoYiConfig().setProfile(temp.toString());
        CommonController controller = new CommonController();
        MockHttpServletResponse badPrefix = new MockHttpServletResponse();
        MockHttpServletResponse escaping = new MockHttpServletResponse();

        controller.resourceDownload("/other/file.txt", badPrefix);
        controller.resourceDownload("/profile/../file.txt", escaping);

        assertEquals(404, badPrefix.getStatus());
        assertEquals(404, escaping.getStatus());
    }
}
