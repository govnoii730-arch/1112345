package ru.mytheria.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mytheria.Mytheria;
import ru.mytheria.api.events.impl.EventPlayerTick;
import ru.mytheria.main.module.movement.LockSlot;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    public void tick( CallbackInfo ci) {
        EventPlayerTick event = new EventPlayerTick();
        Mytheria.getEventProvider().post(event);
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void mytheria$blockLockedSlotDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        if (!LockSlot.shouldCancelDrop()) {
            return;
        }

        LockSlot.notifyBlockedDrop();
        cir.setReturnValue(false);
    }
}
