package ru.mytheria.mixin;

import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.api.events.EventManager;
import ru.mytheria.api.events.impl.MouseEvent;

import static ru.mytheria.api.clientannotation.QuickImport.mc;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void mytheria$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (mc.world == null || mc.player == null || action != GLFW.GLFW_PRESS) {
            return;
        }

        EventManager.call(new MouseEvent(button, action));
    }
}
