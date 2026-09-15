#version 330

// Vanilla's core/block vertex shader with a sway added before projection.
//
// The displacement is driven by the weights the mod computes per vertex from Sway's anchor for that
// plant: 0 where it is held and 1 at its free end. That is what makes a ground plant bend from its
// base, a hanging vine from the ceiling, and a tall stack move as one piece. The sway itself lives in
// sway.glsl, which the copy of a shader pack's vertex shader the mod draws with reads too.

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
// The mod's two per-vertex values; see sway.glsl.
in float SwayCell;
in float SwayWeights;

// Settings the player changes in game, and the pushes on the plants near the player, written by the mod into
// buffers so they apply on the next frame. See sway.glsl for what each holds.
layout(std140) uniform FoliageSway {
    float mc2_SwayIntensity;
    float mc2_CalmSway;
    vec4 mc2_SwayEdge;
    vec4 mc2_Weather;
};

#define MC2_MAX_CELLS 128
layout(std140) uniform FoliageInteraction {
    int mc2_CellCount;
    vec4 mc2_CellBoundsMin;
    vec4 mc2_CellBoundsMax;
    vec4 mc2_CellPosition[MC2_MAX_CELLS];
    vec4 mc2_CellForce[MC2_MAX_CELLS];
};

#moj_import <mc2_interactivefoliage:sway.glsl>

uniform sampler2D Sampler2;

// Reading the lightmap, spelled out rather than imported: the file vanilla keeps it in only exists from
// 26.1.2 on, and older versions write these same three lines into each shader that needs them.
vec4 mc2_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    // Relative to the camera: ModelOffset is the region's corner relative to it.
    vec3 pos = mc2_sway(Position + ModelOffset, ModelOffset, CameraBlockPos, CameraOffset, GameTime,
            SwayCell, SwayWeights);

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * mc2_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
