#version 150

// foliage_legacy.vsh for versions before 1.21.1, identical but for the fog: their shader library measures a
// vertex's fog distance through the view matrix, and the chunk mesh's foliage is fogged that way beside it.

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;
// The mod's two per-vertex values, each packed whole into one float. See foliage.vsh.
in float SwayCell;
in float SwayWeights;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// The region's corner relative to the camera, which SwayCell is measured from.
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float GameTime;

// Settings the player changes in game. SwayIntensity multiplies SWAY_STRENGTH, from 0.5 to 2.0.
uniform float SwayIntensity;
// 1 while plants sway in calm weather, 0 while they stand still until the weather wind moves them.
uniform float CalmSway;
// The edge of the area this renderer draws, relative to the camera: x and y its lowest x and z, z and w its highest.
uniform vec4 SwayEdge;
// What newer versions hand every shader: the camera's block, and that block's corner minus the camera.
uniform ivec3 CameraBlockPos;
uniform vec3 CameraOffset;
// The level's rain in x and its thunder in y, how far from the camera it blows in z and over how far it eases off in w.
// See sway.glsl.
uniform vec4 Weather;

const float SWAY_SPEED = 2513.2741;
const int PHASE_WRAP = 4095;
const float SWAY_SCALE = 6.2831853 * 230.0 / 4096.0;
const float SWAY_STRENGTH = 0.33;

#define MAX_CELLS 128
layout(std140) uniform FoliageInteraction {
    int CellCount;
    vec4 CellBoundsMin;           // xyz: lowest cell corner, relative to the camera
    vec4 CellBoundsMax;           // xyz: highest cell corner, relative to the camera
    vec4 CellPosition[MAX_CELLS]; // xyz: anchor block corner, relative to the camera
    vec4 CellForce[MAX_CELLS];    // xy: push along x and z; z: the most this plant may be pushed
};

const float INTERACT_STRENGTH = 1.0;
const float PUSHED_SWAY = 0.25;
const float PUSH_FOR_CALM = 0.2;
const float EDGE_EASE_BLOCKS = 6.0;

// Rain's wind, as in sway.glsl, where each value is explained.
const vec2 WIND_DIRECTION = vec2(-1.0, 0.0);
const float WIND_LEAN = 2.5;
const float WIND_GUST = 0.4;
const float STORM_STRENGTH = 2.0;
const float WIND_FLUTTER = 0.2;
const float WIND_TUG = 0.2;
const float GUST_SPEED = 6.2831853 * 300.0;
const float FLUTTER_SPEED = 6.2831853 * 1600.0;
const float TUG_SPEED = 6.2831853 * 2100.0;

// How much of the wind a plant keeps near the edge. See foliage.vsh.
float edgeEase(vec3 cell) {
    vec2 centre = cell.xz + 0.5;
    float inside = min(min(centre.x - SwayEdge.x, SwayEdge.z - centre.x),
                       min(centre.y - SwayEdge.y, SwayEdge.w - centre.y));
    return smoothstep(0.0, EDGE_EASE_BLOCKS, inside);
}

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

vec3 unpackCell(float bits) {
    float x = floor(bits / 65536.0);
    float rest = bits - x * 65536.0;
    float y = floor(rest / 256.0);
    return vec3(x, y, rest - y * 256.0) - 64.0;
}

// The wind weight, ten bits, the push, eight, whether the plant stands still in calm weather, one, whether it waves as a
// steady (a push leaves its sway as it is), one, then how much of rain's wind reaches the plant, four. w: how much of the calm sway the plant has. See
// sway.glsl.
vec4 unpackWeights(float bits, out float steady) {
    float exposure = bits - floor(bits / 16.0) * 16.0;
    float rest = (bits - exposure) / 16.0;
    steady = rest - floor(rest / 2.0) * 2.0;
    rest = (rest - steady) / 2.0;
    float still = rest - floor(rest / 2.0) * 2.0;
    float weights = (rest - still) / 2.0;
    float wave = floor(weights / 256.0);
    return vec4(wave / 1023.0, (weights - wave * 256.0) / 255.0, exposure / 15.0, 1.0 - still);
}

// How far rain's wind moves a vertex that may sway this much. See sway.glsl.
vec2 wind(vec3 world, float phase, float reach, float shake) {
    float strength = reach * mix(1.0, STORM_STRENGTH, Weather.y);
    float along = -dot(world.xz, WIND_DIRECTION);
    float gust = 0.5 + 0.5 * sin(along * SWAY_SCALE + GameTime * GUST_SPEED);
    vec2 across = vec2(-WIND_DIRECTION.y, WIND_DIRECTION.x);
    float flutter = sin(phase + GameTime * FLUTTER_SPEED) * WIND_FLUTTER * shake;
    float tug = sin(phase * 1.3 + GameTime * TUG_SPEED) * WIND_TUG * shake;
    float lean = WIND_LEAN * (1.0 - WIND_GUST + WIND_GUST * gust);
    return (WIND_DIRECTION * (lean + tug) + across * flutter) * strength;
}

// The calm sway and rain's wind of a vertex that may sway this far; the wind's part of it in windOffset. See sway.glsl.
vec2 motion(vec3 world, float phase, float reach, float shake, float calmShare, float exposure, vec3 easeAt,
            out vec2 windOffset) {
    vec2 sway = vec2(sin(phase), cos(phase * 1.3) * 0.7) * reach * shake * CalmSway * calmShare;
    float rain = Weather.x * exposure * (1.0 - smoothstep(Weather.z - Weather.w, Weather.z, length(easeAt.xz + 0.5)));
    windOffset = rain > 0.0 ? wind(world, phase, reach, shake) * rain : vec2(0.0);
    return mix(sway, vec2(0.0), rain) + windOffset;
}

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ChunkOffset;

    vec3 world = pos + vec3(CameraBlockPos & PHASE_WRAP) - CameraOffset;
    float phase = (world.x + world.z) * SWAY_SCALE + GameTime * SWAY_SPEED;
    vec3 cell = unpackCell(SwayCell) + ChunkOffset;
    vec2 force = swayCellPush(cell);
    float steady;
    vec4 weights = unpackWeights(SwayWeights, steady);
    // Still and steady at once: a block hanging on leaves, swaying as a plant and moving as the leaves. See sway.glsl.
    bool hanging = steady > 0.5 && weights.w < 0.5;
    float calmShare = hanging ? 1.0 : weights.w;
    float exposure = hanging ? 1.0 : weights.z;
    // The leaves ease where each vertex is, so blocks that touch never part. See sway.glsl.
    vec3 easeAt = steady > 0.5 && !hanging ? pos - 0.5 : cell;
    float calm = smoothstep(0.0, PUSH_FOR_CALM, length(force));
    float reach = weights.x * SWAY_STRENGTH * SwayIntensity * edgeEase(easeAt);
    // A steady block never calms, so what hangs on the leaves stays on them while pushed. See sway.glsl.
    float shake = steady > 0.5 ? 1.0 : mix(1.0, PUSHED_SWAY, calm);
    vec2 push = force * weights.y * INTERACT_STRENGTH;
    vec2 windOffset;
    vec2 offset = motion(world, phase, reach, shake, calmShare, exposure, easeAt, windOffset);
    if (hanging) {
        vec3 atVertex = pos - 0.5;
        float leafReach = weights.z * SWAY_STRENGTH * SwayIntensity * edgeEase(atVertex);
        vec2 leafWind;
        offset += motion(world, phase, leafReach, 1.0, 1.0, 1.0, atVertex, leafWind);
        windOffset += leafWind;
    }
    pos.xz += offset + push;
    float pushed = length(push + windOffset);
    pos.y -= min(pushed * pushed, pushed) * 0.5;

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
