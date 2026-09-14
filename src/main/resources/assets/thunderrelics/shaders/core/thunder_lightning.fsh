#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float GameTime;

in float vertexDistance;
in vec4 vertexColor;
in vec3 localPosition;

out vec4 fragColor;

void main() {
    if (vertexColor.a <= 0.001) {
        discard;
    }
    float phase = GameTime * 3769.911 + dot(localPosition, vec3(2.7, 1.9, 2.4));
    float shimmer = 0.93 + 0.07 * sin(phase);
    float luminance = dot(vertexColor.rgb, vec3(0.25, 0.45, 0.30));
    float coreBoost = 1.0 + 0.18 * smoothstep(0.45, 0.95, luminance);
    vec3 rgb = vertexColor.rgb * shimmer * coreBoost;
    float alpha = vertexColor.a * (0.94 + 0.06 * shimmer);
    vec4 color = vec4(rgb, alpha) * ColorModulator;
    fragColor = color * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}
