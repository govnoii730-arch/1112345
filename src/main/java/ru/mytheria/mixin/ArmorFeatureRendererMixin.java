package ru.mytheria.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.Mytheria;
import ru.mytheria.main.module.render.ArmorInvise;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorFeatureRendererMixin {
    @Unique
    private boolean mytheria$armorOpacityApplied;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"))
    private void mytheria$applyArmorOpacity(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        ArmorInvise module = getModule();
        if (module == null || !Boolean.TRUE.equals(module.getEnabled())) {
            mytheria$armorOpacityApplied = false;
            return;
        }

        if (!(state instanceof PlayerEntityRenderState playerState)) {
            mytheria$armorOpacityApplied = false;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || playerState.id == client.player.getId()) {
            mytheria$armorOpacityApplied = false;
            return;
        }

        float alpha = module.getArmorOpacity().getValue() / 255.0f;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        mytheria$armorOpacityApplied = true;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("TAIL"))
    private void mytheria$resetArmorOpacity(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!mytheria$armorOpacityApplied) {
            return;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        mytheria$armorOpacityApplied = false;
    }

    @Unique
    private ArmorInvise getModule() {
        Mytheria mytheria = Mytheria.getInstance();
        if (mytheria == null || mytheria.getModuleManager() == null) {
            return null;
        }
        return (ArmorInvise) mytheria.getModuleManager().find(ArmorInvise.class);
    }
}
