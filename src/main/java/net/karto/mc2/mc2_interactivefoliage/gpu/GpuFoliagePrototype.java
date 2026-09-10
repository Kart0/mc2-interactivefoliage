package net.karto.mc2.mc2_interactivefoliage.gpu;

import com.github.razorplay01.sway.api.SwayAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Prototype: takes foliage out of the chunk mesh so the mod can draw it itself.
 * <p>
 * Both the vanilla section compiler and Sodium's meshing task gate on
 * {@code BlockState.getRenderShape() == RenderShape.MODEL}, so reporting {@code INVISIBLE} removes a
 * block from either mesher through the same, supported signal. Nothing then draws it, which is the
 * point: the mod takes ownership of those blocks and animates them on the GPU instead of re-meshing
 * chunks whenever the deformation changes.
 */
public final class GpuFoliagePrototype {

	/**
	 * Only the versions that have a renderer may hide blocks from the mesher. Everywhere else the
	 * foliage would be excluded with nothing left to draw it, and simply vanish.
	 */
	public static boolean enabled = /*? fabric && >=26.2 {*/ true /*?} else {*/ /*false *//*?} */;

	private static Set<Block> targets;

	private GpuFoliagePrototype() {
	}

	/**
	 * Whether the mod, rather than the chunk mesher, is responsible for drawing this block.
	 * <p>
	 * Called for every block of every chunk build, so it stays to a flag test and a set lookup.
	 */
	public static boolean rendersItself(BlockState state) {
		return enabled && targets().contains(state.getBlock());
	}

	/**
	 * Every block Sway animates: the vanilla foliage it registers itself, plus whatever
	 * {@link net.karto.mc2.mc2_interactivefoliage.ModCompatRegistry} found from other mods.
	 * <p>
	 * Resolved once and cached, because {@code isInteractive} walks Sway's registries and this sits
	 * on the meshing path. Registration is finished by the time the first chunk is built.
	 */
	private static Set<Block> targets() {
		if (targets == null) {
			Set<Block> found = Collections.newSetFromMap(new IdentityHashMap<>());
			for (Block block : BuiltInRegistries.BLOCK) {
				if (SwayAPI.isInteractive(block)) {
					found.add(block);
				}
			}
			targets = found;
		}
		return targets;
	}
}
