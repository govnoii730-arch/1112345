package ru.mytheria.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.api.clientannotation.QuickImport;
import ru.mytheria.api.events.EventManager;
import ru.mytheria.api.events.EventPopTotem;
import ru.mytheria.main.module.render.Removals;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements QuickImport {

    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void mytheria$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (mc.world == null || packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) {
            return;
        }

        Entity entity = packet.getEntity(mc.world);
        if (entity instanceof PlayerEntity player && player != mc.player) {
            EventManager.call(new EventPopTotem(player));
        }
    }

    @Redirect(
            method = "onEntityStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;showFloatingItem(Lnet/minecraft/item/ItemStack;)V"
            )
    )
    private void mytheria$hideTotemAnimation(GameRenderer instance, ItemStack stack) {
        if (!Removals.hideTotemAnimation()) {
            instance.showFloatingItem(stack);
        }
    }
}
