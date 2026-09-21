package com.yicai.common.utils.file;

import com.yicai.common.utils.SecurityUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Binds a generated Excel export to the administrator who requested it. */
public final class ExportFileAccess {
    private ExportFileAccess() { }

    public static void record(Path export) throws IOException {
        Long userId = SecurityUtils.getLoginUser().getUser().getUserId();
        if (userId == null) throw new IOException("Export has no owner");
        Files.write(ownerFile(export), userId.toString().getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public static boolean ownedByCurrentUser(Path export) throws IOException {
        Long userId = SecurityUtils.getLoginUser().getUser().getUserId();
        Path owner = ownerFile(export);
        return userId != null && Files.isRegularFile(owner) &&
                userId.toString().equals(new String(Files.readAllBytes(owner), StandardCharsets.US_ASCII));
    }

    private static Path ownerFile(Path export) {
        return export.resolveSibling(export.getFileName().toString() + ".owner");
    }
}