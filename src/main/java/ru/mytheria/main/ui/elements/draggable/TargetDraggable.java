package ru.mytheria.main.ui.elements.draggable;

import com.google.common.base.Suppliers;
import lombok.Getter;
import lombok.experimental.NonFinal;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.draggable.Draggable;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.util.render.RenderEngine;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.module.render.TargetHUD;

import java.awt.*;
import java.util.function.Supplier;

public class TargetDraggable extends Draggable {
    private static final float BORDER_THICKNESS = -3.8f;

    private float animationValue;

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<TargetHUD> targetHudModule = Suppliers.memoize(
            () -> (TargetHUD) Mytheria.getInstance().getModuleManager().find(TargetHUD.class)
    );

    public TargetDraggable() {
        super(10f, 25f, 100f, 33f, () -> targetHudModule.get().getEnabled());
    }

    @NonFinal
    @Getter
    private Entity target = null;

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        LivingEntity renderTarget = mc.currentScreen instanceof ChatScreen ? mc.player : findNearestEntity(10);
        if (renderTarget == null) {
            renderTarget = mc.player;
        }

        target = renderTarget;

        String mode = interfaceModule.get().getMode().getValue();
        TargetHUD module = targetHudModule.get();
        float panelRadius = InterfaceStyle.radius(11f, 2.8f);
        float avatarRadius = InterfaceStyle.radius(7f, 2f);
        Color textColor = InterfaceStyle.hudPrimary(new Color(module.getTextColor().getColor(), true));
        Color accentPrimary = InterfaceStyle.hudPrimary(new Color(module.getTextColor().getColor(), true));
        Color accentSecondary = InterfaceStyle.hudSecondary(new Color(module.getTextColor().getColor(), true));
        Color backgroundColor = new Color(InterfaceStyle.hudPrimaryColorWithAlpha(module.getBackgroundColor().getColor()), true);
        boolean transparent = module.getTransparent().getEnabled();
        float width = 100f;
        float height = 33f;
        float renderScale = getSizeScale();
        LivingEntity finalRenderTarget = renderTarget;

        animationValue = MathHelper.lerp(0.3f, animationValue, MathHelper.clamp(finalRenderTarget.getHealth() / finalRenderTarget.getMaxHealth(), 0f, 1f));
        MatrixStack matrices = context.getMatrices();

        setWidth(width * renderScale);
        setHeight(height * renderScale);

        ru.mytheria.api.util.math.Math.scale(matrices, getX(), getY(), renderScale, () -> {
            if (!transparent) {
                if (mode.equals("Р‘Р»СЋСЂ")) {
                    RenderEngine.drawBlurRectangle(
                            matrices,
                            getX(), getY(), width, height,
                            new ColorState(backgroundColor),
                            new RadiusState(panelRadius),
                            1f,
                            55,
                            0.1f
                    );
                } else if (mode.equals("РЎС‚РµРєР»Рѕ")) {
                    QuickApi.blur()
                            .size(new SizeState(width, height))
                            .radius(new RadiusState(panelRadius))
                            .blurRadius(10)
                            .build()
                            .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

                    RenderEngine.drawLiquid(
                            matrices,
                            getX(), getY(), width, height,
                            new ColorState(backgroundColor),
                            new RadiusState(panelRadius),
                            1f,
                            9.0f,
                            25.0f,
                            1.2f
                    );
                }
            }

            drawBorder(context, width, height);

            QuickApi.text()
                    .font(QuickApi.inter())
                    .text(finalRenderTarget.getName().getString())
                    .color(textColor)
                    .size(8)
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), getX() + 30.5f, getY() + 6);

            if (finalRenderTarget instanceof AbstractClientPlayerEntity player) {
                RenderEngine.drawTexture(
                        matrices,
                        getX() + 6,
                        getY() + 6,
                        21,
                        21,
                        6f,
                        0.125f,
                        0.125f,
                        0.125f,
                        0.125f,
                        player.getSkinTextures().texture(),
                        Color.WHITE
                );
            } else {
                RenderEngine.drawRectangle(
                        matrices,
                        getX() + 6, getY() + 6, 21, 21,
                        new ColorState(new Color(35, 35, 35, transparent ? 90 : backgroundColor.getAlpha())),
                        new RadiusState(avatarRadius),
                        1f
                );

                QuickApi.text()
                        .font(QuickApi.inter())
                        .text("?")
                        .color(textColor)
                        .size(15)
                        .build()
                        .render(context.getMatrices().peek().getPositionMatrix(), getX() + 12, getY() + 7.5f);
            }

            float maxWidth = 60f;
            float barWidth = maxWidth * animationValue;

            RenderEngine.drawRectangle(
                    matrices,
                    getX() + 30.3f, getY() + 19, barWidth, 6f,
                    new ColorState(accentPrimary, accentSecondary, accentSecondary, accentPrimary),
                    new RadiusState(2),
                    1f
            );

            RenderEngine.drawRectangle(
                    matrices,
                    getX() + 30.3f, getY() + 19, maxWidth, 6.3f,
                    new ColorState(new Color(255, 255, 255, 34)),
                    new RadiusState(2),
                    1f
            );
        });
    }

    private void drawBorder(DrawContext context, float width, float height) {
        float radius = InterfaceStyle.radius(11f, 2.8f);
        Color primary = InterfaceStyle.hudPrimary(new Color(0xFFFFFFFF, true));
        Color secondary = InterfaceStyle.hudSecondary(new Color(255, 255, 255, 120));
        Color primarySoft = new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 170);
        Color secondarySoft = new Color(secondary.getRed(), secondary.getGreen(), secondary.getBlue(), 130);
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
                .thickness(BORDER_THICKNESS - 1.3f)
                .size(new SizeState(width, height))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());
    }

    private LivingEntity findNearestEntity(double radius) {
        LivingEntity nearest = null;
        double minDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living && entity != mc.player && !entity.isSpectator()) {
                double dist = mc.player.squaredDistanceTo(entity);
                if (dist < radius * radius && dist < minDist) {
                    nearest = living;
                    minDist = dist;
                }
            }
        }

        return nearest;
    }
}
