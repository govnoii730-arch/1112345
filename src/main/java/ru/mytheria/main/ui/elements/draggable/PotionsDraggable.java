package ru.mytheria.main.ui.elements.draggable;

import com.google.common.base.Suppliers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.draggable.Draggable;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.util.render.RenderEngine;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.module.render.Potions;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class PotionsDraggable extends Draggable {
    private static final float BORDER_THICKNESS = -3.4f;
    private static final String TITLE = "\u0417\u0435\u043b\u044c\u044f";
    private static final String NO_EFFECTS = "\u041d\u0435\u0442 \u044d\u0444\u0444\u0435\u043a\u0442\u043e\u0432";
    private static final String INFINITE = "\u0411\u0435\u0441\u043a\u043e\u043d\u0435\u0447\u043d\u043e";

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<Potions> potionsModule = Suppliers.memoize(
            () -> (Potions) Mytheria.getInstance().getModuleManager().find(Potions.class)
    );

    public PotionsDraggable() {
        super(120f, 25f, 90f, 32f, () -> potionsModule.get().getEnabled() && !collectEffects(potionsModule.get()).isEmpty());
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        Interface interfaceSettings = interfaceModule.get();
        Potions module = potionsModule.get();
        List<StatusEffectInstance> effects = collectEffects(module);
        boolean preview = mc.currentScreen instanceof ChatScreen;
        ThemePalette palette = ThemePalette.from(module);

        if (effects.isEmpty() && !preview) {
            return;
        }

        float headerHeight = 15f;
        float rowHeight = 10f;
        float effectIconSize = 8f;
        float titleX = getX() + 6f;
        float contentWidth = QuickApi.sf_semi().getWidth(TITLE, 8.5f) + 12f;

        for (StatusEffectInstance effect : effects) {
            String name = getPotionName(effect);
            String duration = formatDuration(effect);
            float rowWidth = effectIconSize + 4f
                    + QuickApi.inter().getWidth(name, 7)
                    + QuickApi.inter().getWidth(duration, 7)
                    + 20f;
            contentWidth = Math.max(contentWidth, rowWidth);
        }

        if (effects.isEmpty()) {
            contentWidth = Math.max(contentWidth, QuickApi.inter().getWidth(NO_EFFECTS, 7) + 10f);
        }

        float width = Math.max(92f, contentWidth + 18f);
        float height = headerHeight + 8f + (effects.isEmpty() ? rowHeight : effects.size() * rowHeight);
        float renderScale = getSizeScale();

        setWidth(width * renderScale);
        setHeight(height * renderScale);

        ru.mytheria.api.util.math.Math.scale(context.getMatrices(), getX(), getY(), renderScale, () -> {
            drawBackground(context, width, height, interfaceSettings, palette);

            QuickApi.text()
                    .font(QuickApi.sf_semi())
                    .text(TITLE)
                    .color(palette.headerText)
                    .size(8.5f)
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), titleX, getY() + 2);

            float y = getY() + headerHeight + 1.0f;
            if (effects.isEmpty()) {
                QuickApi.text()
                        .font(QuickApi.inter())
                        .text(NO_EFFECTS)
                        .color(palette.secondaryText)
                        .size(7)
                        .build()
                        .render(context.getMatrices().peek().getPositionMatrix(), getX() + 6, y + 3);
                return;
            }

            for (StatusEffectInstance effect : effects) {
                drawEffectIcon(context, effect, getX() + 6f, y + 1f, effectIconSize);

                String name = getPotionName(effect);
                String duration = formatDuration(effect);

                QuickApi.text()
                        .font(QuickApi.inter())
                        .text(name)
                        .color(palette.primaryText)
                        .size(7)
                        .build()
                        .render(context.getMatrices().peek().getPositionMatrix(), getX() + 17f, y + 1);

                QuickApi.text()
                        .font(QuickApi.inter())
                        .text(duration)
                        .color(palette.secondaryText)
                        .size(7)
                        .build()
                        .render(context.getMatrices().peek().getPositionMatrix(), getX() + width - 6 - QuickApi.inter().getWidth(duration, 7), y + 1);

                y += rowHeight;
            }
        });
    }

    private void drawEffectIcon(DrawContext context, StatusEffectInstance effect, float x, float y, float size) {
        Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(effect.getEffectType());
        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, (int) x, (int) y, (int) size, (int) size);
    }

    private void drawBackground(DrawContext context, float width, float height, Interface interfaceSettings, ThemePalette palette) {
        boolean glassMode = isGlassMode(interfaceSettings.getMode().getValue());
        boolean transparent = potionsModule.get().getTransparent().getEnabled();
        float radius = InterfaceStyle.radius(7f, 2.2f);

        if (!transparent) {
            if (glassMode) {
                QuickApi.blur()
                        .size(new SizeState(width, height))
                        .radius(new RadiusState(radius))
                        .blurRadius(10)
                        .build()
                        .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

                RenderEngine.drawLiquid(
                        context.getMatrices(),
                        getX(), getY(), width, height,
                        new ColorState(palette.backgroundFill),
                        new RadiusState(radius),
                        1f,
                        9.0f,
                        25.0f,
                        1.2f
                );
            } else {
                RenderEngine.drawBlurRectangle(
                        context.getMatrices(),
                        getX(), getY(), width, height,
                        new ColorState(palette.backgroundFill),
                        new RadiusState(radius),
                        1f,
                        55,
                        0.1f
                );
            }
        }

        drawBorder(context, width, height);
    }

    private void drawBorder(DrawContext context, float width, float height) {
        float radius = InterfaceStyle.radius(7f, 2.2f);
        Color primary = InterfaceStyle.hudPrimary(new Color(0xFFFFFFFF, true));
        Color secondary = InterfaceStyle.hudSecondary(new Color(255, 255, 255, 110));
        Color primarySoft = new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 170);
        Color secondarySoft = new Color(secondary.getRed(), secondary.getGreen(), secondary.getBlue(), 120);
        QuickApi.border()
                .radius(new RadiusState(radius))
                .color(new ColorState(primary, secondary, secondary, primary))
                .thickness(BORDER_THICKNESS)
                .size(new SizeState(width, height))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

        QuickApi.border()
                .radius(new RadiusState(radius))
                .color(new ColorState(primarySoft, secondarySoft, secondarySoft, primarySoft))
                .thickness(BORDER_THICKNESS - 1.2f)
                .size(new SizeState(width, height))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());
    }

    private static List<StatusEffectInstance> collectEffects(Potions module) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return List.of();
        }

        return client.player.getStatusEffects().stream()
                .filter(effect -> module.getShowNegativePotions().getEnabled()
                        || effect.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL)
                .sorted(Comparator.comparingInt(StatusEffectInstance::getDuration).reversed())
                .toList();
    }

    private String getPotionName(StatusEffectInstance effect) {
        String base = translatePotion(effect.getEffectType().value().getTranslationKey());
        int amplifier = effect.getAmplifier();
        if (amplifier <= 0) {
            return base;
        }

        return base + " " + toRoman(amplifier + 1);
    }

    private String formatDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) {
            return INFINITE;
        }

        int totalSeconds = Math.max(0, effect.getDuration() / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private String translatePotion(String translationKey) {
        return switch (translationKey) {
            case "effect.minecraft.speed" -> "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c";
            case "effect.minecraft.slowness" -> "\u0417\u0430\u043c\u0435\u0434\u043b\u0435\u043d\u0438\u0435";
            case "effect.minecraft.haste" -> "\u0421\u043f\u0435\u0448\u043a\u0430";
            case "effect.minecraft.mining_fatigue" -> "\u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c";
            case "effect.minecraft.strength" -> "\u0421\u0438\u043b\u0430";
            case "effect.minecraft.instant_health" -> "\u041c\u0433\u043d\u043e\u0432\u0435\u043d\u043d\u043e\u0435 \u043b\u0435\u0447\u0435\u043d\u0438\u0435";
            case "effect.minecraft.instant_damage" -> "\u041c\u0433\u043d\u043e\u0432\u0435\u043d\u043d\u044b\u0439 \u0443\u0440\u043e\u043d";
            case "effect.minecraft.jump_boost" -> "\u041f\u0440\u044b\u0433\u0443\u0447\u0435\u0441\u0442\u044c";
            case "effect.minecraft.nausea" -> "\u0422\u043e\u0448\u043d\u043e\u0442\u0430";
            case "effect.minecraft.regeneration" -> "\u0420\u0435\u0433\u0435\u043d\u0435\u0440\u0430\u0446\u0438\u044f";
            case "effect.minecraft.resistance" -> "\u0421\u043e\u043f\u0440\u043e\u0442\u0438\u0432\u043b\u0435\u043d\u0438\u0435";
            case "effect.minecraft.fire_resistance" -> "\u041e\u0433\u043d\u0435\u0441\u0442\u043e\u0439\u043a\u043e\u0441\u0442\u044c";
            case "effect.minecraft.water_breathing" -> "\u041f\u043e\u0434\u0432\u043e\u0434\u043d\u043e\u0435 \u0434\u044b\u0445\u0430\u043d\u0438\u0435";
            case "effect.minecraft.invisibility" -> "\u041d\u0435\u0432\u0438\u0434\u0438\u043c\u043e\u0441\u0442\u044c";
            case "effect.minecraft.blindness" -> "\u0421\u043b\u0435\u043f\u043e\u0442\u0430";
            case "effect.minecraft.night_vision" -> "\u041d\u043e\u0447\u043d\u043e\u0435 \u0437\u0440\u0435\u043d\u0438\u0435";
            case "effect.minecraft.hunger" -> "\u0413\u043e\u043b\u043e\u0434";
            case "effect.minecraft.weakness" -> "\u0421\u043b\u0430\u0431\u043e\u0441\u0442\u044c";
            case "effect.minecraft.poison" -> "\u041e\u0442\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435";
            case "effect.minecraft.wither" -> "\u0418\u0441\u0441\u0443\u0448\u0435\u043d\u0438\u0435";
            case "effect.minecraft.health_boost" -> "\u0414\u043e\u043f. \u0437\u0434\u043e\u0440\u043e\u0432\u044c\u0435";
            case "effect.minecraft.absorption" -> "\u041f\u043e\u0433\u043b\u043e\u0449\u0435\u043d\u0438\u0435";
            case "effect.minecraft.saturation" -> "\u041d\u0430\u0441\u044b\u0449\u0435\u043d\u0438\u0435";
            case "effect.minecraft.glowing" -> "\u0421\u0432\u0435\u0447\u0435\u043d\u0438\u0435";
            case "effect.minecraft.levitation" -> "\u041b\u0435\u0432\u0438\u0442\u0430\u0446\u0438\u044f";
            case "effect.minecraft.luck" -> "\u0423\u0434\u0430\u0447\u0430";
            case "effect.minecraft.unluck" -> "\u041d\u0435\u0443\u0434\u0430\u0447\u0430";
            case "effect.minecraft.slow_falling" -> "\u041c\u0435\u0434\u043b\u0435\u043d\u043d\u043e\u0435 \u043f\u0430\u0434\u0435\u043d\u0438\u0435";
            case "effect.minecraft.conduit_power" -> "\u0421\u0438\u043b\u0430 \u043f\u0440\u043e\u0432\u043e\u0434\u043d\u0438\u043a\u0430";
            case "effect.minecraft.dolphins_grace" -> "\u0413\u0440\u0430\u0446\u0438\u044f \u0434\u0435\u043b\u044c\u0444\u0438\u043d\u0430";
            case "effect.minecraft.bad_omen" -> "\u041f\u043b\u043e\u0445\u043e\u0435 \u0437\u043d\u0430\u043c\u0435\u043d\u0438\u0435";
            case "effect.minecraft.hero_of_the_village" -> "\u0413\u0435\u0440\u043e\u0439 \u0434\u0435\u0440\u0435\u0432\u043d\u0438";
            case "effect.minecraft.darkness" -> "\u0422\u0435\u043c\u043d\u043e\u0442\u0430";
            case "effect.minecraft.trial_omen" -> "\u0417\u043d\u0430\u043c\u0435\u043d\u0438\u0435 \u0438\u0441\u043f\u044b\u0442\u0430\u043d\u0438\u044f";
            case "effect.minecraft.raid_omen" -> "\u0417\u043d\u0430\u043c\u0435\u043d\u0438\u0435 \u0440\u0435\u0439\u0434\u0430";
            case "effect.minecraft.wind_charged" -> "\u0417\u0430\u0440\u044f\u0434 \u0432\u0435\u0442\u0440\u0430";
            case "effect.minecraft.weaving" -> "\u041f\u043b\u0435\u0442\u0435\u043d\u0438\u0435";
            case "effect.minecraft.oozing" -> "\u0421\u043b\u0438\u0437\u044c";
            case "effect.minecraft.infested" -> "\u0417\u0430\u0440\u0430\u0436\u0435\u043d\u0438\u0435";
            default -> {
                int dot = translationKey.lastIndexOf('.');
                yield dot >= 0 ? translationKey.substring(dot + 1) : translationKey;
            }
        };
    }

    private String toRoman(int value) {
        return switch (value) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    private boolean isGlassMode(String mode) {
        return "\u0421\u0442\u0435\u043a\u043b\u043e".equals(mode)
                || "\u0420\u040e\u0421\u201a\u0420\u0412\u00b5\u0420\u0421\u201d\u0420\u0412\u00bb\u0420\u0421\u2022".equals(mode)
                || "\u0420\u045e\u0421\u201a\u0420\u00b5\u0420\u0454\u0420\u00bb\u0420\u0451".equals(mode);
    }

    private record ThemePalette(
            Color headerText,
            Color primaryText,
            Color secondaryText,
            Color backgroundFill
    ) {
        private static ThemePalette from(Potions module) {
            Color textColor = InterfaceStyle.hudPrimary(new Color(module.getTextColor().getColor(), true));
            Color secondary = InterfaceStyle.hudSecondary(new Color(module.getTextColor().getColor(), true));
            Color backgroundColor = new Color(InterfaceStyle.hudPrimaryColorWithAlpha(module.getBackgroundColor().getColor()), true);
            return new ThemePalette(
                    textColor,
                    textColor,
                    secondary,
                    backgroundColor
            );
        }
    }
}
