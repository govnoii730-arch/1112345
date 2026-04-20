package ru.mytheria.main.ui.elements;

import com.google.common.base.Suppliers;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.ClientProvider;
import ru.mytheria.api.client.draggable.Draggable;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.events.impl.Render2DEvent;
import ru.mytheria.api.util.media.MediaPlayer;
import ru.mytheria.api.util.render.RenderEngine;
import ru.mytheria.api.util.render.ScissorUtil;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.api.util.shader.impl.text.TextSystem;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.module.render.Wmark;
import ru.mytheria.main.ui.elements.event.IRender;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

public class Watermark extends Draggable implements IRender {
    private static final long DISPLAY_MS = 2000L;
    private static final long MEDIA_POLL_MS = 600L;
    private static final float BORDER_THICKNESS = -3.4f;
    private static final Identifier LOGO_TEXTURE = Identifier.of("minecraft", "textures/mob_effect/levitation.png");
    private static final Identifier FALLBACK_TRACK_TEXTURE = Identifier.of("minecraft", "textures/mob_effect/levitation.png");

    private static volatile String currentLabel = ClientProvider.getClientName();
    private static volatile long resetAt = 0L;

    private static final Supplier<Interface> interfaceModule = Suppliers.memoize(
            () -> (Interface) Mytheria.getInstance().getModuleManager().find(Interface.class)
    );
    private static final Supplier<Wmark> watermarkModule = Suppliers.memoize(
            () -> (Wmark) Mytheria.getInstance().getModuleManager().find(Wmark.class)
    );

    private long lastMediaPollAt = 0L;

    public Watermark() {
        super(10f, 10f, 118f, 20f, () -> watermarkModule.get().getEnabled());
    }

    public static void notifyModuleToggle(String moduleName, boolean enabled) {
        currentLabel = moduleName + (enabled ? " включен!" : " выключен!");
        resetAt = System.currentTimeMillis() + DISPLAY_MS;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, RenderTickCounter tickCounter) {
        renderWatermark(context.getMatrices());
    }

    @EventHandler
    public void onRender(Render2DEvent event) {
        renderWatermark(event.getMatrixStack());
    }

    private void renderWatermark(MatrixStack matrices) {
        resetIfExpired();

        Wmark module = watermarkModule.get();
        boolean glassMode = isGlassMode(interfaceModule.get().getMode().getValue());
        boolean showMusicBar = module.getMusicBar().getEnabled();
        boolean noCoverMode = "Без обложки".equals(module.getMusicBarMode().getValue());
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String label = currentLabel;
        float renderScale = getSizeScale();

        float iconSize = 11f;
        float timeWidth = QuickApi.sf_bold().getWidth(time, 8);
        float labelWidth = QuickApi.sf_bold().getWidth(label, 8);
        float topWidth = 6f + iconSize + 5f + labelWidth + 10f + timeWidth + 8f;
        float width = showMusicBar ? Math.max(topWidth, noCoverMode ? 150f : 168f) : topWidth;
        float height = showMusicBar ? 43f : 22f;

        setWidth(width * renderScale);
        setHeight(height * renderScale);

        ru.mytheria.api.util.math.Math.scale(matrices, getX(), getY(), renderScale, () -> {
            drawBackground(matrices, width, height, glassMode, module.getTransparent().getEnabled());
            drawBorder(matrices, width, height);

            RenderEngine.drawTexture(
                    matrices,
                    getX() + 6f,
                    getY() + 5.5f,
                    iconSize,
                    iconSize,
                    3f,
                    0.0f,
                    0.0f,
                    1.0f,
                    1.0f,
                    LOGO_TEXTURE,
                    Color.WHITE
            );

            new TextSystem()
                    .font(QuickApi.sf_bold())
                    .text(label)
                    .color(textColor())
                    .size(8)
                    .thickness(0.05f)
                    .build()
                    .render(matrices.peek().getPositionMatrix(), getX() + 22f, getY() + 6f);

            new TextSystem()
                    .font(QuickApi.sf_bold())
                    .text(time)
                    .color(textColor())
                    .size(8)
                    .thickness(0.05f)
                    .build()
                    .render(matrices.peek().getPositionMatrix(), getX() + width - 8f - timeWidth, getY() + 6f);

            if (showMusicBar) {
                drawMusicBar(matrices, noCoverMode, width);
            }
        });
    }

    private void drawMusicBar(MatrixStack matrices, boolean noCoverMode, float width) {
        MediaPlayer mediaPlayer = Mytheria.getInstance().getMediaPlayer();
        updateMediaPlayer(mediaPlayer);

        boolean hasMedia = mediaPlayer != null && !mediaPlayer.fullNullCheck();
        String trackLine = hasMedia ? buildTrackLine(mediaPlayer) : "Музыка не найдена";

        float textStartX = noCoverMode ? getX() + 6f : getX() + 22f;
        float trackY = getY() + 24f;
        float trackRegionWidth = width - (textStartX - getX()) - 8f;

        if (!noCoverMode) {
            drawTrackCover(matrices, mediaPlayer, hasMedia);
        }

        drawMarqueeText(matrices, trackLine, textStartX, trackY, trackRegionWidth, textColor());

        float progress = 0.0f;
        if (hasMedia && mediaPlayer.getDuration() > 0L) {
            progress = Math.max(0.0f, Math.min(1.0f, mediaPlayer.getPosition() / (float) mediaPlayer.getDuration()));
        }

        float barX = textStartX;
        float barY = getY() + 35f;
        float barWidth = Math.max(12f, trackRegionWidth);
        Color barBg = new Color(255, 255, 255, 50);
        Color accent = InterfaceStyle.hudPrimary(textColor());
        Color accentSecondary = InterfaceStyle.hudSecondary(textColor());

        RenderEngine.drawRectangle(
                matrices,
                barX,
                barY,
                barWidth,
                3.0f,
                new ColorState(barBg),
                new RadiusState(2f),
                1f
        );

        float fillWidth = Math.max(4f, barWidth * progress);
        RenderEngine.drawRectangle(
                matrices,
                barX,
                barY,
                hasMedia ? fillWidth : 4f,
                3.0f,
                new ColorState(accent, accentSecondary, accentSecondary, accent),
                new RadiusState(2f),
                1f
        );
    }

    private void drawTrackCover(MatrixStack matrices, MediaPlayer mediaPlayer, boolean hasMedia) {
        AbstractTexture artwork = hasMedia ? mediaPlayer.getTexture() : null;
        if (artwork != null) {
            RenderEngine.drawTexture(
                    matrices,
                    getX() + 6f,
                    getY() + 24f,
                    12f,
                    12f,
                    3f,
                    artwork,
                    Color.WHITE
            );
            return;
        }

        RenderEngine.drawTexture(
                matrices,
                getX() + 6f,
                getY() + 24f,
                12f,
                12f,
                3f,
                0.0f,
                0.0f,
                1.0f,
                1.0f,
                FALLBACK_TRACK_TEXTURE,
                Color.WHITE
        );
    }

    private void drawMarqueeText(MatrixStack matrices, String text, float x, float y, float width, Color color) {
        float textWidth = QuickApi.inter().getWidth(text, 7f);
        if (textWidth <= width) {
            QuickApi.text()
                    .font(QuickApi.inter())
                    .text(text)
                    .color(color)
                    .size(7f)
                    .build()
                    .render(matrices.peek().getPositionMatrix(), x, y);
            return;
        }

        float gap = 22f;
        float cycleWidth = textWidth + gap;
        float offset = (System.currentTimeMillis() * 0.05f) % cycleWidth;

        ScissorUtil.push(x, y - 1f, width, 9f);
        QuickApi.text()
                .font(QuickApi.inter())
                .text(text)
                .color(color)
                .size(7f)
                .build()
                .render(matrices.peek().getPositionMatrix(), x - offset, y);

        QuickApi.text()
                .font(QuickApi.inter())
                .text(text)
                .color(color)
                .size(7f)
                .build()
                .render(matrices.peek().getPositionMatrix(), x - offset + cycleWidth, y);
        ScissorUtil.pop();
    }

    private void drawBackground(MatrixStack matrices, float width, float height, boolean glassMode, boolean transparent) {
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
                    .render(matrices.peek().getPositionMatrix(), getX(), getY());

            RenderEngine.drawLiquid(
                    matrices,
                    getX(), getY(), width, height,
                    new ColorState(backgroundColor()),
                    new RadiusState(radius),
                    1f,
                    9.0f,
                    25.0f,
                    1.2f
            );
            return;
        }

        RenderEngine.drawBlurRectangle(
                matrices,
                getX(), getY(), width, height,
                new ColorState(backgroundColor()),
                new RadiusState(radius),
                1f,
                55,
                0.1f
        );
    }

    private void drawBorder(MatrixStack matrices, float width, float height) {
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
                .render(matrices.peek().getPositionMatrix(), getX(), getY());

        QuickApi.border()
                .radius(new RadiusState(radius))
                .color(new ColorState(primarySoft, secondarySoft, secondarySoft, primarySoft))
                .thickness(BORDER_THICKNESS - 1.2f)
                .size(new SizeState(width, height))
                .build()
                .render(matrices.peek().getPositionMatrix(), getX(), getY());
    }

    private void resetIfExpired() {
        if (resetAt != 0L && System.currentTimeMillis() >= resetAt) {
            currentLabel = ClientProvider.getClientName();
            resetAt = 0L;
        }
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
                || "РЎС‚РµРєР»Рѕ".equals(mode)
                || "РЋРЎвЂљР ВµР С”Р В»Р С•".equals(mode);
    }

    private Color textColor() {
        return InterfaceStyle.hudPrimary(new Color(watermarkModule.get().getTextColor().getColor(), true));
    }

    private Color backgroundColor() {
        int color = watermarkModule.get().getBackgroundColor().getColor();
        return new Color(InterfaceStyle.hudPrimaryColorWithAlpha(color), true);
    }
}
