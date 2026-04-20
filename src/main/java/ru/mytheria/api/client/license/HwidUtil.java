package ru.mytheria.api.client.license;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class HwidUtil {
    private HwidUtil() {
    }

    public static String buildHwid() {
        List<String> parts = new ArrayList<>();
        add(parts, System.getProperty("os.name"));
        add(parts, System.getProperty("os.arch"));
        add(parts, System.getProperty("os.version"));
        add(parts, System.getProperty("user.name"));
        add(parts, System.getenv("PROCESSOR_IDENTIFIER"));
        add(parts, System.getenv("COMPUTERNAME"));
        add(parts, String.valueOf(Runtime.getRuntime().availableProcessors()));
        add(parts, firstMacAddress());
        return sha256Hex(String.join("|", parts));
    }

    private static String firstMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "";
            }

            List<NetworkInterface> list = Collections.list(interfaces);
            for (NetworkInterface network : list) {
                if (network.isLoopback() || network.isVirtual() || !network.isUp()) {
                    continue;
                }

                byte[] mac = network.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    continue;
                }

                StringBuilder builder = new StringBuilder();
                for (byte b : mac) {
                    builder.append(String.format("%02X", b));
                }
                return builder.toString();
            }
        } catch (Exception ignored) {
        }

        return "";
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

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }
}
