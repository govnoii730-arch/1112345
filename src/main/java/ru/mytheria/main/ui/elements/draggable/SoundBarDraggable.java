package ru.mytheria.main.ui.elements.draggable;

import com.google.common.base.Suppliers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.draggable.Draggable;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.util.media.MediaPlayer;
import ru.mytheria.api.util.render.RenderEngine;
import ru.mytheria.api.util.render.ScissorUtil;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.module.render.SoundBar;

import java.awt.Color;
import java.util.function.Supplier;

public class SoundBarDraggable extends Draggable {
    private static final float BORDER_THICKNESS = -3.4f;
    private static final long MEDIA_POLL_MS = 600L;
    private static final Identifier FALLBACK_TEXTURE = Identifier.of("minecraft", "textures/mob_effect/levitation.png");

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<SoundBar> soundBarModule = Suppliers.memoize(
            () -> (SoundBar) Mytheria.getInstance().getModuleManager().find(SoundBar.class)
    );

    private long lastMediaPollAt = 0L;

    public SoundBarDraggable() {
        super(10f, 205f, 170f, 38f, () -> soundBarModule.get().getEnabled());
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        SoundBar module = soundBarModule.get();
        MediaPlayer mediaPlayer = Mytheria.getInstance().getMediaPlayer();
        boolean noCoverMode = "Без обложки".equals(module.getMode().getValue());
        boolean glassMode = isGlassMode(interfaceModule.get().getMode().getValue());
        boolean transparent = module.getTransparent().getEnabled();
        float renderScale = getSizeScale();

        updateMediaPlayer(mediaPlayer);

        boolean hasMedia = mediaPlayer != null && !mediaPlayer.fullNullCheck();
        String trackLine = hasMedia ? buildTrackLine(mediaPlayer) : "Музыка не найдена";
        float coverWidth = noCoverMode ? 0f : 18f;
        float textStartX = noCoverMode ? 7f : 25f;
        float width = noCoverMode ? 152f : 170f;
        float height = 38f;

        Color textColor = InterfaceStyle.hudPrimary(new Color(module.getTextColor().getColor(), true));
        Color accentColor = new Color(InterfaceStyle.hudPrimaryColorWithAlpha(module.getAccentColor().getColor()), true);
        Color accentSecondary = new Color(InterfaceStyle.hudSecondaryColorWithAlpha(module.getAccentColor().getColor()), true);
        Color backgroundColor = new Color(InterfaceStyle.hudPrimaryColorWithAlpha(module.getBackgroundColor().getColor()), true);

        setWidth(width * renderScale);
        setHeight(height * renderScale);

        ru.mytheria.api.util.math.Math.scale(context.getMatrices(), getX(), getY(), renderScale, () -> {
            drawBackground(context, width, height, backgroundColor, glassMode, transparent);
            drawBorder(context, width, height);

            if (!noCoverMode) {
                drawCover(context, mediaPlayer, hasMedia);
            }

            float titleY = getY() + 6f;
            QuickApi.text()
                    .font(QuickApi.sf_semi())
                    .text("SoundBar")
                    .color(textColor)
                    .size(8f)
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), getX() + textStartX, titleY);

            float trackY = getY() + 16.5f;
            float trackRegionWidth = width - textStartX - 8f;
            drawMarqueeText(context, trackLine, getX() + textStartX, trackY, trackRegionWidth, textColor);

            float progress = 0.0f;
            if (hasMedia && mediaPlayer.getDuration() > 0L) {
                progress = Math.max(0.0f, Math.min(1.0f, mediaPlayer.getPosition() / (float) mediaPlayer.getDuration()));
            }

            float barX = getX() + textStartX;
            float barY = getY() + 29f;
            float barWidth = width - textStartX - 8f;

            RenderEngine.drawRectangle(
                    context.getMatrices(),
                    barX,
                    barY,
                    barWidth,
                    3.0f,
                    new ColorState(new Color(255, 255, 255, 50)),
                    new RadiusState(2f),
                    1f
            );

            float fillWidth = Math.max(4f, barWidth * progress);
            RenderEngine.drawRectangle(
                    context.getMatrices(),
                    barX,
                    barY,
                    hasMedia ? fillWidth : 4f,
                    3.0f,
                    new ColorState(accentColor, accentSecondary, accentSecondary, accentColor),
                    new RadiusState(2f),
                    1f
            );
        });
    }

    private void drawMarqueeText(DrawContext context, String text, float x, float y, float width, Color color) {
        float textWidth = QuickApi.inter().getWidth(text, 7f);
        if (textWidth <= width) {
            QuickApi.text()
                    .font(QuickApi.inter())
                    .text(text)
                    .color(color)
                    .size(7f)
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), x, y);
            return;
        }

        float gap = 24f;
        float cycleWidth = textWidth + gap;
        float offset = (System.currentTimeMillis() * 0.05f) % cycleWidth;

        ScissorUtil.push(x, y - 1f, width, 9f);
        QuickApi.text()
                .font(QuickApi.inter())
                .text(text)
                .color(color)
                .size(7f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x - offset, y);

        QuickApi.text()
                .font(QuickApi.inter())
                .text(text)
                .color(color)
                .size(7f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x - offset + cycleWidth, y);
        ScissorUtil.pop();
    }

    private void drawCover(DrawContext context, MediaPlayer mediaPlayer, boolean hasMedia) {
        AbstractTexture artwork = hasMedia ? mediaPlayer.getTexture() : null;
        if (artwork != null) {
            RenderEngine.drawTexture(
                    context.getMatrices(),
                    getX() + 5f,
                    getY() + 9f,
                    14f,
                    14f,
                    3f,
                    artwork,
                    Color.WHITE
            );
            return;
        }

        RenderEngine.drawTexture(
                context.getMatrices(),
                getX() + 5f,
                getY() + 9f,
                14f,
                14f,
                3f,
                0.0f,
                0.0f,
                1.0f,
                1.0f,
                FALLBACK_TEXTURE,
                Color.WHITE
        );
    }

    private void drawBackground(DrawContext context, float width, float height, Color backgroundColor, boolean glassMode, boolean transparent) {
        float radius = InterfaceStyle.radius(8f, 2.5f);
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
        float radius = InterfaceStyle.radius(8f, 2.5f);
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

    private String buildTrackLine(MediaPlayer mediaPlayer) {
        String title = mediaPlayer.getTitle().isEmpty() ? mediaPlayer.getLastTitle() : mediaPlayer.getTitle();
        String artist = mediaPlayer.getArtist();
        return artist == null || artist.isEmpty() ? title : title + " - " + artist;
    }

    private void updateMediaPlayer(MediaPlayer mediaPlayer) {
        if (mediaPlayer == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastMediaPollAt >= MEDIA_POLL_MS) {
            mediaPlayer.onTick();
            lastMediaPollAt = now;
        }
    }

    private boolean isGlassMode(String mode) {
        return "Стекло".equals(mode)
                || "РЎС‚РµРєР»Рѕ".equals(mode);
    }
}
