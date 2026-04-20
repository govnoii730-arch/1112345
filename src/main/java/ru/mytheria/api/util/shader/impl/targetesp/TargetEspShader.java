package ru.mytheria.api.util.shader.impl.targetesp;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import ru.mytheria.api.util.shader.common.event.IRenderer;
import ru.mytheria.api.util.shader.common.providers.ResourceProvider;
import net.minecraft.util.Util;

public record TargetEspShader(
        float width,
        float height,
        int color
) implements IRenderer {

    private static final ShaderProgramKey TARGET_ESP_SHADER_KEY = new ShaderProgramKey(
            ResourceProvider.getShaderIdentifier("target_esp"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        ShaderProgram shader = RenderSystem.setShader(TARGET_ESP_SHADER_KEY);
        if (shader == null) {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            return;
        }

        shader.getUniform("Time").set((Util.getMeasuringTimeMs() / 1000.0f) * 0.1f);

        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        float a = (float) (color >> 24 & 255) / 255.0F;

        shader.getUniform("Color").set(r, g, b, a);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        builder.vertex(matrix, x, y, z);
        builder.vertex(matrix, x, y + height, z);
        builder.vertex(matrix, x + width, y + height, z);
        builder.vertex(matrix, x + width, y, z);

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
