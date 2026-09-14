// How the GPU renderer moves a plant: the wind, the pushes from entities and the ease off at the edge of its area.
//
// Shared word for word by the mod's own vertex shader and, while a shader pack is loaded, by the copy of the pack's
// vertex shader the mod draws foliage with, so the two can never drift apart. Everything is prefixed so it cannot
// clash with a shader pack's own names; it reads the two uniform blocks below, which each shader declares itself.
//
// mc2_FoliageSway:       float mc2_SwayIntensity; vec4 mc2_SwayEdge; vec4 mc2_Weather;
// mc2_FoliageInteraction: int mc2_CellCount; vec4 mc2_CellBoundsMin; vec4 mc2_CellBoundsMax;
//                         vec4 mc2_CellPosition[MC2_MAX_CELLS]; vec4 mc2_CellForce[MC2_MAX_CELLS];
//
// SwayIntensity multiplies MC2_SWAY_STRENGTH, from 0.5 to 2.0. SwayEdge is the edge of the area the renderer draws,
// relative to the camera: x and y its lowest x and z, z and w its highest. Weather is the level's rain in x and its
// thunder in y, each from 0 to 1 and eased in and out by the game; z and w are
// unused. The cells are Sway's pushes on the plants near the player this frame, one per plant by its anchor block, already followed through a spring by the mod so
// plants lean in smoothly and rock back when let go: xyz the anchor block's corner relative to the camera, and for
// the force xy the push along x and z and z the most the plant may be pushed.

// GameTime is the fraction of a Minecraft day, so it advances from 0 to 1 over 24000 ticks -- twenty real minutes.
// Scaling by two pi times a whole number of cycles gives a visible rhythm (one sway every three seconds) and keeps
// the motion seamless when it wraps back to zero.
const float MC2_SWAY_SPEED = 2513.2741;

// The phase follows world position, with the camera's block wrapped to 4096 so the numbers stay precise far from
// the origin. The scale fits a whole number of cycles into that span -- 230, and 299 for the second axis, which
// runs 1.3 times faster -- so the wrap never shows as a seam.
const int MC2_PHASE_WRAP = 4095;
const float MC2_SWAY_SCALE = 6.2831853 * 230.0 / 4096.0;
const float MC2_SWAY_STRENGTH = 0.33;

// ---- Rain's wind -------------------------------------------------------------------------------------------------
// While it rains the calm sway gives way to a wind blowing west, the way the clouds drift. Every amount below is in
// units of the calm sway's reach -- the player's intensity times MC2_SWAY_STRENGTH, on a plant's freest vertex -- so
// the slider scales the wind as it does the sway. Raising the lean or the storm makes plants reach further out of their
// sections; GpuFoliageRenderer.SWAY_MARGIN has to grow with them, or plants at the edge of the screen get culled.
const vec2 MC2_WIND_DIRECTION = vec2(-1.0, 0.0);
// How far a plant leans downwind, and how much of that lean comes and goes with the gusts: at 0.4 a plant leans 60%
// of the way between gusts and all the way under one.
const float MC2_WIND_LEAN = 2.5;
const float MC2_WIND_GUST = 0.4;
// How much further a storm blows everything: the lean, the gusts, the flutter and the tug.
const float MC2_STORM_STRENGTH = 2.0;
// A plant flutters across the wind and tugs along it, each to its own rhythm, as if the wind were about to tear it
// out. The tug should stay below the lean between gusts, MC2_WIND_LEAN * (1 - MC2_WIND_GUST), or plants swing back
// upwind.
const float MC2_WIND_FLUTTER = 0.2;
const float MC2_WIND_TUG = 0.2;
// Speeds are two pi times how many times a day each repeats, a whole number so nothing jumps when the day wraps:
// 400 is one cycle every three seconds. Gusts roll over the ground downwind once every four seconds, a little under
// eighteen blocks apart.
const float MC2_GUST_SPEED = 6.2831853 * 300.0;
const float MC2_FLUTTER_SPEED = 6.2831853 * 1600.0;
const float MC2_TUG_SPEED = 6.2831853 * 2100.0;

// The wind eases off over this many blocks before the edge, so it does not stop dead where the chunk mesh takes
// over.
const float MC2_EDGE_EASE_BLOCKS = 6.0;

// A last say over how hard entities push, on top of what Sway already decides. At 1.0 a plant bends exactly as far
// as it would in the chunk mesh; the mod already sends a stronger push while it draws the foliage.
const float MC2_INTERACT_STRENGTH = 1.0;
// How much of its sway a plant keeps while an entity pushes it.
const float MC2_PUSHED_SWAY = 0.25;
// How hard a push has to be before a plant keeps only MC2_PUSHED_SWAY; lighter pushes calm it partly, so the sway
// eases down as a plant is pushed and back up as the push fades.
const float MC2_PUSH_FOR_CALM = 0.2;

// How much of the wind a plant keeps, by how far its anchor block sits inside the edge. It rises along a curve that
// starts and ends gently, so the plants by the edge barely stir and no one plant moves much more than the next; and
// continuously, so the edge following the player changes each plant's sway smoothly rather than in steps. Measured
// from the anchor, so the whole plant eases off together; outside the edge, while the chunk mesh takes a plant back,
// it keeps none.
float mc2_edgeEase(vec3 cell) {
    vec2 centre = cell.xz + 0.5;
    float inside = min(min(centre.x - mc2_SwayEdge.x, mc2_SwayEdge.z - centre.x),
                       min(centre.y - mc2_SwayEdge.y, mc2_SwayEdge.w - centre.y));
    return smoothstep(0.0, MC2_EDGE_EASE_BLOCKS, inside);
}

// Sway's push on the plant whose anchor block is at this cell, or none. Every vertex of a plant reads the same push,
// so the whole plant leans as one piece.
vec2 mc2_cellPush(vec3 cell) {
    if (mc2_CellCount == 0
            || any(lessThan(cell, mc2_CellBoundsMin.xyz - 0.5))
            || any(greaterThan(cell, mc2_CellBoundsMax.xyz + 0.5))) {
        return vec2(0.0);
    }
    for (int i = 0; i < mc2_CellCount; i++) {
        if (all(lessThan(abs(mc2_CellPosition[i].xyz - cell), vec3(0.5)))) {
            vec2 force = mc2_CellForce[i].xy;
            float limit = mc2_CellForce[i].z;
            float amount = length(force);
            return amount > limit ? force * (limit / amount) : force;
        }
    }
    return vec2(0.0);
}

// The mod's two per-vertex values each travel packed whole into one float, because a vertex attribute is read for
// every vertex of every frame and eight bytes beat twenty. A float holds whole numbers up to 2^24 exactly, which is
// three bytes' worth, and both values fit inside that.
//
// SwayCell: the plant's anchor block relative to the region, a byte per axis, biased so an anchor just outside its
// region still fits. Every vertex of a plant carries the same one.
vec3 mc2_unpackCell(float bits) {
    float x = floor(bits / 65536.0);
    float rest = bits - x * 65536.0;
    float y = floor(rest / 256.0);
    return vec3(x, y, rest - y * 256.0) - 64.0;
}

// SwayWeights: how freely this vertex sways, how far a push moves it, ten bits each, and how much of rain's wind
// reaches the plant, four bits. The first is the wind's own curve; the second is Sway's, so a pushed plant bends the
// same whoever draws it; the third is 1 in the open and falls under roofs, in caves and behind walls upwind, as the
// mod works out when it meshes the plant. x: the wind weight, y: the push, z: the wind's reach.
vec3 mc2_unpackWeights(float bits) {
    float exposure = bits - floor(bits / 16.0) * 16.0;
    float weights = (bits - exposure) / 16.0;
    float wave = floor(weights / 1024.0);
    return vec3(vec2(wave, weights - wave * 1024.0) / 1023.0, exposure / 15.0);
}

// How far rain's wind moves a vertex that may sway this much, along x and z: the plant leans downwind, gusts rolling
// over the ground push it further, and it flutters across the wind and tugs along it. The tug never outweighs the lean,
// so a plant shakes in place rather than swinging back upwind.
vec2 mc2_wind(vec3 world, float phase, float gameTime, float amount) {
    float strength = amount * mix(1.0, MC2_STORM_STRENGTH, mc2_Weather.y);
    // Measured against the wind's direction, so the gusts travel the way it blows.
    float along = -dot(world.xz, MC2_WIND_DIRECTION);
    float gust = 0.5 + 0.5 * sin(along * MC2_SWAY_SCALE + gameTime * MC2_GUST_SPEED);
    vec2 across = vec2(-MC2_WIND_DIRECTION.y, MC2_WIND_DIRECTION.x);
    float flutter = sin(phase + gameTime * MC2_FLUTTER_SPEED) * MC2_WIND_FLUTTER;
    // The second axis's phase, so the tug keeps to whole cycles across the wrapped span too.
    float tug = sin(phase * 1.3 + gameTime * MC2_TUG_SPEED) * MC2_WIND_TUG;
    float lean = MC2_WIND_LEAN * (1.0 - MC2_WIND_GUST + MC2_WIND_GUST * gust);
    return (MC2_WIND_DIRECTION * (lean + tug) + across * flutter) * strength;
}

// Moves one vertex. pos is relative to the camera; modelOffset is its region's corner relative to the camera, which
// SwayCell is measured from; the camera's block, that block's corner minus the camera and the game time are the
// values vanilla hands every shader. Returns the moved position, still relative to the camera.
vec3 mc2_sway(vec3 pos, vec3 modelOffset, ivec3 cameraBlockPos, vec3 cameraOffset, float gameTime,
              float swayCell, float swayWeights) {
    // Adding the camera back gives a world position, so each plant keeps its own rhythm while the player moves
    // instead of the pattern sliding along with them.
    vec3 world = pos + vec3(cameraBlockPos & MC2_PHASE_WRAP) - cameraOffset;

    // Phase varies with world position so neighbouring plants never move in lockstep.
    float phase = (world.x + world.z) * MC2_SWAY_SCALE + gameTime * MC2_SWAY_SPEED;
    vec3 cell = mc2_unpackCell(swayCell) + modelOffset;
    vec2 force = mc2_cellPush(cell);
    vec3 weights = mc2_unpackWeights(swayWeights);
    // Every vertex of a plant reads the same force, so the whole plant calms together.
    float calm = smoothstep(0.0, MC2_PUSH_FOR_CALM, length(force));
    float amount = weights.x * MC2_SWAY_STRENGTH * mc2_SwayIntensity * mc2_edgeEase(cell)
            * mix(1.0, MC2_PUSHED_SWAY, calm);
    vec2 push = force * weights.y * MC2_INTERACT_STRENGTH;
    vec2 sway = vec2(sin(phase), cos(phase * 1.3) * 0.7) * amount;
    // The game eases rain in and out over a few seconds, and the sway turns into the wind along with it -- as far as
    // the wind reaches the plant: under a roof or behind a wall it keeps its calm sway.
    float rain = mc2_Weather.x * weights.z;
    vec2 wind = rain > 0.0 ? mc2_wind(world, phase, gameTime, amount) * rain : vec2(0.0);
    pos.xz += mix(sway, vec2(0.0), rain) + wind + push;
    // A plant bends rather than slides: the further its tip is pushed, the lower it sits. Up to a block of push the
    // drop grows with its square, as a bending stalk would; past that it only grows in step, so a strong push leans a
    // plant over instead of sinking it into the ground. The wind leans plants over the same way.
    float pushed = length(push + wind);
    pos.y -= min(pushed * pushed, pushed) * 0.5;
    return pos;
}
