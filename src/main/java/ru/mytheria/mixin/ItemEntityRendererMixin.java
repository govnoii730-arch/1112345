package ru.mytheria.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.Mytheria;
import ru.mytheria.main.module.render.ItemPhysic;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F"
            )
    )
    private float mytheria$disableVanillaSpin(float age, float uniqueOffset) {
        return isItemPhysicEnabled() ? 0.0f : net.minecraft.entity.ItemEntity.getRotation(age, uniqueOffset);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;renderStack(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;)V"
            )
    )
    private void mytheria$applyItemPhysics(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!isItemPhysicEnabled()) {
            return;
        }

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        matrices.translate(0.0f, 0.0f, -0.02f);
    }

    private boolean isItemPhysicEnabled() {
        if (Mytheria.getInstance() == null || Mytheria.getInstance().getModuleManager() == null) {
            return false;
        }

        ItemPhysic module = (ItemPhysic) Mytheria.getInstance().getModuleManager().find(ItemPhysic.class);
        return module != null && Boolean.TRUE.equals(module.getEnabled());
    }
}
