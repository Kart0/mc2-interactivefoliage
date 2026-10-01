package net.karto.mc2.mc2_interactivefoliage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

//? >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The blocks the GPU renderer draws that never wave in calm weather. The weather's wind still moves them, and they
 * still bend when something pushes them, as Sway has them do.
 * <p>
 * Chosen by the author, not the player: blocks that lie flat on a surface or cling to one, and blocks others stand on,
 * where a calm wave reads as the surface itself wobbling. Laid out like ModCompatRegistry, one section per mod; a block
 * of a mod that is not installed, or of a version that does not have it, is skipped.
 */
public final class WavingBlacklist {

	private static final Set<Block> BLOCKS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static boolean resolved;

	private WavingBlacklist() {
	}

	/** Whether this block never waves in calm weather. Asked for every block the GPU renderer meshes. */
	public static boolean contains(BlockState state) {
		if (!resolved) {
			// Only once the world is drawn, when every block of every mod is registered.
			resolved = true;
			vanilla();
			biomesOPlenty();
			farmersDelight();
			sereneShrubbery();
			noMansLand();
		}
		return BLOCKS.contains(state.getBlock());
	}

	// ------------------------------------------------------------------
	// Vanilla
	// ------------------------------------------------------------------

	private static void vanilla() {
		// Clinging to walls and ceilings. Vines are not here: they follow the leaves they hang on, see
		// WavingWhitelist.followLeaves().
		add(
				"minecraft:glow_lichen"
		);
		// Lying flat on the ground or on water.
		add(
				"minecraft:moss_carpet",
				"minecraft:lily_pad",
				"minecraft:sea_pickle"
		);
		// Solid enough to be stood on, or built from blocks that are.
		add(
				"minecraft:big_dripleaf",
				"minecraft:big_dripleaf_stem",
				"minecraft:chorus_plant",
				"minecraft:chorus_flower"
		);
	}

	// ------------------------------------------------------------------
	// Biomes O' Plenty
	// ------------------------------------------------------------------

	private static void biomesOPlenty() {
		// Willow vines follow the leaves they hang on, see WavingWhitelist.followLeaves().
		add(
		);
	}

	// ------------------------------------------------------------------
	// Farmer's Delight
	// ------------------------------------------------------------------

	private static void farmersDelight() {
		add(
		);
	}

	// ------------------------------------------------------------------
	// Serene Shrubbery
	// ------------------------------------------------------------------

	private static void sereneShrubbery() {
		add(
		);
	}

	// ------------------------------------------------------------------
	// No Man's Land
	// ------------------------------------------------------------------

	private static void noMansLand() {
		add(
		);
	}

	// ------------------------------------------------------------------
	// Helper. Every lookup is optional.
	// ------------------------------------------------------------------

	private static void add(String... ids) {
		for (String id : ids) {
			//? >=1.21.11 {
			Identifier key = Identifier.tryParse(id);
			//?} else {
			/*ResourceLocation key = ResourceLocation.tryParse(id);
			*///?}
			if (key != null) {
				BuiltInRegistries.BLOCK.getOptional(key).ifPresent(BLOCKS::add);
			}
		}
	}
}
