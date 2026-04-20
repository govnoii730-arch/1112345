package ru.mytheria.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import ru.mytheria.main.module.render.Animations;

@Mixin(InGameHud.class)
public class InGameHudAnimationMixin {
    @Unique
    private static float mytheria$hotbarPulse = 0.0f;
    @Unique
    private static long mytheria$hotbarLastTimeMs = -1L;
    @Unique
    private static int mytheria$lastSelectedSlot = -1;
    @Unique
    private boolean mytheria$hotbarPushed = false;

    @Inject(method = "renderHotbar", at = @At("HEAD"), require = 0)
    private void mytheria$animateHotbarStart(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!Animations.animateHotbar()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.player.getInventory() == null) {
            return;
        }

        int selectedSlot = mc.player.getInventory().selectedSlot;
        if (mytheria$lastSelectedSlot == -1) {
            mytheria$lastSelectedSlot = selectedSlot;
        } else if (selectedSlot != mytheria$lastSelectedSlot) {
            mytheria$lastSelectedSlot = selectedSlot;
            mytheria$hotbarPulse = 1.0f;
        }

        float delta = frameDelta();
        mytheria$hotbarPulse = Math.max(0.0f, mytheria$hotbarPulse - delta * 6.0f);
        float easing = mytheria$hotbarPulse * mytheria$hotbarPulse * (3.0f - 2.0f * mytheria$hotbarPulse);
        float offset = easing * 8.0f;

        if (offset <= 0.01f) {
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, -offset, 0.0f);
        mytheria$hotbarPushed = true;
    }

    @Inject(method = "renderHotbar", at = @At("RETURN"), require = 0)
    private void mytheria$animateHotbarEnd(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!mytheria$hotbarPushed) {
            return;
        }

        context.getMatrices().pop();
        mytheria$hotbarPushed = false;
    }

    @Unique
    private float frameDelta() {
        long now = Util.getMeasuringTimeMs();
        if (mytheria$hotbarLastTimeMs < 0L) {
            mytheria$hotbarLastTimeMs = now;
            return 1.0f / 60.0f;
        }

        float delta = (now - mytheria$hotbarLastTimeMs) / 1000.0f;
        mytheria$hotbarLastTimeMs = now;
        return Math.max(1.0f / 240.0f, Math.min(0.05f, delta));
    }
}
