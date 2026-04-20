package ru.mytheria.main.ui.clickGui.components.settings.colorsetting.window;

import net.minecraft.util.math.MathHelper;
import net.minecraft.client.gui.DrawContext;
import ru.mytheria.Mytheria;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.util.animations.Direction;
import ru.mytheria.api.util.color.ColorUtil;
import ru.mytheria.api.util.math.Math;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.api.util.window.WindowLayer;

import java.awt.Color;

public class ColorPickerWindowComponent extends WindowLayer {

    private static final float PADDING = 8f;
    private static final float PALETTE_SIZE = 78f;
    private static final float HUE_WIDTH = 10f;
    private static final float SLIDER_HEIGHT = 8f;
    private static final int GRID_STEPS = 24;

    private final ColorSetting colorSetting;
    private boolean draggingPalette;
    private boolean draggingHue;
    private boolean draggingAlpha;

    public ColorPickerWindowComponent(ColorSetting colorSetting) {
        this.colorSetting = colorSetting;
    }

    @Override
    public void init() {
        size(PADDING * 2f + PALETTE_SIZE + HUE_WIDTH + 8f, PADDING * 2f + PALETTE_SIZE + SLIDER_HEIGHT + 20f);
    }

    @Override
    public ColorPickerWindowComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (draggingPalette) {
            updatePalette(mouseX, mouseY);
        }
        if (draggingHue) {
            updateHue(mouseY);
        }
        if (draggingAlpha) {
            updateAlpha(mouseX);
        }

        QuickApi.border()
                .radius(new RadiusState(4))
                .size(new SizeState(getWidth(), getHeight()))
                .color(new ColorState(ColorUtil.applyOpacity(0xFFFFFFFF, 25)))
                .thickness(-.5f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

        QuickApi.blur()
                .radius(new RadiusState(4))
                .size(new SizeState(getWidth(), getHeight()))
                .blurRadius(8)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

        QuickApi.rectangle()
                .radius(new RadiusState(4))
                .size(new SizeState(getWidth(), getHeight()))
                .color(new ColorState(ColorUtil.applyOpacity(0xFF000000, 65)))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY());

        float paletteX = getX() + PADDING;
        float paletteY = getY() + PADDING;
        float hueX = paletteX + PALETTE_SIZE + 8f;
        float hueY = paletteY;
        float alphaX = paletteX;
        float alphaY = paletteY + PALETTE_SIZE + 8f;
        float alphaWidth = PALETTE_SIZE + HUE_WIDTH + 8f;

        drawPalette(context, paletteX, paletteY);
        drawHueSlider(context, hueX, hueY);
        drawAlphaSlider(context, alphaX, alphaY, alphaWidth);
        drawMarkers(context, paletteX, paletteY, hueX, hueY, alphaX, alphaY, alphaWidth);
        drawPreview(context, alphaX, alphaY + SLIDER_HEIGHT + 6f, alphaWidth);

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float paletteX = getX() + PADDING;
        float paletteY = getY() + PADDING;
        float hueX = paletteX + PALETTE_SIZE + 8f;
        float hueY = paletteY;
        float alphaX = paletteX;
        float alphaY = paletteY + PALETTE_SIZE + 8f;
        float alphaWidth = PALETTE_SIZE + HUE_WIDTH + 8f;

        if (Math.isHover(mouseX, mouseY, paletteX, paletteY, PALETTE_SIZE, PALETTE_SIZE)) {
            draggingPalette = true;
            updatePalette(mouseX, mouseY);
            return true;
        }

        if (Math.isHover(mouseX, mouseY, hueX, hueY, HUE_WIDTH, PALETTE_SIZE)) {
            draggingHue = true;
            updateHue(mouseY);
            return true;
        }

        if (Math.isHover(mouseX, mouseY, alphaX, alphaY, alphaWidth, SLIDER_HEIGHT)) {
            draggingAlpha = true;
            updateAlpha(mouseX);
            return true;
        }

        if (Math.isHover(mouseX, mouseY, getX(), getY(), getWidth(), getHeight())) {
            return true;
        }

        if (getAnimation().getDirection().equals(Direction.BACKWARDS)) return false;

        Mytheria.getInstance().getClickGuiScreen().getWindowRepository().pop(this);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = draggingPalette || draggingHue || draggingAlpha;
        draggingPalette = false;
        draggingHue = false;
        draggingAlpha = false;
        return wasDragging;
    }

    private void drawPalette(DrawContext context, float x, float y) {
        float cellSize = PALETTE_SIZE / GRID_STEPS;

        for (int xi = 0; xi < GRID_STEPS; xi++) {
            for (int yi = 0; yi < GRID_STEPS; yi++) {
                float saturation = xi / (float) (GRID_STEPS - 1);
                float brightness = 1f - yi / (float) (GRID_STEPS - 1);
                int color = toArgb(colorSetting.getHsb(), saturation, brightness, 1f);

                QuickApi.rectangle()
                        .size(new SizeState(cellSize + 0.6f, cellSize + 0.6f))
                        .color(new ColorState(color))
                        .radius(new RadiusState(0))
                        .build()
                        .render(context.getMatrices().peek().getPositionMatrix(), x + xi * cellSize, y + yi * cellSize);
            }
        }
    }

    private void drawHueSlider(DrawContext context, float x, float y) {
        float cellHeight = PALETTE_SIZE / GRID_STEPS;

        for (int i = 0; i < GRID_STEPS; i++) {
            float hue = i / (float) GRID_STEPS;
            int color = toArgb(hue, 1f, 1f, 1f);

            QuickApi.rectangle()
                    .size(new SizeState(HUE_WIDTH, cellHeight + 0.6f))
                    .color(new ColorState(color))
                    .radius(new RadiusState(0))
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), x, y + i * cellHeight);
        }
    }

    private void drawAlphaSlider(DrawContext context, float x, float y, float width) {
        int steps = 28;
        float cellWidth = width / steps;

        for (int i = 0; i < steps; i++) {
            float alpha = i / (float) (steps - 1);
            int color = toArgb(colorSetting.getHsb(), colorSetting.getSaturation(), colorSetting.getBrightness(), alpha);

            QuickApi.rectangle()
                    .size(new SizeState(cellWidth + 0.6f, SLIDER_HEIGHT))
                    .color(new ColorState(color))
                    .radius(new RadiusState(0))
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), x + i * cellWidth, y);
        }
    }

    private void drawMarkers(DrawContext context, float paletteX, float paletteY, float hueX, float hueY, float alphaX, float alphaY, float alphaWidth) {
        float markerX = paletteX + colorSetting.getSaturation() * PALETTE_SIZE;
        float markerY = paletteY + (1f - colorSetting.getBrightness()) * PALETTE_SIZE;
        float hueMarkerY = hueY + colorSetting.getHsb() * PALETTE_SIZE;
        float alphaMarkerX = alphaX + getAlphaNormalized() * alphaWidth;

        QuickApi.border()
                .size(new SizeState(5f, 5f))
                .radius(new RadiusState(1.5f))
                .color(new ColorState(0xFFFFFFFF))
                .thickness(-1f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), markerX - 2.5f, markerY - 2.5f);

        QuickApi.border()
                .size(new SizeState(HUE_WIDTH + 2f, 4f))
                .radius(new RadiusState(1.5f))
                .color(new ColorState(0xFFFFFFFF))
                .thickness(-1f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), hueX - 1f, hueMarkerY - 2f);

        QuickApi.border()
                .size(new SizeState(4f, SLIDER_HEIGHT + 2f))
                .radius(new RadiusState(1.5f))
                .color(new ColorState(0xFFFFFFFF))
                .thickness(-1f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), alphaMarkerX - 2f, alphaY - 1f);
    }

    private void drawPreview(DrawContext context, float x, float y, float width) {
        String previewText = String.format("#%08X", colorSetting.getColor());

        QuickApi.rectangle()
                .size(new SizeState(width, 12f))
                .radius(new RadiusState(3f))
                .color(new ColorState(ColorUtil.applyOpacity(0xFFFFFFFF, 10)))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        QuickApi.rectangle()
                .size(new SizeState(20f, 8f))
                .radius(new RadiusState(2f))
                .color(new ColorState(colorSetting.getColor()))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x + 2f, y + 2f);

        QuickApi.text()
                .font(QuickApi.inter())
                .size(6)
                .text(previewText)
                .color(0xFFFFFFFF)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x + 26f, y + 2.5f);
    }

    private void updatePalette(double mouseX, double mouseY) {
        float paletteX = getX() + PADDING;
        float paletteY = getY() + PADDING;
        float saturation = (float) ((mouseX - paletteX) / PALETTE_SIZE);
        float brightness = 1f - (float) ((mouseY - paletteY) / PALETTE_SIZE);

        setColor(colorSetting.getHsb(),
                MathHelper.clamp(saturation, 0f, 1f),
                MathHelper.clamp(brightness, 0f, 1f),
                getAlphaNormalized());
    }

    private void updateHue(double mouseY) {
        float hueY = getY() + PADDING;
        float hue = (float) ((mouseY - hueY) / PALETTE_SIZE);
        setColor(MathHelper.clamp(hue, 0f, 1f),
                colorSetting.getSaturation(),
                colorSetting.getBrightness(),
                getAlphaNormalized());
    }

    private void updateAlpha(double mouseX) {
        float alphaX = getX() + PADDING;
        float alphaWidth = PALETTE_SIZE + HUE_WIDTH + 8f;
        float alpha = (float) ((mouseX - alphaX) / alphaWidth);

        setColor(colorSetting.getHsb(),
                colorSetting.getSaturation(),
                colorSetting.getBrightness(),
                MathHelper.clamp(alpha, 0f, 1f));
    }

    private void setColor(float hue, float saturation, float brightness, float alpha) {
        colorSetting.set(
                MathHelper.clamp(hue, 0f, 1f),
                MathHelper.clamp(saturation, 0f, 1f),
                MathHelper.clamp(brightness, 0f, 1f),
                MathHelper.clamp(alpha, 0f, 1f)
        );
    }

    private float getAlphaNormalized() {
        float alpha = colorSetting.getAlpha();
        return alpha > 1f ? alpha / 255f : alpha;
    }

    private int toArgb(float hue, float saturation, float brightness, float alpha) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        int alphaChannel = Math.round(MathHelper.clamp(alpha, 0f, 1f) * 255f);
        return (alphaChannel << 24) | (rgb & 0x00FFFFFF);
    }
}
