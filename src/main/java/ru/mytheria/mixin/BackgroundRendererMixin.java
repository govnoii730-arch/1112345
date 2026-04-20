package ru.mytheria.mixin;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mytheria.Mytheria;
import ru.mytheria.main.module.render.CustomFog;
import ru.mytheria.main.module.render.InterfaceStyle;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private static void mytheria$overrideFogColor(Camera camera, float tickProgress, net.minecraft.client.world.ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        CustomFog customFog = getCustomFog();
        if (customFog == null || !Boolean.TRUE.equals(customFog.getEnabled())) {
            return;
        }

        cir.setReturnValue(toVector(themedFogColor(customFog)));
    }

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
    private static void mytheria$applyCustomFog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickFog, float tickProgress, CallbackInfoReturnable<Fog> cir) {
        CustomFog customFog = getCustomFog();
        if (customFog == null || !Boolean.TRUE.equals(customFog.getEnabled())) {
            return;
        }

        Fog originalFog = cir.getReturnValue();
        int fogColor = themedFogColor(customFog);
        float end = customFog.getDistance().getValue();
        float start = Math.max(0.0f, end * 0.2f);

        cir.setReturnValue(new Fog(
                start,
                end,
                originalFog.shape(),
                ((fogColor >> 16) & 0xFF) / 255.0f,
                ((fogColor >> 8) & 0xFF) / 255.0f,
                (fogColor & 0xFF) / 255.0f,
                ((fogColor >>> 24) & 0xFF) / 255.0f
        ));
    }

    private static CustomFog getCustomFog() {
        Mytheria mytheria = Mytheria.getInstance();
        if (mytheria == null || mytheria.getModuleManager() == null) {
            return null;
        }
        return (CustomFog) mytheria.getModuleManager().find(CustomFog.class);
    }

    private static Vector4f toVector(int color) {
        return new Vector4f(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                ((color >>> 24) & 0xFF) / 255.0f
        );
    }

    private static int themedFogColor(CustomFog customFog) {
        return InterfaceStyle.hudPrimaryColor(customFog.getColor().getColor());
    }
}
