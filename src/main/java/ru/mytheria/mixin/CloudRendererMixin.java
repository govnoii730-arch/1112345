package ru.mytheria.mixin;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.Mytheria;
import ru.mytheria.main.module.render.CustomFog;

@Mixin(CloudRenderer.class)
public class CloudRendererMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void mytheria$hideClouds(int color, CloudRenderMode cloudRenderMode, float cloudHeight, Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d cameraPos, float cloudsHeight, CallbackInfo ci) {
        Mytheria mytheria = Mytheria.getInstance();
        if (mytheria == null || mytheria.getModuleManager() == null) {
            return;
        }

        CustomFog customFog = (CustomFog) mytheria.getModuleManager().find(CustomFog.class);
        if (customFog != null && Boolean.TRUE.equals(customFog.getEnabled()) && customFog.getRemoveClouds().getEnabled()) {
            ci.cancel();
        }
    }
}
