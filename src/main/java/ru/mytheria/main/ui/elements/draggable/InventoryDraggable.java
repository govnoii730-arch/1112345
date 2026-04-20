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
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.module.render.Inventory;

import java.awt.*;
import java.util.function.Supplier;

public class InventoryDraggable extends Draggable {
    private static final float BORDER_THICKNESS = -3.4f;
    private static final String TITLE = "Инвентарь";

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<Inventory> inventoryModule = Suppliers.memoize(
            () -> (Inventory) Mytheria.getInstance().getModuleManager().find(Inventory.class)
    );

    public InventoryDraggable() {
        super(10f, 122f, 176f, 74f, () -> inventoryModule.get().getEnabled());
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        Inventory module = inventoryModule.get();
        boolean preview = mc.currentScreen instanceof ChatScreen;
        boolean glassMode = isGlassMode(interfaceModule.get().getMode().getValue());
        float renderScale = getSizeScale();
        float width = 176f;
        float height = 74f;
        float slotSize = 18f;
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

            if (mc.player == null && !preview) {
                return;
            }

            float gridX = getX() + 7f;
            float gridY = getY() + 15f;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slot = 9 + row * 9 + col;
                    drawInventorySlot(context, slot, gridX + col * slotSize, gridY + row * slotSize, preview);
                }
            }
        });
    }

    private void drawInventorySlot(DrawContext context, int slot, float x, float y, boolean preview) {
        QuickApi.rectangle()
                .size(new SizeState(16f, 16f))
                .radius(new RadiusState(2f))
                .color(new ColorState(0x22FFFFFF))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        if (mc.player == null) {
            return;
        }

        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty() && !preview) {
            return;
        }

        context.drawItem(stack, (int) x, (int) y);
        context.drawStackOverlay(mc.textRenderer, stack, (int) x, (int) y);
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
