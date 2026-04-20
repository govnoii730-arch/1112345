package ru.mytheria.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.Mytheria;

@Mixin(MinecraftClient.class)
public class MinecraftClientShutdownMixin {

    @Inject(method = "stop", at = @At("HEAD"), require = 0)
    private void mytheria$saveOnShutdown(CallbackInfo ci) {
        Mytheria mytheria = Mytheria.getInstance();
        if (mytheria == null) {
            return;
        }

        if (mytheria.getConfigurationService() != null) {
            mytheria.getConfigurationService().save("autosave");
        }

        if (mytheria.getDraggableRepository() != null) {
            mytheria.getDraggableRepository().saveState();
        }
    }
}
