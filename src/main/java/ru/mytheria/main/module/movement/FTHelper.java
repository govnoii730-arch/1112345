package ru.mytheria.main.module.movement;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import ru.mytheria.api.events.impl.EventRender3D;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.module.settings.impl.ModeListSetting;
import ru.mytheria.api.module.settings.impl.SliderSetting;
import ru.mytheria.main.module.render.InterfaceStyle;

import java.util.ArrayList;
import java.util.List;

public class FTHelper extends Module {
    private static final String MODE_SNOWBALL = "\u0421\u043d\u0435\u0436\u043e\u043a";
    private static final int CIRCLE_SEGMENTS = 50;

    private final ModeListSetting helpers = new ModeListSetting(Text.of("\u0422\u0443\u043c\u0431\u043b\u0435\u0440\u044b"), null, () -> true)
            .set(MODE_SNOWBALL);
    private final ColorSetting color = new ColorSetting(Text.of("\u0426\u0432\u0435\u0442"), null, () -> true)
            .set(0xFFFFFFFF);
    private final SliderSetting opacity = new SliderSetting(Text.of("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c"), null, () -> true)
            .set(15.0f, 255.0f, 1.0f)
            .set(210.0f);
    private final SliderSetting lineWidth = new SliderSetting(Text.of("\u0416\u0438\u0440\u043d\u043e\u0441\u0442\u044c \u043b\u0438\u043d\u0438\u0438"), null, () -> true)
            .set(1.0f, 6.0f, 0.1f)
            .set(2.2f);

    public FTHelper() {
        super(Text.of("FTHelper"), null, Category.MOVEMENT);
        addSettings(helpers, color, opacity, lineWidth);

        if (helpers.get(MODE_SNOWBALL) != null) {
            helpers.get(MODE_SNOWBALL).set(true);
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (!Boolean.TRUE.equals(getEnabled()) || fullNullCheck()) {
            return;
        }

        int themedColor = InterfaceStyle.hudPrimaryColor(color.getColor());
        int renderColor = applyOpacity(themedColor, opacity.getValue());

        if (isEnabled(MODE_SNOWBALL) && isHolding(Items.SNOWBALL)) {
            renderSnowballCircles(event, renderColor);
        }
    }

    private void renderSnowballCircles(EventRender3D event, int renderColor) {
        Vec3d velocity = getProjectileVelocity(event, 1.5, 0.0);
        Vec3d startPos = getProjectileStart(event, velocity);
        PredictionResult prediction = simulate(
                mc.world,
                mc.player,
                startPos,
                velocity,
                0.03,
                0.99,
                80
        );

        if (!prediction.hitBlock()) {
            return;
        }

        Vec3d center = prediction.hitPos();
        renderCircle(event.getMatrixStack().peek().getPositionMatrix(), event.getCameraPos(), center, 3.5, renderColor, lineWidth.getValue());
        renderCircle(event.getMatrixStack().peek().getPositionMatrix(), event.getCameraPos(), center, 2.0, renderColor, lineWidth.getValue());
    }

    private Vec3d getProjectileStart(EventRender3D event, Vec3d directionVelocity) {
        Vec3d direction = directionVelocity.normalize();
        return mc.player.getCameraPosVec(event.getPartialTicks()).add(direction.multiply(0.25));
    }

    private Vec3d getProjectileVelocity(EventRender3D event, double speed, double pitchOffset) {
        float pitch = mc.player.getPitch(event.getPartialTicks());
        float yaw = mc.player.getYaw(event.getPartialTicks());
        Vec3d direction = Vec3d.fromPolar((float) (pitch + pitchOffset), yaw).normalize();
        return direction.multiply(speed);
    }

    private boolean isEnabled(String name) {
        return helpers.get(name) != null && helpers.get(name).getEnabled();
    }

    private boolean isHolding(net.minecraft.item.Item item) {
        return mc.player.getMainHandStack().isOf(item) || mc.player.getOffHandStack().isOf(item);
    }

    private PredictionResult simulate(net.minecraft.world.World world, Entity source, Vec3d startPos, Vec3d velocity, double gravity, double drag, int maxSteps) {
        List<Vec3d> points = new ArrayList<>();
        Vec3d currentPos = startPos;
        Vec3d currentVelocity = velocity;
        points.add(currentPos);

        for (int i = 0; i < maxSteps; i++) {
            Vec3d nextPos = currentPos.add(currentVelocity);
            BlockHitResult hitResult = world.raycast(new RaycastContext(
                    currentPos,
                    nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    source
            ));

            if (hitResult.getType() != HitResult.Type.MISS) {
                Vec3d hitPos = hitResult.getPos();
                points.add(hitPos);
                return new PredictionResult(points, hitPos, true);
            }

            currentPos = nextPos;
            points.add(currentPos);
            currentVelocity = currentVelocity.multiply(drag).add(0.0, -gravity, 0.0);

            if (currentPos.y < world.getBottomY() - 4.0) {
                break;
            }
        }

        return new PredictionResult(points, currentPos, false);
    }

    private void renderCircle(Matrix4f matrix, Vec3d cameraPos, Vec3d center, double radius, int color, float width) {
        setupLineState(width);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double angle1 = (Math.PI * 2.0 * i) / CIRCLE_SEGMENTS;
            double angle2 = (Math.PI * 2.0 * (i + 1)) / CIRCLE_SEGMENTS;

            Vec3d from = center.add(Math.cos(angle1) * radius, 0.03, Math.sin(angle1) * radius).subtract(cameraPos);
            Vec3d to = center.add(Math.cos(angle2) * radius, 0.03, Math.sin(angle2) * radius).subtract(cameraPos);
            vertex(builder, matrix, from, color);
            vertex(builder, matrix, to, color);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
        restoreLineState();
    }

    private void setupLineState(float width) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(Math.max(1.0f, width));
    }

    private void restoreLineState() {
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private void vertex(BufferBuilder builder, Matrix4f matrix, Vec3d point, int color) {
        builder.vertex(matrix, (float) point.x, (float) point.y, (float) point.z).color(color);
    }

    private int applyOpacity(int color, float alphaValue) {
        int alpha = Math.max(0, Math.min(255, Math.round(alphaValue)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private record PredictionResult(List<Vec3d> points, Vec3d hitPos, boolean hitBlock) {
    }
}
