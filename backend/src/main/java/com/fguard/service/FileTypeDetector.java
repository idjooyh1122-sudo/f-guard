package com.fguard.service;

import java.util.Optional;

/** 확장자/Content-Type 대신 파일 시그니처(매직 바이트)로 실제 형식을 판별한다. */
public final class FileTypeDetector {
    private FileTypeDetector() {}

    public static Optional<String> detect(byte[] b) {
        if (b == null || b.length < 12) return Optional.empty();
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return Optional.of("application/pdf");
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return Optional.of("image/png");
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Optional.of("image/jpeg");
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }
}
