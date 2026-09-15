#version 330

// Not a whole shader: what the mod adds to the terrain vertex shader in use -- vanilla's, or a resource pack's own -- so
// foliage is lit and coloured exactly as the rest of the terrain. TerrainFoliageShader splices it in where that shader
// reads Position, and runs the shader's own main with the swayed position in its place. See foliage.vsh for the values.

in float SwayCell;
in float SwayWeights;

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
