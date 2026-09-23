#version 330

// Not a whole shader: what the mod adds to the terrain vertex shader in use -- vanilla's, or a resource pack's own -- so
// foliage is lit and coloured exactly as the rest of the terrain. TerrainFoliageShader splices it in where that shader
// reads Position, and runs the shader's own main with the swayed position in its place. This is the 26.3 and up copy,
// where shaders include rather than import and every input names its location; see foliage_terrain.vsh for the older one.

// Where the terrain shader is drawn from and when, and where the section it belongs to is: what the sway is worked out
// from. The terrain's own shader includes both, and an include is read once however many times it is asked for.
#include <minecraft:globals.glsl>
#include <minecraft:chunksection.glsl>

// After the block's own four inputs, which the terrain shader declares; see foliage_26_3.vsh for the values.
layout(location = 4) in float SwayCell;
layout(location = 5) in float SwayWeights;

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

#include <mc2_interactivefoliage:sway.glsl>
