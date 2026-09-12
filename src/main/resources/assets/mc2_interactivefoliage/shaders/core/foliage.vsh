#version 330

// Vanilla's core/block vertex shader with a sway added before projection.
//
// The displacement is driven by the weights the mod computes per vertex from Sway's anchor for that
// plant: 0 where it is held and 1 at its free end. That is what makes a ground plant bend from its
// base, a hanging vine from the ceiling, and a tall stack move as one piece.

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
// The mod's two per-vertex values, each packed whole into one float, because a vertex attribute is read
// for every vertex of every frame and eight bytes beat twenty. A float holds whole numbers up to 2^24
// exactly, which is three bytes' worth, and both values fit inside that.
//
// SwayCell: the plant's anchor block relative to the region, a byte per axis, biased so an anchor just
// outside its region still fits. Every vertex of a plant carries the same one.
in float SwayCell;
// SwayWeights: how freely this vertex sways, and how far a push moves it, twelve bits each. The first is
// the wind's own curve; the second is Sway's, so a pushed plant bends the same whoever draws it.
in float SwayWeights;

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
    vec4 CellForce[MAX_CELLS];    // xy: push along x and z; z: the most this plant may be pushed
};

// A last say over how hard entities push, on top of what Sway already decides. At 1.0 a plant bends exactly
// as far as it would in the chunk mesh; the mod already sends a stronger push while it draws the foliage.
const float INTERACT_STRENGTH = 1.0;
// How much of its sway a plant keeps while an entity pushes it.
const float PUSHED_SWAY = 0.25;
// How hard a push has to be before a plant keeps only PUSHED_SWAY; lighter pushes calm it partly, so the
// sway eases down as a plant is pushed and back up as the push fades.
const float PUSH_FOR_CALM = 0.2;

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
            float limit = CellForce[i].z;
            float amount = length(force);
            return amount > limit ? force * (limit / amount) : force;
        }
    }
    return vec2(0.0);
}

uniform sampler2D Sampler2;

vec3 unpackCell(float bits) {
    float x = floor(bits / 65536.0);
    float rest = bits - x * 65536.0;
    float y = floor(rest / 256.0);
    return vec3(x, y, rest - y * 256.0) - 64.0;
}

// x: the wind weight, y: the push weight.
vec2 unpackWeights(float bits) {
    float wave = floor(bits / 4096.0);
    return vec2(wave, bits - wave * 4096.0) / 4095.0;
}

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
    vec3 pos = Position + ModelOffset;

    // pos is relative to the camera. Adding the camera back gives a world position, so each plant
    // keeps its own rhythm while the player moves instead of the pattern sliding along with them.
    // CameraOffset is the camera's block corner minus the camera itself.
    vec3 world = pos + vec3(CameraBlockPos & PHASE_WRAP) - CameraOffset;

    // Phase varies with world position so neighbouring plants never move in lockstep.
    float phase = (world.x + world.z) * SWAY_SCALE + GameTime * SWAY_SPEED;
    // ModelOffset is the region's corner relative to the camera, which SwayCell is measured from.
    vec2 force = swayCellPush(unpackCell(SwayCell) + ModelOffset);
    vec2 weights = unpackWeights(SwayWeights);
    // Every vertex of a plant reads the same force, so the whole plant calms together.
    float calm = smoothstep(0.0, PUSH_FOR_CALM, length(force));
    float amount = weights.x * SWAY_STRENGTH * SwayIntensity * mix(1.0, PUSHED_SWAY, calm);
    vec2 push = force * weights.y * INTERACT_STRENGTH;
    pos.x += sin(phase) * amount;
    pos.z += cos(phase * 1.3) * amount * 0.7;
    pos.xz += push;
    // A plant bends rather than slides: the further its tip is pushed, the lower it sits. Up to a block of
    // push the drop grows with its square, as a bending stalk would; past that it only grows in step, so a
    // strong push leans a plant over instead of sinking it into the ground.
    float pushed = length(push);
    pos.y -= min(pushed * pushed, pushed) * 0.5;

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * mc2_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
