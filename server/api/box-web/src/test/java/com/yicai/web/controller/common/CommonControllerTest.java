package com.yicai.web.controller.common;

import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.domain.model.LoginUser;
import com.yicai.common.utils.file.ExportFileAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommonControllerTest {
    @TempDir Path temp;

    private void login(long userId) {
        LoginUser user = new LoginUser(new SysUser().setUserId(userId), Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @AfterEach void clearLogin() { SecurityContextHolder.clearContext(); }

    @Test void exportCanOnlyBeReadByItsCreatorAndIsNeverDeleted() throws Exception {
        Path download = Files.createDirectories(temp.resolve("download"));
        Path file = download.resolve(UUID.randomUUID() + "_report.xlsx");
        Files.write(file, "synthetic".getBytes(StandardCharsets.UTF_8));
        new RuoYiConfig().setProfile(temp.toString());
        login(17);
        ExportFileAccess.record(file);
        MockHttpServletResponse owner = new MockHttpServletResponse();
        new CommonController().fileDownload(file.getFileName().toString(), owner);
        assertEquals(200, owner.getStatus());
        assertEquals("synthetic", owner.getContentAsString());
        assertTrue(Files.exists(file));

        login(18);
        MockHttpServletResponse other = new MockHttpServletResponse();
        new CommonController().fileDownload(file.getFileName().toString(), other);
        assertEquals(403, other.getStatus());
        assertEquals(0, other.getContentAsByteArray().length);
        assertTrue(Files.exists(file));
    }

    @Test void unregisteredExportAndEscapingPathsAreNotReadable() throws Exception {
        Path download = Files.createDirectories(temp.resolve("download"));
        Path legacy = download.resolve(UUID.randomUUID() + "_legacy.xlsx");
        Files.write(legacy, "legacy".getBytes(StandardCharsets.UTF_8));
        new RuoYiConfig().setProfile(temp.toString());
        login(17);
        CommonController controller = new CommonController();
        MockHttpServletResponse unregistered = new MockHttpServletResponse();
        MockHttpServletResponse escaping = new MockHttpServletResponse();
        MockHttpServletResponse missing = new MockHttpServletResponse();

        controller.fileDownload(legacy.getFileName().toString(), unregistered);
        controller.fileDownload("../secret.xlsx", escaping);
        controller.fileDownload(UUID.randomUUID() + "_missing.xlsx", missing);

        assertEquals(403, unregistered.getStatus());
        assertEquals(404, escaping.getStatus());
        assertEquals(404, missing.getStatus());
    }

    @Test void publicDirectoryCannotResolveASiblingPrivateFile() throws Exception {
        Path publicRoot = Files.createDirectories(temp.resolve("public"));
        Path privateFile = temp.resolve("private.txt");
        Files.write(privateFile, "private".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> CommonController.resolveInside(publicRoot.toString(), "../private.txt"));
    }

    @Test void resourceDownloadOnlyAllowsExplicitPublicArea() throws Exception {
        new RuoYiConfig().setProfile(temp.toString());
        Path publicFile = Files.createDirectories(temp.resolve("public")).resolve("guide.txt");
        Files.write(publicFile, "guide".getBytes(StandardCharsets.UTF_8));
        CommonController controller = new CommonController();
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        MockHttpServletResponse privateFile = new MockHttpServletResponse();
        MockHttpServletResponse escaping = new MockHttpServletResponse();

        controller.resourceDownload("/profile/public/guide.txt", allowed);
        controller.resourceDownload("/profile/private.txt", privateFile);
        controller.resourceDownload("/profile/public/../download/secret.txt", escaping);

        assertEquals(200, allowed.getStatus());
        assertEquals("guide", allowed.getContentAsString());
        assertEquals(404, privateFile.getStatus());
        assertEquals(404, escaping.getStatus());
    }
}