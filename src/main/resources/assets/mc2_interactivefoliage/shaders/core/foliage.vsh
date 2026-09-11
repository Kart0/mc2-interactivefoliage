#version 330

// Vanilla's core/block vertex shader with a sway added before projection.
//
// The displacement is driven entirely by WaveWeight, which the mod computes per vertex from Sway's
// anchor for that plant: 0 where it is held and 1 at its free end. That is what makes a ground
// plant bend from its base, a hanging vine from the ceiling, and a tall stack move as one piece.

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in float WaveWeight;
// The plant's anchor block, relative to the region: every vertex of a plant shares it.
in vec3 SwayCell;

// GameTime is the fraction of a Minecraft day, so it advances from 0 to 1 over 24000 ticks --
// twenty real minutes. Scaling by two pi times a whole number of cycles gives a visible rhythm
// (one sway every three seconds) and keeps the motion seamless when it wraps back to zero.
const float SWAY_SPEED = 2513.2741;

// The phase follows world position, with the camera's block wrapped to 4096 so the numbers stay
// precise far from the origin. The scale fits a whole number of cycles into that span -- 230, and
// 299 for the second axis, which runs 1.3 times faster -- so the wrap never shows as a seam.
const int PHASE_WRAP = 4095;
const float SWAY_SCALE = 6.2831853 * 230.0 / 4096.0;
const float SWAY_STRENGTH = 0.33;

// Settings the player changes in game, written by the mod into a buffer so they apply on the next frame.
// SwayIntensity multiplies SWAY_STRENGTH, from 0.5 to 2.0.
layout(std140) uniform FoliageSway {
    float SwayIntensity;
};

// Sway's pushes on the plants near the player this frame, one per plant by its anchor block, already
// followed through a spring by the mod so plants lean in smoothly and rock back when let go.
#define MAX_CELLS 128
layout(std140) uniform FoliageInteraction {
    int CellCount;
    vec4 CellBoundsMin;           // xyz: lowest cell corner, relative to the camera
    vec4 CellBoundsMax;           // xyz: highest cell corner, relative to the camera
    vec4 CellPosition[MAX_CELLS]; // xyz: anchor block corner, relative to the camera
    vec4 CellForce[MAX_CELLS];    // xy: push along x and z
};

// How far the tip of a plant can be pushed, in blocks.
const float INTERACT_STRENGTH = 0.55;

// Sway's push on the plant whose anchor block is at this cell, or none. Every vertex of a plant reads the
// same push, so the whole plant leans as one piece.
vec2 swayCellPush(vec3 cell) {
    if (CellCount == 0
            || any(lessThan(cell, CellBoundsMin.xyz - 0.5))
            || any(greaterThan(cell, CellBoundsMax.xyz + 0.5))) {
        return vec2(0.0);
    }
    for (int i = 0; i < CellCount; i++) {
        if (all(lessThan(abs(CellPosition[i].xyz - cell), vec3(0.5)))) {
            vec2 force = CellForce[i].xy;
            float amount = length(force);
            return amount > 1.0 ? force / amount : force;
        }
    }
    return vec2(0.0);
}

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ModelOffset;

    // pos is relative to the camera. Adding the camera back gives a world position, so each plant
    // keeps its own rhythm while the player moves instead of the pattern sliding along with them.
    // CameraOffset is the camera's block corner minus the camera itself.
    vec3 world = pos + vec3(CameraBlockPos & PHASE_WRAP) - CameraOffset;

    // Phase varies with world position so neighbouring plants never move in lockstep.
    float phase = (world.x + world.z) * SWAY_SCALE + GameTime * SWAY_SPEED;
    float amount = WaveWeight * SWAY_STRENGTH * SwayIntensity;
    // ModelOffset is the region's corner relative to the camera, which SwayCell is measured from.
    vec2 push = swayCellPush(SwayCell + ModelOffset) * WaveWeight * INTERACT_STRENGTH;
    pos.x += sin(phase) * amount;
    pos.z += cos(phase * 1.3) * amount * 0.7;
    pos.xz += push;
    // A plant bends rather than slides: the further its tip is pushed, the lower it sits.
    pos.y -= dot(push, push) * 0.5;

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
