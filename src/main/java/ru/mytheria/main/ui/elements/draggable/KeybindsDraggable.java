package ru.mytheria.main.ui.elements.draggable;

import com.google.common.base.Suppliers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.draggable.Draggable;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.util.keyboard.KeyBoardUtil;
import ru.mytheria.api.util.render.RenderEngine;
import ru.mytheria.api.util.render.ScissorUtil;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.main.module.render.Binds;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;

import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class KeybindsDraggable extends Draggable {
    private static final float BORDER_THICKNESS = -3.4f;
    private static final String TITLE = "Бинды";

    private static final Supplier<List<Module>> modules = () -> Mytheria.getInstance().getModuleManager().getModuleLayers().stream()
            .filter(module -> !module.getKey().equals(-1))
            .toList();

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<Binds> bindsModule = Suppliers.memoize(
            () -> (Binds) Mytheria.getInstance().getModuleManager().find(Binds.class)
    );

    public KeybindsDraggable() {
        super(10f, 25f, 75f, 26f, () -> bindsModule.get().getEnabled() && !modules.get().isEmpty());
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        List<Module> visibleModules = modules.get();
        Binds module = bindsModule.get();
        boolean glassMode = isGlassMode(interfaceModule.get().getMode().getValue());
        float renderScale = getSizeScale();
        float contentWidth = 60f;

        for (Module currentModule : visibleModules) {
            String keyName = KeyBoardUtil.translate(currentModule.getKey());
            float rowWidth = 8f
                    + QuickApi.sf_bold().getWidth(currentModule.getModuleName().getString(), 7)
                    + QuickApi.sf_bold().getWidth(keyName, 7)
                    + 20f;
            contentWidth = java.lang.Math.max(contentWidth, rowWidth);
        }

        float width = contentWidth + 15f;
        float height = 26f + (visibleModules.size() * 9f);
        float radius = InterfaceStyle.radius(7f, 2.2f);
        Color textColor = InterfaceStyle.hudPrimary(new Color(module.getTextColor().getColor(), true));
        Color backgroundColor = new Color(InterfaceStyle.hudPrimaryColorWithAlpha(module.getBackgroundColor().getColor()), true);

        setWidth(width * renderScale);
        setHeight(height * renderScale);

        ru.mytheria.api.util.math.Math.scale(context.getMatrices(), getX(), getY(), renderScale, () -> {
            if (!module.getTransparent().getEnabled()) {
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
                } else {
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
            }

            drawBorder(context, width, height);

            QuickApi.text()
                    .font(QuickApi.sf_semi())
                    .text(TITLE)
                    .color(textColor)
                    .size(8.5f)
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), getX() + 5.5f, getY() + 2f);

            AtomicReference<Float> offset = new AtomicReference<>(1f);
            ScissorUtil.push(getX(), getY(), width, height);
            visibleModules.forEach(currentModule -> {
                QuickApi.text()
                        .size(7)
                        .font(QuickApi.sf_bold())
                        .text(currentModule.getModuleName().getString())
                        .thickness(.1f)
                        .color(textColor)
                        .build()
                        .render(
                                context.getMatrices().peek().getPositionMatrix(),
                                getX() + 2,
                                getY() + 19f + offset.get()
                        );

                String keyName = KeyBoardUtil.translate(currentModule.getKey());
                QuickApi.text()
                        .size(7)
                        .font(QuickApi.sf_bold())
                        .text(keyName)
                        .thickness(.1f)
                        .color(textColor)
                        .build()
                        .render(
                                context.getMatrices().peek().getPositionMatrix(),
                                getX() + width - 8f - QuickApi.sf_bold().getWidth(keyName, 7),
                                getY() + 19f + offset.get()
                        );

                offset.set(offset.get() + 9f);
            });
            ScissorUtil.pop();
        });
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
