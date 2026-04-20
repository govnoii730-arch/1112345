package ru.mytheria.main.ui.elements.draggable;

import com.google.common.base.Suppliers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.draggable.Draggable;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.util.color.ColorUtil;
import ru.mytheria.api.util.render.RenderEngine;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.main.module.render.Cooldowns;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class CooldownsDraggable extends Draggable {
    private static final String TITLE = "\u0417\u0430\u0434\u0435\u0440\u0436\u043a\u0438";
    private static final String EMPTY = "\u041d\u0435\u0442 \u0430\u043a\u0442\u0438\u0432\u043d\u044b\u0445 \u043a\u0434";
    private static final float BORDER_THICKNESS = -3.4f;

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<Cooldowns> cooldownsModule = Suppliers.memoize(
            () -> (Cooldowns) Mytheria.getInstance().getModuleManager().find(Cooldowns.class)
    );

    private static Field entriesField;
    private static Field endTickField;
    private static Field startTickField;

    public CooldownsDraggable() {
        super(10f, 72f, 95f, 30f, () -> cooldownsModule.get().getEnabled() && !collectLines(cooldownsModule.get(), false).isEmpty());
        resolveReflection();
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        Cooldowns module = cooldownsModule.get();
        boolean preview = mc.currentScreen instanceof ChatScreen;
        List<CooldownLine> lines = collectLines(module, preview);

        if (lines.isEmpty() && !preview) {
            return;
        }

        float renderScale = getSizeScale();
        float titleWidth = QuickApi.sf_semi().getWidth(TITLE, 8.5f);
        float contentWidth = titleWidth + 12f;

        for (CooldownLine line : lines) {
            float rowWidth = 6f
                    + QuickApi.sf_bold().getWidth(line.label(), 7f)
                    + QuickApi.sf_bold().getWidth(line.time(), 7f)
                    + 18f;
            contentWidth = Math.max(contentWidth, rowWidth);
        }

        float width = Math.max(100f, contentWidth + 14f);
        float height = 17f + (lines.isEmpty() ? 12f : lines.size() * 10f) + 5f;

        setWidth(width * renderScale);
        setHeight(height * renderScale);

        Color textColor = InterfaceStyle.hudPrimary(new Color(module.getTextColor().getColor(), true));
        Color backgroundColor = new Color(InterfaceStyle.hudPrimaryColorWithAlpha(module.getBackgroundColor().getColor()), true);
        boolean glassMode = isGlassMode(interfaceModule.get().getMode().getValue());

        ru.mytheria.api.util.math.Math.scale(context.getMatrices(), getX(), getY(), renderScale, () -> {
            drawBackground(context, width, height, backgroundColor, glassMode, module.getTransparent().getEnabled());
            drawBorder(context, width, height);

            QuickApi.text()
                    .font(QuickApi.sf_semi())
                    .text(TITLE)
                    .color(textColor)
                    .size(8.5f)
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), getX() + 5.5f, getY() + 2f);

            float y = getY() + 16f;
            for (CooldownLine line : lines) {
                QuickApi.text()
                        .font(QuickApi.sf_bold())
                        .text(line.label())
                        .color(textColor)
                        .size(7f)
                        .build()
                        .render(context.getMatrices().peek().getPositionMatrix(), getX() + 5.5f, y);

                QuickApi.text()
                        .font(QuickApi.sf_bold())
                        .text(line.time())
                        .color(textColor)
                        .size(7f)
                        .build()
                        .render(
                                context.getMatrices().peek().getPositionMatrix(),
                                getX() + width - 6f - QuickApi.sf_bold().getWidth(line.time(), 7f),
                                y
                        );

                y += 10f;
            }
        });
    }

    private void drawBackground(DrawContext context, float width, float height, Color backgroundColor, boolean glassMode, boolean transparent) {
        float radius = InterfaceStyle.radius(7f, 2.2f);
        if (transparent) {
            return;
        }

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
                    new ColorState(backgroundColor),
                    new RadiusState(radius),
                    1f,
                    9.0f,
                    25.0f,
                    1.2f
            );
            return;
        }

        RenderEngine.drawBlurRectangle(
                context.getMatrices(),
                getX(), getY(), width, height,
                new ColorState(backgroundColor),
                new RadiusState(radius),
                1f,
                55,
                0.1f
        );
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

    private static List<CooldownLine> collectLines(Cooldowns module, boolean preview) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return preview ? List.of(new CooldownLine(EMPTY, "0.0s", 0.0f)) : List.of();
        }

        ItemCooldownManager cooldownManager = client.player.getItemCooldownManager();
        Set<String> checkedGroups = new HashSet<>();
        List<CooldownLine> lines = new ArrayList<>();

        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            appendLine(module, cooldownManager, checkedGroups, lines, stack);
        }

        appendLine(module, cooldownManager, checkedGroups, lines, client.player.getOffHandStack());

        if (lines.isEmpty() && preview) {
            return List.of(new CooldownLine(EMPTY, "0.0s", 0.0f));
        }

        lines.sort(Comparator.comparingDouble(CooldownLine::seconds).reversed());
        return lines;
    }

    private static void appendLine(Cooldowns module,
                                   ItemCooldownManager cooldownManager,
                                   Set<String> checkedGroups,
                                   List<CooldownLine> lines,
                                   ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        Item item = stack.getItem();
        if (module.getIgnoreWeapons().getEnabled() && isWeapon(item)) {
            return;
        }

        String groupKey = cooldownManager.getGroup(stack).toString();
        if (!checkedGroups.add(groupKey) || !cooldownManager.isCoolingDown(stack)) {
            return;
        }

        float seconds = getRemainingSeconds(stack, cooldownManager);
        String label = translateItem(stack);
        lines.add(new CooldownLine(label, String.format(Locale.US, "%.1fs", seconds), seconds));
    }

    private static float getRemainingSeconds(ItemStack stack, ItemCooldownManager manager) {
        resolveReflection();
        if (entriesField == null) {
            return Math.max(0.0f, manager.getCooldownProgress(stack, 0.0f) * 2.0f);
        }

        try {
            Map<?, ?> entries = (Map<?, ?>) entriesField.get(manager);
            Object entry = entries.get(manager.getGroup(stack));
            if (entry != null && endTickField != null && startTickField != null) {
                int endTick = endTickField.getInt(entry);
                int startTick = startTickField.getInt(entry);
                int durationTicks = Math.max(0, endTick - startTick);
                float progress = manager.getCooldownProgress(stack, 0.0f);
                return Math.max(0.0f, (durationTicks * progress) / 20.0f);
            }
        } catch (IllegalAccessException ignored) {
        }

        return Math.max(0.0f, manager.getCooldownProgress(stack, 0.0f) * 2.0f);
    }

    private static void resolveReflection() {
        if (entriesField != null) {
            return;
        }

        try {
            for (Field field : ItemCooldownManager.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    entriesField = field;
                    break;
                }
            }

            if (entriesField == null) {
                return;
            }

            Class<?> entryClass = null;
            for (Class<?> innerClass : ItemCooldownManager.class.getDeclaredClasses()) {
                if (innerClass.getSimpleName().toLowerCase(Locale.ROOT).contains("entry")) {
                    entryClass = innerClass;
                    break;
                }
            }

            if (entryClass == null) {
                return;
            }

            endTickField = entryClass.getDeclaredField("endTick");
            startTickField = entryClass.getDeclaredField("startTick");
            endTickField.setAccessible(true);
            startTickField.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
            entriesField = null;
            endTickField = null;
            startTickField = null;
        }
    }

    private static boolean isWeapon(Item item) {
        return item == Items.MACE
                || item == Items.TRIDENT
                || item == Items.CROSSBOW
                || item == Items.BOW;
    }

    private static String translateItem(ItemStack stack) {
        String translationKey = stack.getItem().getTranslationKey();
        return switch (translationKey) {
            case "item.minecraft.ender_pearl" -> "\u0416\u0435\u043c\u0447\u0443\u0433 \u044d\u043d\u0434\u0435\u0440\u0430";
            case "item.minecraft.chorus_fruit" -> "\u0425\u043e\u0440\u0443\u0441";
            case "item.minecraft.wind_charge" -> "\u0412\u0435\u0442\u0440\u044f\u043d\u043e\u0439 \u0437\u0430\u0440\u044f\u0434";
            case "item.minecraft.mace" -> "\u0411\u0443\u043b\u0430\u0432\u0430";
            case "item.minecraft.trident" -> "\u0422\u0440\u0435\u0437\u0443\u0431\u0435\u0446";
            case "item.minecraft.crossbow" -> "\u0410\u0440\u0431\u0430\u043b\u0435\u0442";
            case "item.minecraft.bow" -> "\u041b\u0443\u043a";
            case "item.minecraft.shield" -> "\u0429\u0438\u0442";
            case "item.minecraft.goat_horn" -> "\u041a\u043e\u0437\u044c\u0438\u0439 \u0440\u043e\u0433";
            default -> {
                String vanillaName = stack.getName().getString();
                yield vanillaName == null || vanillaName.isBlank() ? "\u041f\u0440\u0435\u0434\u043c\u0435\u0442" : vanillaName;
            }
        };
    }

    private boolean isGlassMode(String mode) {
        return "\u0421\u0442\u0435\u043a\u043b\u043e".equals(mode)
                || "\u0420\u040e\u0421\u201a\u0420\u0412\u00b5\u0420\u0421\u201d\u0420\u0412\u00bb\u0420\u0421\u2022".equals(mode)
                || "\u0420\u045e\u0421\u201a\u0420\u00b5\u0420\u0454\u0420\u00bb\u0420\u0451".equals(mode);
    }

    private record CooldownLine(String label, String time, float seconds) {
    }
}
