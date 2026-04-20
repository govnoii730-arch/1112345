package ru.mytheria.mixin;

import net.minecraft.client.render.*;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.Mytheria;
import ru.mytheria.api.events.impl.EventRender3D;
import ru.mytheria.main.module.render.BlockOverlay;

import static ru.mytheria.api.clientannotation.QuickImport.mc;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean mytheria$disableVanillaOutlineWhenOverlayOn(boolean renderBlockOutline) {
        Mytheria mytheria = Mytheria.getInstance();
        if (mytheria == null || mytheria.getModuleManager() == null) {
            return renderBlockOutline;
        }

        BlockOverlay overlay = (BlockOverlay) mytheria.getModuleManager().find(BlockOverlay.class);
        if (overlay != null && Boolean.TRUE.equals(overlay.getEnabled())) {
            return false;
        }

        return renderBlockOutline;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(
            ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci
    ) {

        VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();

        MatrixStack matrices = new MatrixStack();
        matrices.loadIdentity();
        matrices.multiplyPositionMatrix(positionMatrix);

        float tickDelta = tickCounter.getTickDelta(true);

        Mytheria.getInstance().eventProvider.post(
                new EventRender3D(matrices, tickDelta, vertexConsumers, camera)
        );

        vertexConsumers.draw();
    }
}
