package ru.mytheria.api.client.configuration.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.Setting;
import ru.mytheria.api.module.settings.impl.*;


import java.util.List;
import java.util.Objects;

public class SettingConfiguration {

    public static JsonElement asElement(List<Setting> settings) {
        JsonArray settingsArray = new JsonArray();

        settings.forEach(e -> {
            JsonObject settingObject = new JsonObject();

            settingObject.addProperty("Setting-Name", e.getName().getString());

            if (e instanceof Collection collection) {
                settingObject.add("Collection-Settings", asElement(collection.getSettingLayers()));
            }

            if (e instanceof BooleanSetting booleanSetting) {
                settingObject.addProperty("Boolean-Enabled", booleanSetting.getEnabled());
            }

            else if (e instanceof SliderSetting sliderSetting) {
                settingObject.addProperty("Slider-Value", sliderSetting.getValue());
            }

            else if (e instanceof ModeSetting modeSetting) {
                settingObject.addProperty("Mode-Setting-Value", modeSetting.getValue() == null ? "N/A" : modeSetting.getValue());
            }

            else if (e instanceof ModeListSetting modeListSetting) {
                JsonArray valuesArray = new JsonArray();

                modeListSetting.getSelected().forEach(valuesArray::add);

                settingObject.add("Mode-List-Setting-Selected", valuesArray);
            }

            else if (e instanceof ColorSetting colorSetting) {
                settingObject.addProperty("Color-Setting-Value", colorSetting.getColor());
            }

            else if (e instanceof BindSetting bindSetting) {
                settingObject.addProperty("Bind-Setting-Selected", bindSetting.getSelected());
                settingObject.addProperty("Bind-Setting-Value", bindSetting.getKey());
            }

            settingsArray.add(settingObject);
        });

        return settingsArray;
    }

    public static void parseSetting( Module moduleLayer, JsonElement element) {
        JsonObject jsonObject = element.getAsJsonObject();

        Setting settingLayer = moduleLayer.getSettingLayers().stream()
                .filter(e -> e.getName().getString().equalsIgnoreCase(jsonObject.get("Setting-Name").getAsString()))
                .findFirst()
                .orElse(null);

        if (Objects.isNull(settingLayer)) return;

        if (settingLayer instanceof BooleanSetting booleanSetting) {
            booleanSetting.set(jsonObject.get("Boolean-Enabled").getAsBoolean());
        } else if (settingLayer instanceof SliderSetting sliderSetting) {
            sliderSetting.set(jsonObject.get("Slider-Value").getAsFloat());
        } else if (settingLayer instanceof BindSetting bindSetting) {
            bindSetting.setSelected(jsonObject.get("Bind-Setting-Selected").getAsBoolean());
            bindSetting.set(jsonObject.get("Bind-Setting-Value").getAsInt());
        } else if (settingLayer instanceof ModeSetting modeSetting) {
            modeSetting.set(Objects.equals(jsonObject.get("Mode-Setting-Value").getAsString(), "N/A") ? null : jsonObject.get("Mode-Setting-Value").getAsString());
        } else if (settingLayer instanceof ModeListSetting modeListSetting) {
            JsonArray jsonArray = jsonObject.getAsJsonArray("Mode-List-Setting-Selected");
            modeListSetting.getValues().forEach(e -> e.set(false));
            if (jsonArray != null) {
                jsonArray.asList().forEach(e -> {
                    var value = modeListSetting.get(e.getAsString());
                    if (value != null) {
                        value.set(true);
                    }
                });
            }
        } else if (settingLayer instanceof ColorSetting colorSetting && jsonObject.has("Color-Setting-Value")) {
            colorSetting.set(jsonObject.get("Color-Setting-Value").getAsInt());
        }
    }
}
