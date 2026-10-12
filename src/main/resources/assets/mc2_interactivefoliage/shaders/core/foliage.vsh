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
#ifdef MC2_NO_UNIFORM_ARRAYS
// For a renderer whose shader converter takes no arrays in a uniform block (VulkanMod's): the same bytes, each array
// of 128 vec4 laid out as 32 mat4, whose columns are those vec4 in the same order, read through the two functions
// sway.glsl asks for.
layout(std140) uniform FoliageInteraction {
    int mc2_CellCount;
    vec4 mc2_CellBoundsMin;
    vec4 mc2_CellBoundsMax;
    mat4 mc2_CellPosition0;
    mat4 mc2_CellPosition1;
    mat4 mc2_CellPosition2;
    mat4 mc2_CellPosition3;
    mat4 mc2_CellPosition4;
    mat4 mc2_CellPosition5;
    mat4 mc2_CellPosition6;
    mat4 mc2_CellPosition7;
    mat4 mc2_CellPosition8;
    mat4 mc2_CellPosition9;
    mat4 mc2_CellPosition10;
    mat4 mc2_CellPosition11;
    mat4 mc2_CellPosition12;
    mat4 mc2_CellPosition13;
    mat4 mc2_CellPosition14;
    mat4 mc2_CellPosition15;
    mat4 mc2_CellPosition16;
    mat4 mc2_CellPosition17;
    mat4 mc2_CellPosition18;
    mat4 mc2_CellPosition19;
    mat4 mc2_CellPosition20;
    mat4 mc2_CellPosition21;
    mat4 mc2_CellPosition22;
    mat4 mc2_CellPosition23;
    mat4 mc2_CellPosition24;
    mat4 mc2_CellPosition25;
    mat4 mc2_CellPosition26;
    mat4 mc2_CellPosition27;
    mat4 mc2_CellPosition28;
    mat4 mc2_CellPosition29;
    mat4 mc2_CellPosition30;
    mat4 mc2_CellPosition31;
    mat4 mc2_CellForce0;
    mat4 mc2_CellForce1;
    mat4 mc2_CellForce2;
    mat4 mc2_CellForce3;
    mat4 mc2_CellForce4;
    mat4 mc2_CellForce5;
    mat4 mc2_CellForce6;
    mat4 mc2_CellForce7;
    mat4 mc2_CellForce8;
    mat4 mc2_CellForce9;
    mat4 mc2_CellForce10;
    mat4 mc2_CellForce11;
    mat4 mc2_CellForce12;
    mat4 mc2_CellForce13;
    mat4 mc2_CellForce14;
    mat4 mc2_CellForce15;
    mat4 mc2_CellForce16;
    mat4 mc2_CellForce17;
    mat4 mc2_CellForce18;
    mat4 mc2_CellForce19;
    mat4 mc2_CellForce20;
    mat4 mc2_CellForce21;
    mat4 mc2_CellForce22;
    mat4 mc2_CellForce23;
    mat4 mc2_CellForce24;
    mat4 mc2_CellForce25;
    mat4 mc2_CellForce26;
    mat4 mc2_CellForce27;
    mat4 mc2_CellForce28;
    mat4 mc2_CellForce29;
    mat4 mc2_CellForce30;
    mat4 mc2_CellForce31;
};

vec4 mc2_cellPosition(int i) {
    int field = i / 4;
    int column = i - field * 4;
    switch (field) {
        case 0: return mc2_CellPosition0[column];
        case 1: return mc2_CellPosition1[column];
        case 2: return mc2_CellPosition2[column];
        case 3: return mc2_CellPosition3[column];
        case 4: return mc2_CellPosition4[column];
        case 5: return mc2_CellPosition5[column];
        case 6: return mc2_CellPosition6[column];
        case 7: return mc2_CellPosition7[column];
        case 8: return mc2_CellPosition8[column];
        case 9: return mc2_CellPosition9[column];
        case 10: return mc2_CellPosition10[column];
        case 11: return mc2_CellPosition11[column];
        case 12: return mc2_CellPosition12[column];
        case 13: return mc2_CellPosition13[column];
        case 14: return mc2_CellPosition14[column];
        case 15: return mc2_CellPosition15[column];
        case 16: return mc2_CellPosition16[column];
        case 17: return mc2_CellPosition17[column];
        case 18: return mc2_CellPosition18[column];
        case 19: return mc2_CellPosition19[column];
        case 20: return mc2_CellPosition20[column];
        case 21: return mc2_CellPosition21[column];
        case 22: return mc2_CellPosition22[column];
        case 23: return mc2_CellPosition23[column];
        case 24: return mc2_CellPosition24[column];
        case 25: return mc2_CellPosition25[column];
        case 26: return mc2_CellPosition26[column];
        case 27: return mc2_CellPosition27[column];
        case 28: return mc2_CellPosition28[column];
        case 29: return mc2_CellPosition29[column];
        case 30: return mc2_CellPosition30[column];
        case 31: return mc2_CellPosition31[column];
    }
    return vec4(0.0);
}

vec4 mc2_cellForce(int i) {
    int field = i / 4;
    int column = i - field * 4;
    switch (field) {
        case 0: return mc2_CellForce0[column];
        case 1: return mc2_CellForce1[column];
        case 2: return mc2_CellForce2[column];
        case 3: return mc2_CellForce3[column];
        case 4: return mc2_CellForce4[column];
        case 5: return mc2_CellForce5[column];
        case 6: return mc2_CellForce6[column];
        case 7: return mc2_CellForce7[column];
        case 8: return mc2_CellForce8[column];
        case 9: return mc2_CellForce9[column];
        case 10: return mc2_CellForce10[column];
        case 11: return mc2_CellForce11[column];
        case 12: return mc2_CellForce12[column];
        case 13: return mc2_CellForce13[column];
        case 14: return mc2_CellForce14[column];
        case 15: return mc2_CellForce15[column];
        case 16: return mc2_CellForce16[column];
        case 17: return mc2_CellForce17[column];
        case 18: return mc2_CellForce18[column];
        case 19: return mc2_CellForce19[column];
        case 20: return mc2_CellForce20[column];
        case 21: return mc2_CellForce21[column];
        case 22: return mc2_CellForce22[column];
        case 23: return mc2_CellForce23[column];
        case 24: return mc2_CellForce24[column];
        case 25: return mc2_CellForce25[column];
        case 26: return mc2_CellForce26[column];
        case 27: return mc2_CellForce27[column];
        case 28: return mc2_CellForce28[column];
        case 29: return mc2_CellForce29[column];
        case 30: return mc2_CellForce30[column];
        case 31: return mc2_CellForce31[column];
    }
    return vec4(0.0);
}

// Under that renderer the mesher may write a block's id for its shader renderer above the light; see BerylFoliageShader.
ivec2 mc2_light(ivec2 uv) {
    return uv & 255;
}
#else
layout(std140) uniform FoliageInteraction {
    int mc2_CellCount;
    vec4 mc2_CellBoundsMin;
    vec4 mc2_CellBoundsMax;
    vec4 mc2_CellPosition[MC2_MAX_CELLS];
    vec4 mc2_CellForce[MC2_MAX_CELLS];
};

ivec2 mc2_light(ivec2 uv) {
    return uv;
}
#endif

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
    vertexColor = Color * mc2_sample_lightmap(Sampler2, mc2_light(UV2));
    texCoord0 = UV0;
}
