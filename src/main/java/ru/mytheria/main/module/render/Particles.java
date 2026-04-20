package ru.mytheria.main.module.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.mytheria.api.events.EventPopTotem;
import ru.mytheria.api.events.impl.AuraEvents;
import ru.mytheria.api.events.impl.EventRender3D;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.module.settings.impl.ModeListSetting;
import ru.mytheria.api.module.settings.impl.ModeSetting;
import ru.mytheria.api.module.settings.impl.SliderSetting;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Particles extends Module {
    private static final long PARTICLE_LIFE_MS = 2600L;
    private static final long WORLD_SPAWN_INTERVAL_MS = 550L;
    private static final double WORLD_TRIGGER_MIN_RADIUS = 4.0;
    private static final double WORLD_TRIGGER_MAX_RADIUS = 12.0;

    private static final Identifier STAR = Identifier.of("mre", "images/particles/star.png");
    private static final Identifier SNOW = Identifier.of("mre", "images/particles/snow.png");
    private static final Identifier HEART = Identifier.of("mre", "images/particles/heart.png");
    private static final Identifier DOLLAR = Identifier.of("mre", "images/particles/dollar.png");

    private final CopyOnWriteArrayList<Particle> particles = new CopyOnWriteArrayList<>();

    private final ModeListSetting triggers = new ModeListSetting(Text.of("Триггеры"), null, () -> true)
            .set("Удар", "В мире", "Снос тотема");
    private final ModeSetting type = new ModeSetting(Text.of("Тип"), null, () -> true)
            .set("Звездочки", "Снежинки", "Сердца", "Доллары")
            .setDefault("Звездочки");
    private final SliderSetting count = new SliderSetting(Text.of("Кол-во"), null, () -> true)
            .set(1.0f, 5.0f, 1.0f)
            .set(2.0f);
    private final SliderSetting speed = new SliderSetting(Text.of("Скорость"), null, () -> true)
            .set(0.2f, 5.0f, 0.1f)
            .set(2.0f);
    private final SliderSetting size = new SliderSetting(Text.of("Размер"), null, () -> true)
            .set(0.15f, 1.25f, 0.05f)
            .set(0.42f);
    private final ColorSetting color = new ColorSetting(Text.of("Основной цвет"), null, () -> true)
            .set(0xFFFFFFFF);
    private final ColorSetting hitColor = new ColorSetting(Text.of("Цвет удара"), null, () -> true)
            .set(0xFFFF5555);

    private long lastWorldSpawnTime = 0L;

    public Particles() {
        super(Text.of("Particles"), Category.RENDER);
        addSettings(triggers, type, count, speed, size, color, hitColor);

        if (triggers.get("Удар") != null) {
            triggers.get("Удар").set(true);
        }
    }

    @EventHandler
    private void onAttack(AuraEvents.AttackEvent event) {
        if (!Boolean.TRUE.equals(getEnabled()) || !isTriggerEnabled("Удар")) {
            return;
        }

        if (event.entity instanceof LivingEntity livingEntity && event.entity != mc.player) {
            spawnAt(
                    livingEntity.getPos().add(0.0, livingEntity.getHeight() / 2.0f, 0.0),
                    getSpawnCount(),
                    themedParticleColor()
            );
        }
    }

    @EventHandler
    private void onPopTotem(EventPopTotem event) {
        if (!Boolean.TRUE.equals(getEnabled()) || !isTriggerEnabled("Снос тотема")) {
            return;
        }

        PlayerEntity player = event.getPlayer();
        if (player != null) {
            spawnAt(player.getPos().add(0.0, player.getHeight() / 2.0f, 0.0), getSpawnCount(), themedParticleColor());
        }
    }

    @EventHandler
    private void onRender3D(EventRender3D event) {
        if (!Boolean.TRUE.equals(getEnabled()) || mc.player == null || mc.world == null) {
            particles.clear();
            return;
        }

        spawnWorldParticlesIfNeeded();
        renderParticles(event);
    }

    private void spawnWorldParticlesIfNeeded() {
        if (!isTriggerEnabled("В мире")) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastWorldSpawnTime < WORLD_SPAWN_INTERVAL_MS) {
            return;
        }

        lastWorldSpawnTime = now;
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        double radius = ThreadLocalRandom.current().nextDouble(WORLD_TRIGGER_MIN_RADIUS, WORLD_TRIGGER_MAX_RADIUS);
        double x = mc.player.getX() + Math.cos(angle) * radius;
        double z = mc.player.getZ() + Math.sin(angle) * radius;
        double y = mc.player.getY() + ThreadLocalRandom.current().nextDouble(0.2, 2.0);

        spawnAt(new Vec3d(x, y, z), Math.max(1, (int) Math.ceil(getSpawnCount() * 0.5f)), themedParticleColor());
    }

    private void renderParticles(EventRender3D event) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        for (Particle particle : particles) {
            if (particle.isExpired()) {
                particles.remove(particle);
                continue;
            }

            particle.update(mc.world);

            float lifeProgress = 1.0f - ((float) (System.currentTimeMillis() - particle.time) / PARTICLE_LIFE_MS);
            if (lifeProgress <= 0.0f) {
                particles.remove(particle);
                continue;
            }

            float renderSize = size.getValue() * lifeProgress;
            Color particleColor = applyAlpha(particle.color, lifeProgress * particle.alpha);

            double relX = particle.pos.x - event.getCameraPos().x;
            double relY = particle.pos.y - event.getCameraPos().y;
            double relZ = particle.pos.z - event.getCameraPos().z;

            event.getMatrixStack().push();
            event.getMatrixStack().translate(relX, relY, relZ);
            event.getMatrixStack().multiply(event.getCamera().getRotation());
            event.getMatrixStack().scale(-renderSize, -renderSize, renderSize);

            drawParticleQuad(event.getMatrixStack().peek().getPositionMatrix(), getParticleTexture(), particleColor);

            event.getMatrixStack().pop();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawParticleQuad(Matrix4f matrix, Identifier texture, Color color) {
        RenderSystem.setShaderTexture(0, texture);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        int argb = color.getRGB();
        builder.vertex(matrix, -0.5f, -0.5f, 0.0f).texture(0.0f, 0.0f).color(argb);
        builder.vertex(matrix, -0.5f, 0.5f, 0.0f).texture(0.0f, 1.0f).color(argb);
        builder.vertex(matrix, 0.5f, 0.5f, 0.0f).texture(1.0f, 1.0f).color(argb);
        builder.vertex(matrix, 0.5f, -0.5f, 0.0f).texture(1.0f, 0.0f).color(argb);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private void spawnAt(Vec3d center, int amount, int particleColor) {
        for (int i = 0; i < amount; i++) {
            particles.add(new Particle(center, speed.getValue(), particleColor));
        }
    }

    private boolean isTriggerEnabled(String name) {
        return triggers.get(name) != null && triggers.get(name).getEnabled();
    }

    private int getSpawnCount() {
        return Math.max(1, Math.round(count.getValue()));
    }

    private Identifier getParticleTexture() {
        return switch (type.getValue()) {
            case "Доллары" -> DOLLAR;
            case "Снежинки" -> SNOW;
            case "Сердца" -> HEART;
            default -> STAR;
        };
    }

    private Color applyAlpha(int argb, float extraAlpha) {
        int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * extraAlpha)));
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, alpha);
    }

    private int themedParticleColor() {
        return InterfaceStyle.hudPrimaryColor(color.getColor());
    }

    private static final class Particle {
        private Vec3d pos;
        private Vec3d velocity;
        private final long time;
        private float alpha;
        private final float speedFactor;
        private final int color;

        private Particle(Vec3d pos, float speedFactor, int color) {
            this.pos = pos;
            this.time = System.currentTimeMillis();
            this.speedFactor = speedFactor;
            this.color = color;
            this.velocity = new Vec3d(
                    ThreadLocalRandom.current().nextDouble(-0.02, 0.02) * speedFactor,
                    ThreadLocalRandom.current().nextDouble(0.01, 0.02) * speedFactor,
                    ThreadLocalRandom.current().nextDouble(-0.02, 0.02) * speedFactor
            );
            this.alpha = 1.0f;
        }

        private void update(net.minecraft.world.World world) {
            long elapsed = System.currentTimeMillis() - time;
            float lifeFactor = MathHelper.clamp(elapsed / (float) PARTICLE_LIFE_MS, 0.0f, 1.0f);
            alpha = 1.0f - lifeFactor;
            velocity = velocity.add(0.0, -0.0001 * speedFactor, 0.0);

            Vec3d newPos = pos.add(velocity);
            BlockPos particlePos = BlockPos.ofFloored(newPos);
            BlockState blockState = world.getBlockState(particlePos);
            if (!blockState.isAir()) {
                if (!world.getBlockState(BlockPos.ofFloored(pos.x + velocity.x, pos.y, pos.z)).isAir()) {
                    velocity = new Vec3d(-velocity.x, velocity.y, velocity.z);
                }
                if (!world.getBlockState(BlockPos.ofFloored(pos.x, pos.y + velocity.y, pos.z)).isAir()) {
                    velocity = new Vec3d(velocity.x, -velocity.y * 0.5f * speedFactor, velocity.z);
                }
                if (!world.getBlockState(BlockPos.ofFloored(pos.x, pos.y, pos.z + velocity.z)).isAir()) {
                    velocity = new Vec3d(velocity.x, velocity.y, -velocity.z);
                }
                pos = pos.add(velocity);
            } else {
                pos = newPos;
            }
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - time > PARTICLE_LIFE_MS || alpha <= 0.0f;
        }
    }
}
