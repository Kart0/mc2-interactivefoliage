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

// GameTime is the fraction of a Minecraft day, so it advances from 0 to 1 over 24000 ticks --
// twenty real minutes. Scaling by two pi times a whole number of cycles gives a visible rhythm
// (one sway every three seconds) and keeps the motion seamless when it wraps back to zero.
const float SWAY_SPEED = 2513.2741;

// The phase follows world position, with the camera's block wrapped to 4096 so the numbers stay
// precise far from the origin. The scale fits a whole number of cycles into that span -- 230, and
// 299 for the second axis, which runs 1.3 times faster -- so the wrap never shows as a seam.
const int PHASE_WRAP = 4095;
const float SWAY_SCALE = 6.2831853 * 230.0 / 4096.0;
const float SWAY_STRENGTH = 0.20;

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
    float amount = WaveWeight * SWAY_STRENGTH;
    pos.x += sin(phase) * amount;
    pos.z += cos(phase * 1.3) * amount * 0.7;

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
