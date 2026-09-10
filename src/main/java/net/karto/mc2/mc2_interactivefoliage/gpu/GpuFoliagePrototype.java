package net.karto.mc2.mc2_interactivefoliage.gpu;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Prototype: takes foliage out of the chunk mesh so the mod can draw it itself.
 * <p>
 * Both the vanilla section compiler and Sodium's meshing task gate on
 * {@code BlockState.getRenderShape() == RenderShape.MODEL}, so reporting {@code INVISIBLE} removes a
 * block from either mesher through the same, supported signal. Nothing then draws it, which is the
 * point: the mod takes ownership of those blocks and can animate them on the GPU instead of
 * re-meshing chunks whenever the deformation changes.
 * <p>
 * Step one only proves the exclusion works, so the expected result is that the target block simply
 * disappears -- with and without Sodium installed.
 */
public final class GpuFoliagePrototype {

	/** Prototype-only switch. Nothing outside this branch should depend on it. */
	public static boolean enabled = true;

	private static Block target;

	private GpuFoliagePrototype() {
	}

	/**
	 * Whether the mod, rather than the chunk mesher, is responsible for drawing this block.
	 * <p>
	 * Called for every block of every chunk build, so it stays to a flag test and a reference
	 * comparison.
	 */
	public static boolean rendersItself(BlockState state) {
		return enabled && state.getBlock() == target();
	}

	private static Block target() {
		if (target == null) {
			target = /*? >1.20.1 {*/ Blocks.SHORT_GRASS /*?} else {*/ /*Blocks.GRASS *//*?} */;
		}
		return target;
	}
}
