package ru.mytheria.main.module.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import ru.mytheria.api.events.impl.EventRender3D;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BooleanSetting;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.module.settings.impl.SliderSetting;

public class BlockOverlay extends Module {
    private final BooleanSetting corners = new BooleanSetting(Text.of("Углы"), null, () -> true)
            .set(false);
    private final BooleanSetting smooth = new BooleanSetting(Text.of("Плавный"), null, () -> true)
            .set(false);
    private final ColorSetting color = new ColorSetting(Text.of("Цвет"), null, () -> true)
            .set(0xFFFFFFFF);
    private final SliderSetting opacity = new SliderSetting(Text.of("Прозрачность"), null, () -> true)
            .set(15.0f, 255.0f, 1.0f)
            .set(220.0f);
    private final SliderSetting lineWidth = new SliderSetting(Text.of("Жирность"), null, () -> true)
            .set(1.0f, 6.0f, 0.1f)
            .set(2.0f);

    public BlockOverlay() {
        super(Text.of("BlockOverlay"), null, Category.RENDER);
        addSettings(corners, smooth, color, opacity, lineWidth);
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (!Boolean.TRUE.equals(getEnabled()) || mc.world == null || mc.player == null) {
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos, ShapeContext.absent());
        if (shape.isEmpty()) {
            return;
        }

        int alpha = Math.max(0, Math.min(255, Math.round(opacity.getValue())));
        int baseColor = withAlpha(InterfaceStyle.hudPrimaryColor(color.getColor()), alpha);

        float width = lineWidth.getValue();
        boolean onlyCorners = corners.getEnabled();
        boolean isSmooth = smooth.getEnabled();

        if (isSmooth) {
            int softColor = scaleAlpha(baseColor, 0.30f);
            renderShapeEdges(
                    event.getMatrixStack().peek().getPositionMatrix(),
                    event.getCameraPos(),
                    pos,
                    shape,
                    softColor,
                    width * 2.0f,
                    onlyCorners
            );

            int midColor = scaleAlpha(baseColor, 0.62f);
            renderShapeEdges(
                    event.getMatrixStack().peek().getPositionMatrix(),
                    event.getCameraPos(),
                    pos,
                    shape,
                    midColor,
                    width * 1.45f,
                    onlyCorners
            );
        }

        renderShapeEdges(
                event.getMatrixStack().peek().getPositionMatrix(),
                event.getCameraPos(),
                pos,
                shape,
                baseColor,
                width,
                onlyCorners
        );
    }

    private void renderShapeEdges(Matrix4f matrix, Vec3d cameraPos, BlockPos origin, VoxelShape shape, int color, float width, boolean onlyCorners) {
        setupLineState(width);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        shape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) -> {
            float x1 = (float) (origin.getX() + minX - cameraPos.x);
            float y1 = (float) (origin.getY() + minY - cameraPos.y);
            float z1 = (float) (origin.getZ() + minZ - cameraPos.z);
            float x2 = (float) (origin.getX() + maxX - cameraPos.x);
            float y2 = (float) (origin.getY() + maxY - cameraPos.y);
            float z2 = (float) (origin.getZ() + maxZ - cameraPos.z);

            if (!onlyCorners) {
                builder.vertex(matrix, x1, y1, z1).color(color);
                builder.vertex(matrix, x2, y2, z2).color(color);
                return;
            }

            float dx = x2 - x1;
            float dy = y2 - y1;
            float dz = z2 - z1;
            float length = (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length <= 1.0E-4f) {
                return;
            }

            float cornerLength = java.lang.Math.min(length * 0.30f, 0.24f);
            float nx = dx / length;
            float ny = dy / length;
            float nz = dz / length;

            float sx = x1 + nx * cornerLength;
            float sy = y1 + ny * cornerLength;
            float sz = z1 + nz * cornerLength;
            float ex = x2 - nx * cornerLength;
            float ey = y2 - ny * cornerLength;
            float ez = z2 - nz * cornerLength;

            builder.vertex(matrix, x1, y1, z1).color(color);
            builder.vertex(matrix, sx, sy, sz).color(color);
            builder.vertex(matrix, ex, ey, ez).color(color);
            builder.vertex(matrix, x2, y2, z2).color(color);
        });

        BufferRenderer.drawWithGlobalProgram(builder.end());
        restoreLineState();
    }

    private void setupLineState(float width) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(java.lang.Math.max(1.0f, width));
    }

    private void restoreLineState() {
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private int scaleAlpha(int color, float multiplier) {
        int alpha = (int) (((color >>> 24) & 0xFF) * MathHelper.clamp(multiplier, 0.0f, 1.0f));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
