#version 150

#moj_import <mre:common.glsl>

in vec4 vertexColor;
out vec4 fragColor;

uniform float Time;
uniform vec4 Color;

void main() {
    float alpha = (sin(Time * 2.0) + 1.0) / 2.0; // Pulsating alpha
    alpha = 0.5 + alpha * 0.5; // Make it pulsate between 0.5 and 1.0

    fragColor = vec4(Color.rgb, Color.a * alpha);
}
