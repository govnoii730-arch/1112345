package ru.mytheria.api.client.license;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ProtectionGuard {
    private static final long MAX_ONLINE_STALE_MS = 20L * 60L * 1000L;
    private static final String[] ALLOWED_HOSTS = new String[]{
            hostPart("150", "251", "152", "244"),
            "cloudvisuals.xyz",
            "www.cloudvisuals.xyz",
            "127.0.0.1",
            "localhost"
    };
    private static final String[] BLOCKED_JVM_FLAGS = new String[]{
            "jdwp", "xdebug", "javaagent", "agentpath", "-noverify"
    };
    private static final String[] BLOCKED_PROCESS_PARTS = new String[]{
            "x64dbg", "ida", "ollydbg", "cheatengine", "dnspy", "ilspy", "fiddler", "wireshark", "frida"
    };
    private static final String[] SALT_PARTS = new String[]{
            "cl", "oud", "vis", "ual", "s", "-", "guard", "-", "2026"
    };
    private static final String[] DEV_KEY_PARTS = new String[]{
            "cloud", "-", "visuals", "-", "dev", "-", "local", "-", "2026"
    };

    private ProtectionGuard() {
    }

    public static boolean performStartupChecks() {
        if (isDevelopmentMode()) {
            return true;
        }
        return !hasBlockedJvmFlags() && !hasBlockedProcess();
    }

    public static boolean isServerAllowed(String serverUrl) {
        if (isDevelopmentMode()) {
            return true;
        }

        try {
            URI uri = URI.create(serverUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            for (String allowed : ALLOWED_HOSTS) {
                if (normalizedHost.equals(allowed)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isLicenseSessionFresh(long lastCheckAt, long graceUntil) {
        long now = System.currentTimeMillis();
        if (graceUntil > now) {
            return true;
        }
        return now - lastCheckAt <= MAX_ONLINE_STALE_MS;
    }

    public static String buildSessionSeal(String licenseKey, String hwid, long graceUntil, long lastCheckAt, long licenseExpiresAtMs) {
        String payload = safe(licenseKey) + "|" + safe(hwid) + "|" + graceUntil + "|" + lastCheckAt + "|" + licenseExpiresAtMs + "|" + buildSalt();
        return sha256(payload);
    }

    public static boolean isSessionSealValid(String sessionSeal, String licenseKey, String hwid, long graceUntil, long lastCheckAt, long licenseExpiresAtMs) {
        if (sessionSeal == null || sessionSeal.isBlank()) {
            return false;
        }
        String expected = buildSessionSeal(licenseKey, hwid, graceUntil, lastCheckAt, licenseExpiresAtMs);
        return MessageDigest.isEqual(sessionSeal.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateDevelopmentKey(String hwid, long expiresAtMs) {
        long normalizedExpiry = Math.max(System.currentTimeMillis() + 60_000L, expiresAtMs);
        String expiryPart = Long.toUnsignedString(normalizedExpiry, 36).toUpperCase(Locale.ROOT);
        String signature = buildDevelopmentSignature(safe(hwid), normalizedExpiry).substring(0, 16).toUpperCase(Locale.ROOT);
        return "DEV-" + expiryPart + "-" + signature;
    }

    public static boolean isDevelopmentKeyValid(String key, String hwid) {
        long expiresAt = getDevelopmentKeyExpiresAtMs(key);
        if (expiresAt <= System.currentTimeMillis()) {
            return false;
        }

        String expected = generateDevelopmentKey(safe(hwid), expiresAt);
        return expected.equalsIgnoreCase(safe(key).trim());
    }

    public static long getDevelopmentKeyExpiresAtMs(String key) {
        if (key == null) {
            return 0L;
        }

        String[] parts = key.trim().split("-");
        if (parts.length != 3 || !"DEV".equalsIgnoreCase(parts[0])) {
            return 0L;
        }

        try {
            return Long.parseUnsignedLong(parts[1], 36);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static String encodeKeyForStorage(String rawKey, String hwid) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        byte[] mask = sha256Bytes(safe(hwid) + "|" + buildSalt());
        byte[] out = new byte[keyBytes.length];
        for (int i = 0; i < keyBytes.length; i++) {
            out[i] = (byte) (keyBytes[i] ^ mask[i % mask.length]);
        }
        return Base64.getEncoder().encodeToString(out);
    }

    public static String decodeKeyFromStorage(String encodedValue, String hwid) {
        if (encodedValue == null || encodedValue.isBlank()) {
            return "";
        }
        try {
            byte[] src = Base64.getDecoder().decode(encodedValue);
            byte[] mask = sha256Bytes(safe(hwid) + "|" + buildSalt());
            byte[] out = new byte[src.length];
            for (int i = 0; i < src.length; i++) {
                out[i] = (byte) (src[i] ^ mask[i % mask.length]);
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean hasBlockedJvmFlags() {
        try {
            List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String argument : arguments) {
                String normalized = argument.toLowerCase(Locale.ROOT);
                for (String blocked : BLOCKED_JVM_FLAGS) {
                    if (normalized.contains(blocked)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean hasBlockedProcess() {
        try {
            return ProcessHandle.allProcesses()
                    .map(ProcessHandle::info)
                    .map(ProcessHandle.Info::command)
                    .filter(Objects::nonNull)
                    .flatMap(java.util.Optional::stream)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(command -> {
                        for (String blocked : BLOCKED_PROCESS_PARTS) {
                            if (command.contains(blocked)) {
                                return true;
                            }
                        }
                        return false;
                    });
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String buildSalt() {
        StringBuilder builder = new StringBuilder();
        for (String part : SALT_PARTS) {
            builder.append(part);
        }
        return builder.toString();
    }

    private static String hostPart(String a, String b, String c, String d) {
        return a + "." + b + "." + c + "." + d;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String buildDevelopmentSignature(String hwid, long expiresAtMs) {
        return sha256(safe(hwid) + "|" + expiresAtMs + "|" + buildDevSecret());
    }

    private static String buildDevSecret() {
        StringBuilder builder = new StringBuilder();
        for (String part : DEV_KEY_PARTS) {
            builder.append(part);
        }
        return builder.toString();
    }

    private static String sha256(String value) {
        byte[] hash = sha256Bytes(value);
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private static byte[] sha256Bytes(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static boolean isDevelopmentMode() {
        String fabricDev = System.getProperty("fabric.development", "false");
        String loomDev = System.getProperty("loom.development", "false");
        return "true".equalsIgnoreCase(fabricDev) || "true".equalsIgnoreCase(loomDev);
    }
}
