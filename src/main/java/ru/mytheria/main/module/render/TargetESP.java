package ru.mytheria.main.module.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.mytheria.api.events.impl.AuraEvents;
import ru.mytheria.api.events.impl.EventRender3D;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.module.settings.impl.ModeSetting;
import ru.mytheria.api.module.settings.impl.SliderSetting;

public class TargetESP extends Module {
    private static final float ORBIT_RADIUS_PADDING = 0.45f;
    private static final float DIAMOND_WIDTH = 0.18f;
    private static final float DIAMOND_HEIGHT = 0.30f;
    private static final float CUBE_SIZE = 0.22f;
    private static final float ARROW_LENGTH = 0.42f;
    private static final float ARROW_HEAD_WIDTH = 0.24f;
    private static final float ARROW_SHAFT_WIDTH = 0.10f;
    private static final float VISIBILITY_SPEED = 8.5f;
    private static final long HIT_COLOR_DURATION_MS = 420L;

    private final ModeSetting mode = new ModeSetting(Text.of("Режим"), null, () -> true)
            .setDefault("Ромбы")
            .set("Ромбы", "Кубы", "Стрелки");
    private final SliderSetting count = new SliderSetting(Text.of("Количество"), null, () -> true)
            .set(1.0f, 12.0f, 1.0f)
            .set(4.0f);
    private final ColorSetting color = new ColorSetting(Text.of("Цвет"), null, () -> true)
            .set(0xFFFFFFFF);
    private final ColorSetting hitColor = new ColorSetting(Text.of("Цвет удара"), null, () -> true)
            .set(0xFFFF5555);
    private final SliderSetting opacity = new SliderSetting(Text.of("Непрозрачность"), null, () -> true)
            .set(0.0f, 255.0f, 1.0f)
            .set(255.0f);

    private Vec3d lastTargetPos = Vec3d.ZERO;
    private float lastTargetHeight = 0.0f;
    private float lastTargetWidth = 0.0f;
    private float visibilityAlpha = 0.0f;
    private float orbitTime = 0.0f;
    private long lastFrameTimeMs = -1L;
    private int lastHitTargetId = Integer.MIN_VALUE;
    private long lastHitTimeMs = -1L;

    public TargetESP() {
        super(Text.of("TargetESP"), Category.RENDER);
        addSettings(mode, count, color, hitColor, opacity);
    }

    @EventHandler
    private void onAttack(AuraEvents.AttackEvent event) {
        if (event.entity instanceof LivingEntity livingEntity && event.entity != mc.player) {
            lastHitTargetId = livingEntity.getId();
            lastHitTimeMs = Util.getMeasuringTimeMs();
        }
    }

    @EventHandler
    private void onRender3D(EventRender3D event) {
        if (!Boolean.TRUE.equals(getEnabled())) {
            return;
        }

        float deltaSeconds = getFrameDeltaSeconds();
        orbitTime += deltaSeconds * 1.6f;

        LivingEntity target = getTarget();
        if (target != null) {
            float tickDelta = event.getPartialTicks();
            double targetX = MathHelper.lerp(tickDelta, target.prevX, target.getX());
            double targetY = MathHelper.lerp(tickDelta, target.prevY, target.getY());
            double targetZ = MathHelper.lerp(tickDelta, target.prevZ, target.getZ());

            lastTargetPos = new Vec3d(targetX, targetY, targetZ);
            lastTargetHeight = target.getHeight();
            lastTargetWidth = target.getWidth();
        }

        visibilityAlpha = approach(visibilityAlpha, target != null ? 1.0f : 0.0f, deltaSeconds, VISIBILITY_SPEED);
        if (visibilityAlpha <= 0.01f || lastTargetHeight <= 0.0f || lastTargetWidth <= 0.0f) {
            return;
        }

        Vec3d cameraPos = event.getCameraPos();
        int renderColor = scaleAlpha(resolveShapeColor(target), visibilityAlpha);

        renderOrbit(
                event.getMatrixStack(),
                lastTargetPos,
                lastTargetWidth,
                lastTargetHeight,
                cameraPos,
                lastTargetPos.x - cameraPos.x,
                lastTargetPos.y - cameraPos.y,
                lastTargetPos.z - cameraPos.z,
                renderColor
        );
    }

    private void renderOrbit(MatrixStack matrices, Vec3d targetPos, float targetWidth, float targetHeight, Vec3d cameraPos,
                             double relativeX, double relativeY, double relativeZ, int baseColor) {
        int shapeCount = Math.max(1, Math.round(count.getValue()));
        float orbitRadius = Math.max(targetWidth * 0.9f, 0.55f) + ORBIT_RADIUS_PADDING;
        float time = orbitTime;
        Vec3d targetCenter = targetPos.add(0.0, targetHeight * 0.5f, 0.0);

        String modeValue = mode.getValue();
        boolean cubesMode = "Кубы".equals(modeValue) || "РљСѓР±С‹".equals(modeValue);
        boolean arrowsMode = "Стрелки".equals(modeValue) || "РЎС‚СЂРµР»РєРё".equals(modeValue);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        for (int i = 0; i < shapeCount; i++) {
            float progress = (float) i / shapeCount;
            float orbitAngle = (float) (progress * Math.PI * 2.0 + time);
            float bobAngle = time * 2.4f + progress * 5.0f;

            float localX = MathHelper.cos(orbitAngle) * orbitRadius;
            float localZ = MathHelper.sin(orbitAngle) * orbitRadius;
            float localY = targetHeight * 0.5f + MathHelper.sin(bobAngle) * (targetHeight * 0.18f + 0.08f);
            Vec3d worldShapePos = targetPos.add(localX, localY, localZ);
            float pulse = 0.85f + (MathHelper.sin(time * 3.2f + i) + 1.0f) * 0.12f;

            float shapeExtent = arrowsMode
                    ? ARROW_LENGTH * pulse
                    : cubesMode
                    ? CUBE_SIZE * pulse
                    : Math.max(DIAMOND_WIDTH, DIAMOND_HEIGHT) * pulse;

            if (isOccludedByTarget(targetWidth, targetHeight, targetCenter, worldShapePos, cameraPos, shapeExtent)) {
                continue;
            }

            Vec3d inwardDirection = targetCenter.subtract(worldShapePos);
            if (!cubesMode && inwardDirection.lengthSquared() <= 1.0E-6) {
                continue;
            }

            Vec3d normalizedDirection = inwardDirection.lengthSquared() <= 1.0E-6 ? Vec3d.ZERO : inwardDirection.normalize();
            float yaw = (float) Math.atan2(normalizedDirection.x, normalizedDirection.z);
            float pitch = (float) -Math.asin(MathHelper.clamp((float) normalizedDirection.y, -1.0f, 1.0f));

            matrices.push();
            matrices.translate(relativeX + localX, relativeY + localY, relativeZ + localZ);

            if (arrowsMode) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
                drawArrow(
                        matrices.peek().getPositionMatrix(),
                        ARROW_LENGTH * pulse,
                        ARROW_HEAD_WIDTH * pulse,
                        ARROW_SHAFT_WIDTH * pulse,
                        baseColor
                );
            } else if (cubesMode) {
                float spin = time * 4.0f + progress * 6.2831855f;
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(spin));
                matrices.multiply(RotationAxis.POSITIVE_X.rotation(spin * 0.6f));
                drawCube(matrices.peek().getPositionMatrix(), CUBE_SIZE * pulse, baseColor);
            } else {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
                drawDiamond(matrices.peek().getPositionMatrix(), DIAMOND_WIDTH * pulse, DIAMOND_HEIGHT * pulse, baseColor);
            }
            matrices.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private boolean isOccludedByTarget(float targetWidth, float targetHeight, Vec3d targetCenter, Vec3d shapePos, Vec3d cameraPos, float shapeExtent) {
        Vec3d viewDirection = targetCenter.subtract(cameraPos);
        if (viewDirection.lengthSquared() < 1.0E-6) {
            return true;
        }

        Vec3d offset = shapePos.subtract(targetCenter);
        viewDirection = viewDirection.normalize();

        double depth = offset.dotProduct(viewDirection);
        if (depth <= 0.0) {
            return false;
        }

        Vec3d lateralOffset = offset.subtract(viewDirection.multiply(depth));
        double silhouetteRadius = Math.max(targetWidth * 0.34f, 0.18f) + shapeExtent * 0.08f;
        double verticalLimit = targetHeight * 0.44f + shapeExtent * 0.18f;

        if (Math.abs(offset.y) > verticalLimit) {
            return false;
        }

        return lateralOffset.lengthSquared() <= silhouetteRadius * silhouetteRadius;
    }

    private void drawArrow(Matrix4f matrix, float length, float headWidth, float shaftWidth, int color) {
        float tipZ = length * 0.5f;
        float tailZ = -length * 0.5f;
        float headBaseZ = tipZ - length * 0.44f;
        float headHalf = headWidth * 0.5f;
        float shaftHalf = shaftWidth * 0.5f;

        int tipColor = scaleAlpha(color, 1.0f);
        int headColor = scaleAlpha(color, 0.82f);
        int shaftColor = scaleAlpha(color, 0.62f);
        int backColor = scaleAlpha(color, 0.46f);

        BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        triangle(fill, matrix, 0.0f, 0.0f, tipZ, -headHalf, -headHalf, headBaseZ, headHalf, -headHalf, headBaseZ, tipColor, headColor, headColor);
        triangle(fill, matrix, 0.0f, 0.0f, tipZ, headHalf, -headHalf, headBaseZ, headHalf, headHalf, headBaseZ, tipColor, headColor, headColor);
        triangle(fill, matrix, 0.0f, 0.0f, tipZ, headHalf, headHalf, headBaseZ, -headHalf, headHalf, headBaseZ, tipColor, headColor, headColor);
        triangle(fill, matrix, 0.0f, 0.0f, tipZ, -headHalf, headHalf, headBaseZ, -headHalf, -headHalf, headBaseZ, tipColor, headColor, headColor);
        quad(fill, matrix, -headHalf, -headHalf, headBaseZ, headHalf, -headHalf, headBaseZ, headHalf, headHalf, headBaseZ, -headHalf, headHalf, headBaseZ, headColor);

        quad(fill, matrix, -shaftHalf, shaftHalf, tailZ, shaftHalf, shaftHalf, tailZ, shaftHalf, shaftHalf, headBaseZ, -shaftHalf, shaftHalf, headBaseZ, shaftColor);
        quad(fill, matrix, -shaftHalf, -shaftHalf, tailZ, shaftHalf, -shaftHalf, tailZ, shaftHalf, -shaftHalf, headBaseZ, -shaftHalf, -shaftHalf, headBaseZ, shaftColor);
        quad(fill, matrix, -shaftHalf, -shaftHalf, tailZ, -shaftHalf, shaftHalf, tailZ, -shaftHalf, shaftHalf, headBaseZ, -shaftHalf, -shaftHalf, headBaseZ, shaftColor);
        quad(fill, matrix, shaftHalf, -shaftHalf, tailZ, shaftHalf, shaftHalf, tailZ, shaftHalf, shaftHalf, headBaseZ, shaftHalf, -shaftHalf, headBaseZ, shaftColor);
        quad(fill, matrix, -shaftHalf, -shaftHalf, tailZ, shaftHalf, -shaftHalf, tailZ, shaftHalf, shaftHalf, tailZ, -shaftHalf, shaftHalf, tailZ, backColor);
        BufferRenderer.drawWithGlobalProgram(fill.end());
    }

    private void drawDiamond(Matrix4f matrix, float width, float height, int color) {
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;
        int topColor = scaleAlpha(color, 1.0f);
        int midColor = scaleAlpha(color, 0.65f);
        int bottomColor = scaleAlpha(color, 0.42f);

        BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        triangle(fill, matrix, 0.0f, height, 0.0f, -halfWidth, 0.0f, 0.0f, 0.0f, 0.0f, halfWidth, topColor, midColor, midColor);
        triangle(fill, matrix, 0.0f, height, 0.0f, 0.0f, 0.0f, halfWidth, halfWidth, 0.0f, 0.0f, topColor, midColor, midColor);
        triangle(fill, matrix, 0.0f, height, 0.0f, halfWidth, 0.0f, 0.0f, 0.0f, 0.0f, -halfWidth, topColor, midColor, midColor);
        triangle(fill, matrix, 0.0f, height, 0.0f, 0.0f, 0.0f, -halfWidth, -halfWidth, 0.0f, 0.0f, topColor, midColor, midColor);

        triangle(fill, matrix, 0.0f, -halfHeight, 0.0f, 0.0f, 0.0f, halfWidth, -halfWidth, 0.0f, 0.0f, bottomColor, midColor, midColor);
        triangle(fill, matrix, 0.0f, -halfHeight, 0.0f, halfWidth, 0.0f, 0.0f, 0.0f, 0.0f, halfWidth, bottomColor, midColor, midColor);
        triangle(fill, matrix, 0.0f, -halfHeight, 0.0f, 0.0f, 0.0f, -halfWidth, halfWidth, 0.0f, 0.0f, bottomColor, midColor, midColor);
        triangle(fill, matrix, 0.0f, -halfHeight, 0.0f, -halfWidth, 0.0f, 0.0f, 0.0f, 0.0f, -halfWidth, bottomColor, midColor, midColor);
        BufferRenderer.drawWithGlobalProgram(fill.end());
    }

    private void drawCube(Matrix4f matrix, float size, int color) {
        float h = size * 0.5f;
        int topColor = scaleAlpha(color, 1.0f);
        int sideColor = scaleAlpha(color, 0.7f);
        int bottomColor = scaleAlpha(color, 0.5f);

        BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        quad(fill, matrix, -h, h, -h, h, h, -h, h, h, h, -h, h, h, topColor);
        quad(fill, matrix, -h, -h, h, h, -h, h, h, -h, -h, -h, -h, -h, bottomColor);
        quad(fill, matrix, -h, -h, h, -h, h, h, h, h, h, h, -h, h, sideColor);
        quad(fill, matrix, h, -h, -h, h, h, -h, -h, h, -h, -h, -h, -h, sideColor);
        quad(fill, matrix, -h, -h, -h, -h, h, -h, -h, h, h, -h, -h, h, sideColor);
        quad(fill, matrix, h, -h, h, h, h, h, h, h, -h, h, -h, -h, sideColor);
        BufferRenderer.drawWithGlobalProgram(fill.end());
    }

    private void quad(BufferBuilder builder, Matrix4f matrix,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      int color) {
        triangle(builder, matrix, x1, y1, z1, x2, y2, z2, x3, y3, z3, color, color, color);
        triangle(builder, matrix, x1, y1, z1, x3, y3, z3, x4, y4, z4, color, color, color);
    }

    private void triangle(BufferBuilder builder, Matrix4f matrix,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          int c1, int c2, int c3) {
        vertex(builder, matrix, x1, y1, z1, c1);
        vertex(builder, matrix, x2, y2, z2, c2);
        vertex(builder, matrix, x3, y3, z3, c3);
    }

    private void vertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, int color) {
        builder.vertex(matrix, x, y, z).color(color);
    }

    private int scaleAlpha(int color, float factor) {
        int alpha = color >>> 24;
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * factor)));
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }

    private int resolveShapeColor(LivingEntity target) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity.getValue())));
        int defaultColor = ((alpha & 0xFF) << 24) | (InterfaceStyle.hudPrimaryColor(color.getColor()) & 0x00FFFFFF);

        if (target == null || target.getId() != lastHitTargetId || lastHitTimeMs < 0L) {
            return defaultColor;
        }

        long elapsed = Util.getMeasuringTimeMs() - lastHitTimeMs;
        if (elapsed >= HIT_COLOR_DURATION_MS) {
            return defaultColor;
        }

        float progress = MathHelper.clamp(elapsed / (float) HIT_COLOR_DURATION_MS, 0.0f, 1.0f);
        int hit = ((alpha & 0xFF) << 24) | (InterfaceStyle.hudPrimaryColor(hitColor.getColor()) & 0x00FFFFFF);
        return blendColors(hit, defaultColor, progress);
    }

    private int blendColors(int from, int to, float progress) {
        float clamped = MathHelper.clamp(progress, 0.0f, 1.0f);
        int a = Math.round(MathHelper.lerp(clamped, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF));
        int r = Math.round(MathHelper.lerp(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(MathHelper.lerp(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(MathHelper.lerp(clamped, from & 0xFF, to & 0xFF));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float approach(float current, float target, float deltaSeconds, float speed) {
        float factor = 1.0f - (float) Math.exp(-speed * deltaSeconds);
        return MathHelper.lerp(factor, current, target);
    }

    private float getFrameDeltaSeconds() {
        long now = Util.getMeasuringTimeMs();
        if (lastFrameTimeMs < 0L) {
            lastFrameTimeMs = now;
            return 1.0f / 60.0f;
        }

        float delta = (now - lastFrameTimeMs) / 1000.0f;
        lastFrameTimeMs = now;
        return MathHelper.clamp(delta, 1.0f / 240.0f, 0.05f);
    }

    private LivingEntity getTarget() {
        if (!(mc.targetedEntity instanceof LivingEntity target)) {
            return null;
        }
        if (mc.player == null || target == mc.player || !target.isAlive()) {
            return null;
        }
        return target;
    }
}
