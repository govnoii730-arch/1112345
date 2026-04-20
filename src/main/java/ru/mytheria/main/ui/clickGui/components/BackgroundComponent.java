package ru.mytheria.main.ui.clickGui.components;


import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.util.animations.Animation;
import ru.mytheria.api.util.animations.Direction;
import ru.mytheria.api.util.animations.implement.DecelerateAnimation;
import ru.mytheria.api.util.color.ColorUtil;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.ui.clickGui.Component;
import ru.mytheria.api.util.math.Math;

import java.awt.*;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BackgroundComponent extends Component {
    static final float PALETTE_SWATCH_WIDTH = 8f;
    static final float PALETTE_SWATCH_HEIGHT = 23f;
    static final float PALETTE_SWATCH_GAP = 3f;
    static final float PALETTE_SWATCH_RADIUS = 2.4f;
    static final float PALETTE_LEFT_GAP = 10f;

    Category category;

    Animation animation = new DecelerateAnimation()
            .setMs(250)
            .setValue(1);

    @Override
    public BackgroundComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        float radius = InterfaceStyle.radius(10f, 3f);

        if (Math.isHover(mouseX, mouseY, getX(), getY(), getWidth(), getHeight()))
            animation.setDirection(Direction.FORWARDS);
        else animation.setDirection(Direction.BACKWARDS);


        QuickApi.blur()
                .size(new SizeState(getWidth(), getHeight()))
                .radius(new RadiusState(radius))
                .blurRadius(16)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

        QuickApi.rectangle()
                .radius(new RadiusState(radius))
                .color(new ColorState(ColorUtil.applyOpacity(0xFF000000, 40)))
                .size(new SizeState(getWidth(), getHeight()))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

        Color primary = InterfaceStyle.hudPrimary(new Color(0xFFFFFFFF, true));
        Color secondary = InterfaceStyle.hudSecondary(new Color(0xFFFFFFFF, true));
        Color primarySoft = new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 180);
        Color secondarySoft = new Color(secondary.getRed(), secondary.getGreen(), secondary.getBlue(), 130);

        QuickApi.border()
                .radius(new RadiusState(radius))
                .color(new ColorState(primarySoft, secondarySoft, secondarySoft, primarySoft))
                .thickness(-2.8f)
                .size(new SizeState(getWidth(), getHeight()))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

        QuickApi.border()
                .radius(new RadiusState(radius))
                .color(new ColorState(
                        ColorUtil.applyOpacity(primarySoft.getRGB(), 70),
                        ColorUtil.applyOpacity(secondarySoft.getRGB(), 70),
                        ColorUtil.applyOpacity(secondarySoft.getRGB(), 70),
                        ColorUtil.applyOpacity(primarySoft.getRGB(), 70)
                ))
                .thickness(-4.1f)
                .size(new SizeState(getWidth(), getHeight()))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());


        float titleWidth = QuickApi.inter().getWidth(category.getName(), 10);
        QuickApi.text()
                .size(10)
                .font(QuickApi.inter())
                .text(category.getName())
                .color(primary.getRGB())
                .build()
                .render(
                        context.getMatrices().peek().getPositionMatrix(),
                        getX() + getWidth() * 0.5f - titleWidth * 0.5f,
                        getY() + 10
                );

        if (category == Category.RENDER) {
            renderHudPalette(context);
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || category != Category.RENDER) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        float x = getPaletteX();
        float startY = getPaletteStartY();

        for (int i = 0; i < InterfaceStyle.HUD_PRESETS.size(); i++) {
            InterfaceStyle.PalettePreset preset = InterfaceStyle.HUD_PRESETS.get(i);
            float y = startY + i * (PALETTE_SWATCH_HEIGHT + PALETTE_SWATCH_GAP);

            if (Math.isHover(mouseX, mouseY, x, y, PALETTE_SWATCH_WIDTH, PALETTE_SWATCH_HEIGHT)) {
                InterfaceStyle.setHudPalette(preset.id());
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderHudPalette(DrawContext context) {
        float x = getPaletteX();
        float startY = getPaletteStartY();

        for (int i = 0; i < InterfaceStyle.HUD_PRESETS.size(); i++) {
            InterfaceStyle.PalettePreset basePreset = InterfaceStyle.HUD_PRESETS.get(i);
            InterfaceStyle.PalettePreset preset = InterfaceStyle.palettePreview(basePreset.id());
            float y = startY + i * (PALETTE_SWATCH_HEIGHT + PALETTE_SWATCH_GAP);
            boolean selected = InterfaceStyle.isHudPaletteSelected(preset.id());
            drawPaletteSwatch(context, x, y, preset.fromColor(), preset.toColor(), selected);
        }
    }

    private float getPaletteTotalHeight() {
        int size = InterfaceStyle.HUD_PRESETS.size();
        return size * PALETTE_SWATCH_HEIGHT + (size - 1) * PALETTE_SWATCH_GAP;
    }

    private float getPaletteX() {
        return getX() - PALETTE_LEFT_GAP - PALETTE_SWATCH_WIDTH;
    }

    private float getPaletteStartY() {
        return getY() + (getHeight() - getPaletteTotalHeight()) * 0.5f;
    }

    private void drawPaletteSwatch(DrawContext context, float x, float y, int leftColor, int rightColor, boolean selected) {
        QuickApi.rectangle()
                .size(new SizeState(PALETTE_SWATCH_WIDTH, PALETTE_SWATCH_HEIGHT))
                .radius(new RadiusState(PALETTE_SWATCH_RADIUS))
                .color(new ColorState(leftColor, leftColor, rightColor, rightColor))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        if (!selected) {
            QuickApi.border()
                    .radius(new RadiusState(PALETTE_SWATCH_RADIUS))
                    .color(new ColorState(
                            ColorUtil.applyOpacity(leftColor, 60),
                            ColorUtil.applyOpacity(rightColor, 60),
                            ColorUtil.applyOpacity(rightColor, 60),
                            ColorUtil.applyOpacity(leftColor, 60)
                    ))
                    .thickness(-1.6f)
                    .size(new SizeState(PALETTE_SWATCH_WIDTH, PALETTE_SWATCH_HEIGHT))
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), x, y);
            return;
        }

        QuickApi.border()
                .radius(new RadiusState(PALETTE_SWATCH_RADIUS))
                .color(new ColorState(
                        ColorUtil.applyOpacity(leftColor, 100),
                        ColorUtil.applyOpacity(rightColor, 100),
                        ColorUtil.applyOpacity(rightColor, 100),
                        ColorUtil.applyOpacity(leftColor, 100)
                ))
                .thickness(-2.6f)
                .size(new SizeState(PALETTE_SWATCH_WIDTH, PALETTE_SWATCH_HEIGHT))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);
    }
}
