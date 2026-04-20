package ru.mytheria.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mytheria.Mytheria;
import ru.mytheria.main.module.render.AspectRatio;

@Mixin(GameRenderer.class)
public class GameRendererAspectRatioMixin {

    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true, require = 0)
    private void mytheria$applyAspectRatio(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio module = getModule();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getWindow().getScaledHeight() <= 0) {
            return;
        }

        Matrix4f matrix = new Matrix4f(cir.getReturnValue());
        float currentAspect = (float) mc.getWindow().getScaledWidth() / (float) mc.getWindow().getScaledHeight();

        if (module != null && Boolean.TRUE.equals(module.getEnabled())) {
            float desiredAspect = module.targetRatio();
            if (desiredAspect > 0.01f) {
                float scaleX = currentAspect / desiredAspect;
                matrix.m00(matrix.m00() * scaleX);
            }
        }

        cir.setReturnValue(matrix);
    }

    private AspectRatio getModule() {
        Mytheria mytheria = Mytheria.getInstance();
        if (mytheria == null || mytheria.getModuleManager() == null) {
            return null;
        }
        return (AspectRatio) mytheria.getModuleManager().find(AspectRatio.class);
    }

}
