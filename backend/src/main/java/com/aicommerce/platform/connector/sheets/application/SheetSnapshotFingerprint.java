package com.aicommerce.platform.connector.sheets.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class SheetSnapshotFingerprint {

    private SheetSnapshotFingerprint() {
    }

    public static String fingerprint(List<List<String>> rows) {
        MessageDigest digest = sha256();
        List<List<String>> safeRows = rows == null ? List.of() : rows;
        updateInt(digest, safeRows.size());
        for (List<String> row : safeRows) {
            List<String> safeRow = row == null ? List.of() : row;
            updateInt(digest, safeRow.size());
            for (String cell : safeRow) {
                byte[] value = normalizeCell(cell).getBytes(StandardCharsets.UTF_8);
                updateInt(digest, value.length);
                digest.update(value);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String normalizeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
