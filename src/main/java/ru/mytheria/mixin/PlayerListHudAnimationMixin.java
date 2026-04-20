package ru.mytheria.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.main.module.render.Animations;

@Mixin(PlayerListHud.class)
public class PlayerListHudAnimationMixin {
    @Unique
    private static float mytheria$tabProgress = 0.0f;
    @Unique
    private static long mytheria$tabLastTimeMs = -1L;
    @Unique
    private static long mytheria$tabLastRenderMs = -1L;
    @Unique
    private boolean mytheria$tabPushed = false;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void mytheria$animateTabStart(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (!Animations.animateTab()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean opened = mc != null && mc.options != null && mc.options.playerListKey.isPressed();

        long now = Util.getMeasuringTimeMs();
        if (mytheria$tabLastRenderMs > 0L && now - mytheria$tabLastRenderMs > 220L) {
            mytheria$tabProgress = 0.0f;
        }
        mytheria$tabLastRenderMs = now;

        float delta = frameDelta();
        float target = opened ? 1.0f : 0.0f;
        mytheria$tabProgress += (target - mytheria$tabProgress) * Math.min(1.0f, delta * 6.5f);

        float slide = (1.0f - mytheria$tabProgress) * -18.0f;
        float scale = 0.92f + mytheria$tabProgress * 0.08f;
        float pivotX = scaledWindowWidth * 0.5f;

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, slide, 0.0f);
        context.getMatrices().translate(pivotX, 0.0f, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-pivotX, 0.0f, 0.0f);
        mytheria$tabPushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void mytheria$animateTabEnd(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (!mytheria$tabPushed) {
            return;
        }
        context.getMatrices().pop();
        mytheria$tabPushed = false;
    }

    @Unique
    private float frameDelta() {
        long now = Util.getMeasuringTimeMs();
        if (mytheria$tabLastTimeMs < 0L) {
            mytheria$tabLastTimeMs = now;
            return 1.0f / 60.0f;
        }

        float delta = (now - mytheria$tabLastTimeMs) / 1000.0f;
        mytheria$tabLastTimeMs = now;
        return Math.max(1.0f / 240.0f, Math.min(0.05f, delta));
    }
}
