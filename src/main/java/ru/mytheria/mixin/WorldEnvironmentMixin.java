package ru.mytheria.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mytheria.main.module.render.Removals;

@Mixin(World.class)
public class WorldEnvironmentMixin {

    @Inject(method = "getRainGradient(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytheria$hideRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (((Object) this) instanceof ClientWorld && Removals.hideWeather()) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "getThunderGradient(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytheria$hideThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (((Object) this) instanceof ClientWorld && Removals.hideWeather()) {
            cir.setReturnValue(0.0f);
        }
    }
}
