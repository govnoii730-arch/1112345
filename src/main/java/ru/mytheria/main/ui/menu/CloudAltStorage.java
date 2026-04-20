package ru.mytheria.main.ui.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import ru.mytheria.mixin.accessor.MinecraftClientAccessor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CloudAltStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<CloudAltEntry> ENTRIES = new ArrayList<>();
    private static boolean loaded;

    private CloudAltStorage() {
    }

    public static List<CloudAltEntry> entries() {
        ensureLoaded();
        return ENTRIES;
    }

    public static void add(String name) {
        ensureLoaded();
        if (name == null) {
            return;
        }

        String trimmed = name.trim();
        if (trimmed.length() < 3) {
            return;
        }

        boolean exists = ENTRIES.stream().anyMatch(entry -> entry.name().equalsIgnoreCase(trimmed));
        if (!exists) {
            ENTRIES.add(0, new CloudAltEntry(trimmed));
            save();
        }
    }

    public static void remove(CloudAltEntry entry) {
        ensureLoaded();
        if (entry == null) {
            return;
        }

        ENTRIES.removeIf(current -> current.name().equalsIgnoreCase(entry.name()));
        save();
    }

    public static void use(CloudAltEntry entry) {
        ensureLoaded();
        if (entry == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        String name = entry.name().trim();
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        Session session = new Session(
                name,
                uuid,
                "",
                Optional.empty(),
                Optional.empty(),
                Session.AccountType.LEGACY
        );

        ((MinecraftClientAccessor) client).mytheria$setSession(session);
        save();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        loaded = true;
        File file = getFile();
        if (!file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(file)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                return;
            }

            JsonObject object = root.getAsJsonObject();
            if (object.has("alts") && object.get("alts").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("alts")) {
                    String value = element.getAsString();
                    if (value != null && !value.isBlank()) {
                        ENTRIES.add(new CloudAltEntry(value));
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        File file = getFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        JsonObject root = new JsonObject();
        root.addProperty("last", MinecraftClient.getInstance().getSession().getUsername());

        JsonArray alts = new JsonArray();
        for (CloudAltEntry entry : ENTRIES) {
            alts.add(entry.name());
        }
        root.add("alts", alts);

        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(root, writer);
        } catch (IOException ignored) {
        }
    }

    private static File getFile() {
        return new File(MinecraftClient.getInstance().runDirectory, "mytheria/alts.json");
    }
}
