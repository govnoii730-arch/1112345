package ru.mytheria.main.module.render;

import com.google.gson.JsonObject;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import ru.mytheria.api.events.impl.EventPlayerTick;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DiscordRTC extends Module {
    private static final String CLIENT_ID = "1495366064048050256";
    private static final long UPDATE_INTERVAL_MS = 1_000L;

    private DiscordPipeClient discordClient;
    private long startedAtEpochSeconds;
    private long lastUpdateAt;

    public DiscordRTC() {
        super(Text.of("DiscordRTC"), null, Category.MOVEMENT);
    }

    @Override
    public void activate() {
        super.activate();
        startedAtEpochSeconds = System.currentTimeMillis() / 1000L;
        lastUpdateAt = 0L;
        discordClient = new DiscordPipeClient(CLIENT_ID);
        discordClient.connect();
        updatePresence(true);
    }

    @Override
    public void deactivate() {
        if (discordClient != null) {
            discordClient.close();
            discordClient = null;
        }
        super.deactivate();
    }

    @EventHandler
    public void onTick(EventPlayerTick event) {
        if (!Boolean.TRUE.equals(getEnabled()) || discordClient == null) {
            return;
        }

        updatePresence(false);
    }

    private void updatePresence(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastUpdateAt < UPDATE_INTERVAL_MS) {
            return;
        }

        if (!discordClient.isConnected() && !discordClient.connect()) {
            return;
        }

        long elapsedSeconds = java.lang.Math.max(0L, now / 1000L - startedAtEpochSeconds);
        String details = "В игре " + formatElapsed(elapsedSeconds);
        String state = resolveState();
        if (discordClient.setActivity(details, state, startedAtEpochSeconds)) {
            lastUpdateAt = now;
        }
    }

    private String formatElapsed(long elapsedSeconds) {
        long hours = elapsedSeconds / 3600L;
        long minutes = (elapsedSeconds % 3600L) / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String resolveState() {
        if (mc == null || mc.player == null || mc.world == null) {
            return "В меню";
        }

        ServerInfo server = mc.getCurrentServerEntry();
        if (server != null && server.address != null && !server.address.isBlank()) {
            return trim("Играет: " + server.address, 120);
        }

        return "Одиночный мир";
    }

    private String trim(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static final class DiscordPipeClient {
        private final String clientId;
        private RandomAccessFile pipe;
        private boolean connected;

        private DiscordPipeClient(String clientId) {
            this.clientId = clientId;
        }

        private boolean isConnected() {
            return connected && pipe != null;
        }

        private boolean connect() {
            if (isConnected()) {
                return true;
            }

            close();
            for (int i = 0; i < 10; i++) {
                String path = "\\\\.\\pipe\\discord-ipc-" + i;
                try {
                    pipe = new RandomAccessFile(path, "rw");
                    sendHandshake();
                    connected = true;
                    return true;
                } catch (IOException ignored) {
                    close();
                }
            }

            return false;
        }

        private boolean setActivity(String details, String state, long startEpochSeconds) {
            if (!isConnected()) {
                return false;
            }

            try {
                JsonObject root = new JsonObject();
                root.addProperty("cmd", "SET_ACTIVITY");
                root.addProperty("nonce", UUID.randomUUID().toString());

                JsonObject args = new JsonObject();
                args.addProperty("pid", ProcessHandle.current().pid());

                JsonObject activity = new JsonObject();
                activity.addProperty("details", details);
                activity.addProperty("state", state);

                JsonObject timestamps = new JsonObject();
                timestamps.addProperty("start", startEpochSeconds);
                activity.add("timestamps", timestamps);

                args.add("activity", activity);
                root.add("args", args);
                sendFrame(1, root.toString());
                return true;
            } catch (IOException ignored) {
                close();
                return false;
            }
        }

        private void sendHandshake() throws IOException {
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", clientId);
            sendFrame(0, handshake.toString());
        }

        private void sendFrame(int opcode, String json) throws IOException {
            if (pipe == null) {
                throw new IOException("Discord pipe is closed");
            }

            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(opcode);
            header.putInt(payload.length);

            pipe.write(header.array());
            pipe.write(payload);
        }

        private void close() {
            connected = false;
            if (pipe != null) {
                try {
                    pipe.close();
                } catch (IOException ignored) {
                }
                pipe = null;
            }
        }
    }
}
