# Pending work

What players have asked for and what is still owed, so nothing gets lost between versions. Source: the 82 comments on
the CurseForge page as of 2026-10-12, the GitHub issues, and what was deferred while working. Tick an item off in the
commit that finishes it, and add the date when the list is read against the comments again.

## Before 2.2.0 ships

- [ ] `deps.fabric-loader` back to `0.19.2` in `stonecutter.properties.toml`; decide whether the Fabric API bumps for
      26.2 and 26.3 ship.
- [ ] Smoke tests without VulkanMod or Sable, since shared shaders and the legacy draw changed after 2.1.1:
      26.3, 1.21.11, 1.21.1 Fabric, 1.20.1.
- [ ] Sable on Fabric in a launcher (only NeoForge was tested).
- [x] Read MrKiddo's two Particle Rain logs (see Bugs): our own bug, already fixed in 2.1.1.

## Small drops: plants of other mods

Block registration only (`ModCompatRegistry`), so these can go out as small releases on their own. Already known:
Biomes O' Plenty, Serene Shrubbery, No Man's Land, Farmer's Delight, Snow! Real Magic.

- [ ] Regions Unexplored -- Dreigin. Promised for "the next update".
  https://www.curseforge.com/minecraft/mc-mods/regions-unexplored
- [ ] Oh The Biomes We've Gone -- jaydonthewarrior. Promised.
  https://www.curseforge.com/minecraft/mc-mods/oh-the-biomes-weve-gone
- [ ] Sizeable Foliage -- jaydonthewarrior. Promised.
  https://www.curseforge.com/minecraft/mc-mods/sizeable-foliage
- [ ] Project: Vibrant Journeys -- jaydonthewarrior. Promised.
  https://www.curseforge.com/minecraft/mc-mods/project-vibrant-journeys
- [ ] Immersive Weathering -- jaydonthewarrior. Not answered yet.
  https://www.curseforge.com/minecraft/mc-mods/immersive-weathering-forge
- [ ] Cobblemon -- asked for by the user, 2026-10-12.
- [ ] Modded foliage in general -- StayStrange, Mysticpasta1. Ask which mods when they come back.

## Compatibility layers

- [ ] Colorful Lighting, and its Sodium edition -- jaydonthewarrior: the foliage does not take the coloured light.
      Promised. https://www.curseforge.com/minecraft/mc-mods/colorful-lighting
      https://www.curseforge.com/minecraft/mc-mods/colorful-lighting-sodium
- [ ] Dynamic lights on 1.20.1 Forge -- tnathanro: broken lighting on the foliage with "Dynamic Lights"
      (atomicstrykergrumpy) and "Sodium/Embeddium Dynamic Lights" (Txni). Promised a fix or a layer.
      LambDynamicLights on 26.3 needed no layer; these two are other mods and were never looked at.
- [ ] Chunks Fade In -- RAPTORE, and a GitHub issue: the foliage should fade in with the chunk. Promised for the next
      update.
- [ ] Chunk Animator -- asked for by the user, 2026-10-12: as with Chunks Fade In, which has a fork of the same
      chunk animation, the foliage has to follow the chunk's animation, or the plant floats in place while its chunk
      flies in. One layer may serve both.
- [ ] Terrain Slabs on 1.21.1 (3.1.2) and 1.20.1 (4.0.3-beta) -- another design than 1.21.11 and later: no
      OnTopHelper, the plant is lowered through BlockState.getOffset, which the mesher already reads. Read from the
      jars, never seen in game: a "not a version the GPU renderer knows" warning is logged for nothing, and the sway
      is weighed from half a block too high, so a plant on a slab likely sways less. 1.21.11 to 26.3 are fine.
- [ ] Ecliptic Seasons -- Baronman: snow-covered plants, as Snow! Real Magic has. On the list.
- [ ] Nvidium -- on the author's list; jars for 1.21.1 to 26.3 are in `versions/`. Deferred by the user until after
      2.2.0. The 26.3 issue reporter (cbbg crash) runs it and sees stutter.
- [ ] Luxium -- on the author's list.
- [ ] Windlink -- one request; answered "maybe in the future". https://modrinth.com/mod/windlink
- [ ] Ashen resource pack (plant variant textures) -- Werewolf25, twice: tall grass and the variants of flowers do not
      move with it. Not answered yet.

## Bugs still open

- [ ] Particle Rain, 26.2 Fabric -- MrKiddo, on 2.1.0. Logs https://mclo.gs/qmB4uFp and https://mclo.gs/shyBZtP.
      Not Particle Rain: a NullPointerException of ours in `followRain` ("section" is null), with the same in
      `isVisible` from the shadow callback just before. Rain's shelter check walked the layout last uploaded, which
      could name a slot emptied since; it only runs while it rains with the weather wind on, hence "wind off fixes
      it". Fixed by 74e8cce, which is in 2.1.1. Owed: tell him to update, and tick this when he confirms.
- [ ] Random crashes since 2.0 -- emurp, lev08962, imessentiallyalex, FineARC, Quantum2001 (24-27 Sep, before
      2.1.1). No crash report from any. RAPTORE says turning the wind off stops them, which is the signature of the
      bug above: most likely the same one, fixed in 2.1.1. Not proven without a report; ask them to retry on 2.1.1
      or newer. FineARC gave a mod list (NeoForge, with Create Aeronautics, which runs on Sable).
- [ ] Double plant on 1.20.1 Forge -- Beybok: a moving plant drawn over a still one, in a large modpack. Asked to
      bisect the modpack; no news.
- [ ] Dark waving foliage on 1.20.1 Forge -- Mysticpasta1: no shader or resource pack, a 1900-mod pack. No diagnosis.
- [ ] Mods stop working past 81 mods -- mitzukishogunumu: never gave version or loader. Likely not ours.
- [ ] Stutter on 26.3 with Nvidium, C2ME and AsyncParticles -- GitHub issue, same report as the cbbg crash. Ask for a
      spark profile on 2.2.0.

## Features asked for

- [ ] Blacklist by block state -- Baronman: for example snowy grass still while plain grass waves.
- [ ] Interactive leaves -- Bora, for mods like Passable Foliage. Answered "probably not".

## Deferred by us

- [ ] Multi-threaded meshing.
- [ ] GPU area that follows the zoom.
- [ ] Sable: release sub-levels that are far away; sub-levels in Polytone's shadow map; a swing for a piece spinning
      in place.
- [ ] Snow! Real Magic on 1.21.11, 26.2 and 26.3, once it ships for them.

## Closed, not to be reopened

- VulkanMod on 1.20.1: dropped. VulkanMod is supported from 1.21.1 to 26.1.2.
- VanillaBackport with VulkanMod: not worked around.
- Sable with VulkanMod: Veil needs OpenGL, so Sable does not start there. Nothing on our side.

## Done, for the record

- Sodium and Embeddium -- HappyMeek, Golden0Eye, VoidMonitor, awesomedeity, Placeholder.
- Forge 1.20.1 -- Jerya_, WhatIsThisNikol, sleepy_yalow35, ProducerMZ.
- 26.3 port.
- Blacklist and whitelist, vines and glow lichen included -- Baronman, Mhikusess.
- Farmer's Delight plants -- Unsandboxed_Dev (never answered on the page).
- cbbg crash on 26.3 when a shader pack is turned off -- GitHub issue, fixed in e3491eb for 2.2.0.
