package ru.mytheria.main.module.render;

import ru.mytheria.Mytheria;

import java.awt.Color;
import java.util.List;

public final class InterfaceStyle {
    private InterfaceStyle() {
    }

    public static final String PALETTE_GOLD = "Золото";
    public static final String PALETTE_BERRY = "Малина";
    public static final String PALETTE_LIME = "Зелёный";
    public static final String PALETTE_RED = "Красный";
    public static final String PALETTE_BLUE = "Синий";
    public static final String PALETTE_WHITE = "Белый";

    public record PalettePreset(String id, int fromColor, int toColor) {
    }

    public static final List<PalettePreset> HUD_PRESETS = List.of(
            new PalettePreset(PALETTE_GOLD, 0xFFFFB300, 0xFFFFEA00),
            new PalettePreset(PALETTE_BERRY, 0xFFFF2D7A, 0xFF8A2BFF),
            new PalettePreset(PALETTE_LIME, 0xFF35CD68, 0xFFB6FF30),
            new PalettePreset(PALETTE_RED, 0xFFFF3131, 0xFF730019),
            new PalettePreset(PALETTE_BLUE, 0xFF2B55FF, 0xFF2CEBFF),
            new PalettePreset(PALETTE_WHITE, 0xFFFFFFFF, 0xFF9E9E9E)
    );

    public static float radius(float rounded, float square) {
        Interface settings = getInterfaceSettings();
        if (settings == null || settings.getRounding() == null || settings.getRounding().getValue() == null) {
            return rounded;
        }

        float percent = settings.getRounding().getValue();
        float t = java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, percent / 100.0f));
        return square + (rounded - square) * t;
    }

    public static int hudPrimaryColor(int fallbackColor) {
        PalettePreset preset = resolveCurrentPreset();
        return preset == null ? fallbackColor : preset.fromColor();
    }

    public static int hudSecondaryColor(int fallbackColor) {
        PalettePreset preset = resolveCurrentPreset();
        return preset == null ? fallbackColor : preset.toColor();
    }

    public static int hudPrimaryColorWithAlpha(int fallbackColor) {
        return withSourceAlpha(hudPrimaryColor(fallbackColor), fallbackColor);
    }

    public static int hudSecondaryColorWithAlpha(int fallbackColor) {
        return withSourceAlpha(hudSecondaryColor(fallbackColor), fallbackColor);
    }

    public static Color hudPrimary(Color fallbackColor) {
        return new Color(hudPrimaryColorWithAlpha(fallbackColor.getRGB()), true);
    }

    public static Color hudSecondary(Color fallbackColor) {
        return new Color(hudSecondaryColorWithAlpha(fallbackColor.getRGB()), true);
    }

    public static String currentHudPalette() {
        Interface settings = getInterfaceSettings();
        if (settings == null || settings.getHudPalette() == null) {
            return null;
        }
        return normalizePaletteId(settings.getHudPalette().getValue());
    }

    public static boolean isHudPaletteSelected(String id) {
        String normalizedId = normalizePaletteId(id);
        String current = currentHudPalette();
        return normalizedId != null && normalizedId.equalsIgnoreCase(current);
    }

    public static PalettePreset palettePreview(String id) {
        String normalizedId = normalizePaletteId(id);
        if (normalizedId == null) {
            return HUD_PRESETS.stream()
                    .filter(preset -> preset.id().equalsIgnoreCase(PALETTE_WHITE))
                    .findFirst()
                    .orElse(HUD_PRESETS.get(0));
        }

        return HUD_PRESETS.stream()
                .filter(preset -> preset.id().equalsIgnoreCase(normalizedId))
                .findFirst()
                .orElse(HUD_PRESETS.get(0));
    }

    public static void setHudPalette(String id) {
        Interface settings = getInterfaceSettings();
        if (settings == null || settings.getHudPalette() == null || id == null || id.isBlank()) {
            return;
        }

        String normalizedId = normalizePaletteId(id);
        String current = normalizePaletteId(settings.getHudPalette().getValue());
        if (current != null && current.equalsIgnoreCase(normalizedId)) {
            settings.getHudPalette().set((String) null);
            return;
        }

        settings.getHudPalette().set(normalizedId);
    }

    private static int withSourceAlpha(int themedColor, int sourceColor) {
        int alpha = (sourceColor >>> 24) & 0xFF;
        return (alpha << 24) | (themedColor & 0x00FFFFFF);
    }

    private static PalettePreset resolveCurrentPreset() {
        String selected = currentHudPalette();
        if (selected == null || selected.isBlank()) {
            return null;
        }
        return palettePreview(selected);
    }

    private static String normalizePaletteId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return switch (id) {
            case "Р—РѕР»РѕС‚Рѕ" -> PALETTE_GOLD;
            case "РњР°Р»РёРЅР°" -> PALETTE_BERRY;
            case "Р—РµР»РµРЅС‹Р№", "Р—РµР»С‘РЅС‹Р№" -> PALETTE_LIME;
            case "РљСЂР°СЃРЅС‹Р№" -> PALETTE_RED;
            case "РЎРёРЅРёР№" -> PALETTE_BLUE;
            case "Р‘РµР»С‹Р№" -> PALETTE_WHITE;
            default -> id;
        };
    }

    private static Interface getInterfaceSettings() {
        Mytheria mytheria = Mytheria.getInstance();
        if (mytheria == null || mytheria.getModuleManager() == null) {
            return null;
        }

        return (Interface) mytheria.getModuleManager().find(Interface.class);
    }
}
