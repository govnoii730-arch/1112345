package ru.mytheria.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.main.module.render.Animations;

@Mixin(ChatHud.class)
public class ChatHudAnimationMixin {
    @Unique
    private static float mytheria$chatProgress = 0.0f;
    @Unique
    private static long mytheria$chatLastTimeMs = -1L;
    @Unique
    private boolean mytheria$chatPushed = false;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void mytheria$animateChatStart(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (!Animations.animateChat()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean opened = mc != null && mc.currentScreen instanceof ChatScreen;

        float delta = frameDelta();
        float target = opened ? 1.0f : 0.0f;
        mytheria$chatProgress += (target - mytheria$chatProgress) * Math.min(1.0f, delta * 9.0f);

        float slide = (1.0f - mytheria$chatProgress) * 16.0f;
        float scale = 0.9f + mytheria$chatProgress * 0.1f;
        float pivotX = 4.0f;
        float pivotY = context.getScaledWindowHeight() - 42.0f;

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, slide, 0.0f);
        context.getMatrices().translate(pivotX, pivotY, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-pivotX, -pivotY, 0.0f);
        mytheria$chatPushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void mytheria$animateChatEnd(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (!mytheria$chatPushed) {
            return;
        }

        context.getMatrices().pop();
        mytheria$chatPushed = false;
    }

    @Unique
    private float frameDelta() {
        long now = Util.getMeasuringTimeMs();
        if (mytheria$chatLastTimeMs < 0L) {
            mytheria$chatLastTimeMs = now;
            return 1.0f / 60.0f;
        }

        float delta = (now - mytheria$chatLastTimeMs) / 1000.0f;
        mytheria$chatLastTimeMs = now;
        return Math.max(1.0f / 240.0f, Math.min(0.05f, delta));
    }
}
