package ru.mytheria.api.client.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class HwidTransportCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] SECRET_PARTS = new String[]{
            "cloud", "visuals", "-", "hwid", "-", "transport", "-", "2026"
    };

    private HwidTransportCipher() {
    }

    public static String randomNonce() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String encrypt(String hwid, String nonce) {
        if (hwid == null || hwid.isBlank()) {
            return "";
        }
        byte[] src = hwid.getBytes(StandardCharsets.UTF_8);
        byte[] mask = mask(secret() + "|" + nonce, src.length);
        byte[] out = new byte[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = (byte) (src[i] ^ mask[i]);
        }
        return Base64.getEncoder().encodeToString(out);
    }

    public static String sign(String hwid, String nonce, String context) {
        String payload = secret() + "|" + nonce + "|" + safe(hwid) + "|" + safe(context);
        return sha256Hex(payload);
    }

    private static byte[] mask(String seed, int length) {
        byte[] seedBytes = seed.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[length];
        int offset = 0;
        int counter = 0;
        while (offset < length) {
            byte[] digest = sha256(seedBytes, counter);
            int remain = Math.min(digest.length, length - offset);
            System.arraycopy(digest, 0, out, offset, remain);
            offset += remain;
            counter++;
        }
        return out;
    }

    private static byte[] sha256(byte[] seedBytes, int counter) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(seedBytes);
            digest.update(new byte[]{
                    (byte) ((counter >>> 24) & 0xFF),
                    (byte) ((counter >>> 16) & 0xFF),
                    (byte) ((counter >>> 8) & 0xFF),
                    (byte) (counter & 0xFF)
            });
            return digest.digest();
        } catch (Exception ignored) {
            return seedBytes;
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String secret() {
        StringBuilder sb = new StringBuilder();
        for (String part : SECRET_PARTS) {
            sb.append(part);
        }
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
