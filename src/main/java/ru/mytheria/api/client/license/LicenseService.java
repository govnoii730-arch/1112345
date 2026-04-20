package ru.mytheria.api.client.license;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import ru.mytheria.Mytheria;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LicenseService {
    private static final long OFFLINE_GRACE_MS = 72L * 60L * 60L * 1000L;
    private static final long DEV_KEY_DEFAULT_TTL_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final String DEFAULT_SERVER_URL = "http://150.251.152.244:8080";
    private static final String BOOTSTRAP_PATH = "/api/v1/client/bootstrap";
    private static final String VERIFY_PATH = "/api/v1/license/verify";
    private static final String DEV_MODE_PROPERTY = "cloudvisuals.dev.mode";
    private static final String DEV_MODE_ENV = "CLOUDVISUALS_DEV_MODE";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cloudvisuals-license");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean checking = new AtomicBoolean(false);

    private volatile String serverUrl = DEFAULT_SERVER_URL;
    private volatile String licenseKey = "";
    private volatile String hwid = HwidUtil.buildHwid();
    private volatile boolean licensed;
    private volatile String statusText = "License not verified";
    private volatile String lastReason = "init";
    private volatile long graceUntil;
    private volatile long lastCheckAt;
    private volatile long licenseExpiresAtMs;
    private volatile String sessionSeal = "";
    private volatile boolean bootstrapSent;
    private volatile boolean startupIntegrityPassed = true;

    private File storageFile;

    public void init() {
        storageFile = resolveStorageFile();
        hwid = HwidUtil.buildHwid();
        loadState();

        startupIntegrityPassed = ProtectionGuard.performStartupChecks();
        if (!startupIntegrityPassed) {
            setUnlicensed("protection", "Protection check failed");
            saveState();
            return;
        }

        verifyAsync();
    }

    public void verifyAsync() {
        if (!checking.compareAndSet(false, true)) {
            return;
        }

        executor.execute(() -> {
            try {
                verifyNow();
            } finally {
                checking.set(false);
            }
        });
    }

    public boolean isLicensed() {
        return canUsePremiumFeatures();
    }

    public boolean canUsePremiumFeatures() {
        long now = System.currentTimeMillis();

        if (!startupIntegrityPassed) {
            return false;
        }
        if (licenseExpiresAtMs > 0L && now > licenseExpiresAtMs) {
            graceUntil = 0L;
            sessionSeal = "";
            setUnlicensed("expired_local", "Subscription expired");
            saveState();
            return false;
        }
        if (!licensed) {
            return false;
        }
        if (!ProtectionGuard.isSessionSealValid(sessionSeal, licenseKey, hwid, graceUntil, lastCheckAt, licenseExpiresAtMs)) {
            setUnlicensed("seal_mismatch", "Session signature mismatch");
            saveState();
            return false;
        }
        if (!ProtectionGuard.isLicenseSessionFresh(lastCheckAt, graceUntil)) {
            verifyAsync();
            if (graceUntil <= now) {
                setUnlicensed("stale_check", "License heartbeat expired");
                saveState();
                return false;
            }
        }

        if (now - lastCheckAt > 5L * 60L * 1000L) {
            verifyAsync();
        }

        return true;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastReason() {
        return lastReason;
    }

    public String getLicenseKeyMasked() {
        if (licenseKey == null || licenseKey.isBlank()) {
            return "not set";
        }

        String key = licenseKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public String getHwid() {
        return hwid;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public boolean isDevelopmentLicenseModeEnabled() {
        String propertyValue = System.getProperty(DEV_MODE_PROPERTY, "false");
        String envValue = System.getenv(DEV_MODE_ENV);
        return "true".equalsIgnoreCase(propertyValue) || "1".equals(envValue) || "true".equalsIgnoreCase(envValue);
    }

    public String generateDevelopmentKey() {
        hwid = HwidUtil.buildHwid();
        long expiresAt = System.currentTimeMillis() + DEV_KEY_DEFAULT_TTL_MS;
        return ProtectionGuard.generateDevelopmentKey(hwid, expiresAt);
    }

    public void setLicenseKey(String key) {
        licenseKey = key == null ? "" : key.trim();
        graceUntil = 0L;
        licenseExpiresAtMs = 0L;
        sessionSeal = "";
        licensed = false;
        saveState();
        verifyAsync();
    }

    public void setServerUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        String normalized = normalizeServerUrl(url);
        if (!ProtectionGuard.isServerAllowed(normalized)) {
            setUnlicensed("server_forbidden", "License server is not allowed");
            saveState();
            return;
        }

        serverUrl = normalized;
        saveState();
        verifyAsync();
    }

    private void verifyNow() {
        lastCheckAt = System.currentTimeMillis();
        hwid = HwidUtil.buildHwid();

        startupIntegrityPassed = ProtectionGuard.performStartupChecks();
        if (!startupIntegrityPassed) {
            setUnlicensed("protection", "Protection check failed");
            saveState();
            applyRestrictionIfNeeded();
            return;
        }

        String normalizedServer = normalizeServerUrl(serverUrl);
        if (!ProtectionGuard.isServerAllowed(normalizedServer)) {
            sessionSeal = "";
            setUnlicensed("server_forbidden", "License server is not allowed");
            saveState();
            applyRestrictionIfNeeded();
            return;
        }

        sendBootstrapIfNeeded(normalizedServer);

        if (licenseKey.isBlank()) {
            sessionSeal = "";
            setUnlicensed("no_key", "License key is missing");
            saveState();
            applyRestrictionIfNeeded();
            return;
        }

        if (isDevelopmentLicenseModeEnabled()) {
            if (applyDevelopmentKeyIfValid()) {
                return;
            }
            applyRestrictionIfNeeded();
            return;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(normalizedServer + VERIFY_PATH);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(4500);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JsonObject request = new JsonObject();
            request.addProperty("key", licenseKey);
            String nonce = HwidTransportCipher.randomNonce();
            request.addProperty("hwid", "");
            request.addProperty("hwid_enc", HwidTransportCipher.encrypt(hwid, nonce));
            request.addProperty("hwid_nonce", nonce);
            request.addProperty("hwid_sig", HwidTransportCipher.sign(hwid, nonce, "verify:" + licenseKey));
            request.addProperty("client_version", "CloudVisuals");

            try (OutputStream output = connection.getOutputStream()) {
                output.write(gson.toJson(request).getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            JsonObject json = parseObject(response);

            boolean serverLicensed = getBoolean(json, "licensed", false);
            String reason = getString(json, "reason", "unknown");
            String message = getString(json, "message", "");
            long expiresAtSec = getLong(json, "expires_at", 0L);

            if (serverLicensed) {
                licensed = true;
                lastReason = reason;
                statusText = message.isBlank() ? "License is active" : message;
                licenseExpiresAtMs = expiresAtSec > 0L ? expiresAtSec * 1000L : 0L;
                graceUntil = System.currentTimeMillis() + OFFLINE_GRACE_MS;
                sessionSeal = ProtectionGuard.buildSessionSeal(licenseKey, hwid, graceUntil, lastCheckAt, licenseExpiresAtMs);
                saveState();
                return;
            }

            licenseExpiresAtMs = expiresAtSec > 0L ? expiresAtSec * 1000L : 0L;
            sessionSeal = "";
            setUnlicensed(reason, message.isBlank() ? "License rejected" : message);
            saveState();
        } catch (Exception exception) {
            long now = System.currentTimeMillis();
            boolean validOfflineSession = ProtectionGuard.isSessionSealValid(sessionSeal, licenseKey, hwid, graceUntil, lastCheckAt, licenseExpiresAtMs);

            if (graceUntil > now && validOfflineSession && (licenseExpiresAtMs <= 0L || now <= licenseExpiresAtMs)) {
                licensed = true;
                lastReason = "offline_grace";
                long leftHours = Math.max(1L, (graceUntil - now) / (60L * 60L * 1000L));
                statusText = "Server unreachable, grace " + leftHours + "h";
            } else {
                sessionSeal = "";
                setUnlicensed("network_error", "Cannot connect to license server");
            }
            saveState();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            applyRestrictionIfNeeded();
        }
    }

    private boolean applyDevelopmentKeyIfValid() {
        if (!ProtectionGuard.isDevelopmentKeyValid(licenseKey, hwid)) {
            sessionSeal = "";
            licenseExpiresAtMs = 0L;
            graceUntil = 0L;
            setUnlicensed("dev_key_invalid", "Development key is invalid or expired");
            saveState();
            return false;
        }

        licensed = true;
        lastReason = "dev_key";
        licenseExpiresAtMs = ProtectionGuard.getDevelopmentKeyExpiresAtMs(licenseKey);
        graceUntil = licenseExpiresAtMs;
        statusText = "Development license active";
        sessionSeal = ProtectionGuard.buildSessionSeal(licenseKey, hwid, graceUntil, lastCheckAt, licenseExpiresAtMs);
        saveState();
        return true;
    }

    private void setUnlicensed(String reason, String status) {
        licensed = false;
        lastReason = reason;
        statusText = status;
    }

    private void sendBootstrapIfNeeded(String normalizedServer) {
        if (bootstrapSent) {
            return;
        }

        HttpURLConnection bootstrapConnection = null;
        try {
            URL url = new URL(normalizedServer + BOOTSTRAP_PATH);
            bootstrapConnection = (HttpURLConnection) url.openConnection();
            bootstrapConnection.setRequestMethod("POST");
            bootstrapConnection.setConnectTimeout(2500);
            bootstrapConnection.setReadTimeout(2500);
            bootstrapConnection.setDoOutput(true);
            bootstrapConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            String nonce = HwidTransportCipher.randomNonce();
            JsonObject request = new JsonObject();
            request.addProperty("hwid_enc", HwidTransportCipher.encrypt(hwid, nonce));
            request.addProperty("hwid_nonce", nonce);
            request.addProperty("hwid_sig", HwidTransportCipher.sign(hwid, nonce, "bootstrap"));
            request.addProperty("client_version", "CloudVisuals");

            try (OutputStream output = bootstrapConnection.getOutputStream()) {
                output.write(gson.toJson(request).getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = bootstrapConnection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                bootstrapSent = true;
                saveState();
            }
        } catch (Exception ignored) {
        } finally {
            if (bootstrapConnection != null) {
                bootstrapConnection.disconnect();
            }
        }
    }

    private void applyRestrictionIfNeeded() {
        if (licensed || Mytheria.getInstance() == null || Mytheria.getInstance().getModuleManager() == null) {
            return;
        }

        Mytheria.getInstance().getModuleManager().enforceLicenseState();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.of("License is not active. Modules are blocked."));
        }
    }

    private String normalizeServerUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? DEFAULT_SERVER_URL : normalized;
    }

    private File resolveStorageFile() {
        MinecraftClient client = MinecraftClient.getInstance();
        File runDir = client != null ? client.runDirectory : new File(".");
        File dir = new File(runDir, "mytheria");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, "license.json");
    }

    private void loadState() {
        if (storageFile == null || !storageFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile, StandardCharsets.UTF_8))) {
            JsonObject json = parseObject(readAll(reader));
            serverUrl = getString(json, "serverUrl", DEFAULT_SERVER_URL);
            hwid = getString(json, "hwid", hwid);
            graceUntil = getLong(json, "graceUntil", 0L);
            lastReason = getString(json, "lastReason", "loaded");
            statusText = getString(json, "statusText", "loaded");
            lastCheckAt = getLong(json, "lastCheckAt", 0L);
            sessionSeal = getString(json, "sessionSeal", "");
            licenseExpiresAtMs = getLong(json, "licenseExpiresAtMs", 0L);
            bootstrapSent = getBoolean(json, "bootstrapSent", false);

            String encodedKey = getString(json, "licenseKeyEnc", "");
            if (!encodedKey.isBlank()) {
                licenseKey = ProtectionGuard.decodeKeyFromStorage(encodedKey, hwid);
            } else {
                licenseKey = getString(json, "licenseKey", "");
            }

            boolean hasValidSeal = ProtectionGuard.isSessionSealValid(sessionSeal, licenseKey, hwid, graceUntil, lastCheckAt, licenseExpiresAtMs);
            licensed = hasValidSeal
                    && ProtectionGuard.isLicenseSessionFresh(lastCheckAt, graceUntil)
                    && (licenseExpiresAtMs <= 0L || System.currentTimeMillis() <= licenseExpiresAtMs);
            if (!hasValidSeal) {
                sessionSeal = "";
            }
        } catch (Exception ignored) {
        }
    }

    private void saveState() {
        if (storageFile == null) {
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("serverUrl", serverUrl);
        json.addProperty("licenseKeyEnc", ProtectionGuard.encodeKeyForStorage(licenseKey, hwid));
        json.addProperty("hwid", hwid);
        json.addProperty("graceUntil", graceUntil);
        json.addProperty("lastReason", lastReason);
        json.addProperty("statusText", statusText);
        json.addProperty("lastCheckAt", lastCheckAt);
        json.addProperty("sessionSeal", sessionSeal);
        json.addProperty("licenseExpiresAtMs", licenseExpiresAtMs);
        json.addProperty("bootstrapSent", bootstrapSent);

        try (FileWriter writer = new FileWriter(storageFile, StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        } catch (Exception ignored) {
        }
    }

    private JsonObject parseObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JsonObject();
        }

        JsonElement element = gson.fromJson(raw, JsonElement.class);
        if (element == null || !element.isJsonObject()) {
            return new JsonObject();
        }
        return element.getAsJsonObject();
    }

    private String readAll(InputStream stream) {
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return readAll(reader);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readAll(BufferedReader reader) {
        StringBuilder builder = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } catch (Exception ignored) {
        }
        return builder.toString();
    }

    private boolean getBoolean(JsonObject json, String key, boolean fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        try {
            return json.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long getLong(JsonObject json, String key, long fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        try {
            return json.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String getString(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        try {
            String value = json.get(key).getAsString();
            return value == null ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
