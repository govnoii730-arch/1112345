package ru.mytheria.api.client.configuration;

import java.util.List;

public class ConfigurationService implements ConfigurationApi {

    final Configuration configurationController = new Configuration();

    @Override
    public void save(String name) {
        configurationController.save(normalizeName(name));
    }

    @Override
    public void load(String name) {
        configurationController.load(normalizeName(name));
    }

    @Override
    public void remove(String name) {
        configurationController.remove(normalizeName(name));
    }

    @Override
    public List<String> asList() {
        return configurationController.asList();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "autosave.json";
        }

        return name.toLowerCase().endsWith(".json") ? name : name + ".json";
    }
}
