package ru.mytheria.api.client.configuration;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.configuration.impl.ModuleConfiguration;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Configuration implements ConfigurationApi {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void save(String name) {
        File file = new File(mc.runDirectory.getAbsolutePath() + "/mytheria/configs/" + name);

        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        JsonArray mainArray = new JsonArray();
        JsonObject descriptionObject = new JsonObject();

        mainArray.add(descriptionObject);

        Mytheria.getInstance().getModuleManager().forEach(module -> mainArray.add(ModuleConfiguration.asElement(module)));

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(mainArray, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void load(String name) {
        File file = new File(mc.runDirectory.getAbsolutePath() + "/mytheria/configs/" + name);
        if (!file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(file)) {
            ModuleConfiguration.parseJson(gson, reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void remove(String name) {
        File file = new File(mc.runDirectory.getAbsolutePath() + "/mytheria/configs/" + name);

        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Override
    public List<String> asList() {
        File dir = new File(mc.runDirectory.getAbsolutePath() + "/mytheria/configs/");
        if (!dir.exists() || !dir.isDirectory()) {
            return List.of();
        }

        File[] files = dir.listFiles((file, fileName) -> fileName.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
                name = name.substring(0, name.length() - 5);
            }
            result.add(name);
        }

        result.sort(String::compareToIgnoreCase);
        return result;
    }
}
