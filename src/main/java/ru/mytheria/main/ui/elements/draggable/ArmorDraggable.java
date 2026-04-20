package ru.mytheria.main.ui.elements.draggable;

import com.google.common.base.Suppliers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.draggable.Draggable;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.util.render.RenderEngine;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.main.module.render.Armor;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ArmorDraggable extends Draggable {
    private static final float BORDER_THICKNESS = -3.4f;
    private static final String TITLE = "Броня";

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<Armor> armorModule = Suppliers.memoize(
            () -> (Armor) Mytheria.getInstance().getModuleManager().find(Armor.class)
    );

    public ArmorDraggable() {
        super(120f, 122f, 88f, 34f, () -> armorModule.get().getEnabled() && (mc.currentScreen instanceof ChatScreen || hasAnyArmorNow()));
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        Armor module = armorModule.get();
        boolean preview = mc.currentScreen instanceof ChatScreen;
        List<ItemStack> armorStacks = collectArmor(preview);
        if (armorStacks.isEmpty() && !preview) {
            return;
        }

        boolean glassMode = isGlassMode(interfaceModule.get().getMode().getValue());
        float renderScale = getSizeScale();
        float width = 88f;
        float height = 34f;
        float startX = getX() + 7f;
        Color textColor = InterfaceStyle.hudPrimary(new Color(module.getTextColor().getColor(), true));
        Color backgroundColor = new Color(InterfaceStyle.hudPrimaryColorWithAlpha(module.getBackgroundColor().getColor()), true);

        setWidth(width * renderScale);
        setHeight(height * renderScale);

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

            for (int i = 0; i < armorStacks.size(); i++) {
                ItemStack stack = armorStacks.get(i);
                int x = (int) (startX + i * 18f);
                int y = (int) (getY() + 15f);
                context.drawItem(stack, x, y);
                context.drawStackOverlay(mc.textRenderer, stack, x, y);
            }
        });
    }

    private List<ItemStack> collectArmor(boolean preview) {
        if (mc.player == null) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : mc.player.getInventory().armor) {
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }

        if (result.isEmpty() && preview) {
            result.addAll(mc.player.getInventory().armor);
        }

        return result;
    }

    private static boolean hasAnyArmorNow() {
        if (mc.player == null) {
            return false;
        }

        for (ItemStack stack : mc.player.getInventory().armor) {
            if (!stack.isEmpty()) {
                return true;
            }
        }

        return false;
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

    private boolean isGlassMode(String mode) {
        return "Стекло".equals(mode)
                || "РЎС‚РµРєР»Рѕ".equals(mode)
                || "РЎС‚РµРєР»Рё".equals(mode);
    }
}
