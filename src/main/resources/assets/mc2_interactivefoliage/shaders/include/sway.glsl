// How the GPU renderer moves a plant: the wind, the pushes from entities and the ease off at the edge of its area.
//
// Shared word for word by the mod's own vertex shader and, while a shader pack is loaded, by the copy of the pack's
// vertex shader the mod draws foliage with, so the two can never drift apart. Everything is prefixed so it cannot
// clash with a shader pack's own names; it reads the two uniform blocks below, which each shader declares itself.
//
// mc2_FoliageSway:       float mc2_SwayIntensity; vec4 mc2_SwayEdge;
// mc2_FoliageInteraction: int mc2_CellCount; vec4 mc2_CellBoundsMin; vec4 mc2_CellBoundsMax;
//                         vec4 mc2_CellPosition[MC2_MAX_CELLS]; vec4 mc2_CellForce[MC2_MAX_CELLS];
//
// SwayIntensity multiplies MC2_SWAY_STRENGTH, from 0.5 to 2.0. SwayEdge is the edge of the area the renderer draws,
// relative to the camera: x and y its lowest x and z, z and w its highest. The cells are Sway's pushes on the plants
// near the player this frame, one per plant by its anchor block, already followed through a spring by the mod so
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

// SwayWeights: how freely this vertex sways, and how far a push moves it, twelve bits each. The first is the wind's
// own curve; the second is Sway's, so a pushed plant bends the same whoever draws it. x: the wind weight, y: the push.
vec2 mc2_unpackWeights(float bits) {
    float wave = floor(bits / 4096.0);
    return vec2(wave, bits - wave * 4096.0) / 4095.0;
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
    vec2 weights = mc2_unpackWeights(swayWeights);
    // Every vertex of a plant reads the same force, so the whole plant calms together.
    float calm = smoothstep(0.0, MC2_PUSH_FOR_CALM, length(force));
    float amount = weights.x * MC2_SWAY_STRENGTH * mc2_SwayIntensity * mc2_edgeEase(cell)
            * mix(1.0, MC2_PUSHED_SWAY, calm);
    vec2 push = force * weights.y * MC2_INTERACT_STRENGTH;
    pos.x += sin(phase) * amount;
    pos.z += cos(phase * 1.3) * amount * 0.7;
    pos.xz += push;
    // A plant bends rather than slides: the further its tip is pushed, the lower it sits. Up to a block of push the
    // drop grows with its square, as a bending stalk would; past that it only grows in step, so a strong push leans a
    // plant over instead of sinking it into the ground.
    float pushed = length(push);
    pos.y -= min(pushed * pushed, pushed) * 0.5;
    return pos;
}
