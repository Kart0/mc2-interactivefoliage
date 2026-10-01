package net.karto.mc2.mc2_interactivefoliage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

//? >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blocks Sway does not bend that the GPU renderer still draws and waves: they sway in calm weather and move with
 * the weather's wind, and nothing pushes them.
 * <p>
 * Chosen by the author, not the player, and laid out by group rather than by mod: each group gathers vanilla's blocks
 * and every mod's together, so a group can be switched on or off as a whole. A block of a mod that is not installed, or
 * of a version that does not have it, is skipped. Tags get their own section, apart from the blocks named one by one.
 * <p>
 * Only 26.3 draws these for now; everywhere else the list stays empty until its version is hooked up.
 */
public final class WavingWhitelist {

	/**
	 * The groups of the whitelist, each with how hard its blocks wave, and how.
	 * <p>
	 * A plant waves more the higher above its base, so small blocks that sit low, like petals, barely move at a plant's
	 * strength and are given more. A field group waves as a whole instead: every vertex by where it is in the world, the
	 * same wherever it is shared, so blocks that touch -- a tree's leaves -- move together and never part.
	 */
	public enum Group {
		/** Ground plants and other small blocks: always on. Waves like a plant, twice as hard. */
		GENERAL(2.0F, false),
		/** Leaves: the player can switch them off (waving leaves). Waves as a field, a fifth as far as a plant's tip. */
		LEAVES(0.20F, true);

		/** How hard the group's blocks wave: a multiple of a plant's sway, or for a field, of its tip's. */
		private final float intensity;
		/** Whether the group's blocks wave as a field rather than from their base. */
		private final boolean field;

		Group(float intensity, boolean field) {
			this.intensity = intensity;
			this.field = field;
		}

		/** Whether the group's blocks wave now: leaves only while the player has them on. */
		boolean enabled() {
			return this != LEAVES || FoliageSettings.wavingLeaves();
		}
	}

	private static final Map<Block, Group> BLOCKS = new IdentityHashMap<>();
	/** The blocks that follow the leaves; see followLeaves(). */
	private static final Set<Block> FOLLOWERS = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Set once the lists are read; models are baked on several threads at once, so they are read under a lock. */
	private static volatile boolean resolved;

	private WavingWhitelist() {
	}

	/** Whether the GPU renderer waves this block now, although Sway does not bend it: listed, and its group on. */
	public static boolean contains(Block block) {
		if (!listed(block)) {
			return false;
		}
		return BLOCKS.get(block).enabled();
	}

	/**
	 * Whether this block is on the whitelist, its group on or not. Asked as models are baked, when every block of every
	 * mod is registered: a listed block's models are wrapped either way, so a group can be switched on or off in a world
	 * without baking them again.
	 */
	public static boolean listed(Block block) {
		//? >=26.3 {
		if (!resolved) {
			resolve();
		}
		return BLOCKS.containsKey(block);
		//?} else {
		/*return false;
		*///?}
	}

	//? >=26.3 {
	private static synchronized void resolve() {
		if (resolved) {
			return;
		}
		general();
		leaves();
		followLeaves();
		tags();
		Map<Group, Integer> counts = new EnumMap<>(Group.class);
		for (Group group : BLOCKS.values()) {
			counts.merge(group, 1, Integer::sum);
		}
		ModTemplate.LOGGER.info("Waving whitelist: {}, following the leaves: {}", counts, FOLLOWERS.size());
		resolved = true;
	}
	//?}

	/** How hard this block waves: its group's intensity, or 1 for a block not on the whitelist. */
	public static float intensityOf(Block block) {
		Group group = contains(block) ? BLOCKS.get(block) : null;
		return group == null ? 1.0F : group.intensity;
	}

	/** Whether this block waves as a field, as a tree's leaves do. */
	public static boolean wavesAsField(Block block) {
		Group group = contains(block) ? BLOCKS.get(block) : null;
		return group != null && group.field;
	}

	/** Whether this block follows the leaves: waves as hard as they do while they wave, and not at all else. */
	public static boolean followsLeaves(Block block) {
		//? >=26.3 {
		if (!resolved) {
			resolve();
		}
		return FOLLOWERS.contains(block);
		//?} else {
		/*return false;
		*///?}
	}


	/** Whether this block is one of the leaves group's, whether they wave now or not. */
	public static boolean isLeaves(Block block) {
		return listed(block) && BLOCKS.get(block) == Group.LEAVES;
	}

	/** How hard the leaves wave, which the blocks following them match. */
	public static float leavesIntensity() {
		return Group.LEAVES.intensity;
	}

	// ==================================================================
	// Group: GENERAL -- ground plants and other small blocks, always on
	// ==================================================================

	private static void general() {
		// Vanilla
		add(Group.GENERAL,
				"minecraft:wildflowers",
				"minecraft:pink_petals"
		);
		// Biomes O' Plenty
		add(Group.GENERAL,
				"biomesoplenty:white_petals",
				"biomesoplenty:purple_wildflowers"
		);
	}

	// ==================================================================
	// Group: LEAVES -- a tree's leaves
	// ==================================================================

	private static void leaves() {
		// Vanilla and mods come in through #minecraft:leaves, in the tags below. Name a mod's leaves here only if it
		// does not add them to that tag.
		add(Group.LEAVES
		);
	}

	// ==================================================================
	// Follow the leaves -- blocks that hang on leaves, like vines
	// ==================================================================
	// They only wave while waving leaves is on: as the plants they are, in calm weather and in the weather's wind, as hard
	// as the leaves, and a push leaves their sway as it is so they keep with the leaves. With it off, the renderer neither
	// sways them nor moves them in the wind. Sway pushes them either way.

	private static void followLeaves() {
		// Vanilla
		follow(
				"minecraft:vine"
		);
		// Biomes O' Plenty
		follow(
				"biomesoplenty:willow_vine"
		);
	}

	// ==================================================================
	// Tags -- every block a tag names, from Minecraft and from each mod
	// ==================================================================

	private static void tags() {
		tag(Group.LEAVES,
				"minecraft:leaves"
		);
	}

	// ------------------------------------------------------------------
	// Helpers. Every lookup is optional; a block named twice keeps its first group.
	// ------------------------------------------------------------------

	private static void add(Group group, String... ids) {
		for (String id : ids) {
			//? >=1.21.11 {
			Identifier key = Identifier.tryParse(id);
			//?} else {
			/*ResourceLocation key = ResourceLocation.tryParse(id);
			*///?}
			if (key != null) {
				BuiltInRegistries.BLOCK.getOptional(key).ifPresent(block -> BLOCKS.putIfAbsent(block, group));
			}
		}
	}

	private static void follow(String... ids) {
		for (String id : ids) {
			//? >=1.21.11 {
			Identifier key = Identifier.tryParse(id);
			//?} else {
			/*ResourceLocation key = ResourceLocation.tryParse(id);
			*///?}
			if (key != null) {
				BuiltInRegistries.BLOCK.getOptional(key).ifPresent(FOLLOWERS::add);
			}
		}
	}

	/**
	 * Adds every block the tags name. The world's tags only arrive with the world, long after the models are baked, so
	 * the tag files are read where they come from instead: Minecraft's own and every mod's, each adding to the others.
	 */
	private static void tag(Group group, String... tags) {
		for (String tag : tags) {
			List<String> ids = new ArrayList<>();
			readTag(tag, ids, new HashSet<>());
			add(group, ids.toArray(String[]::new));
		}
	}

	/** Gathers the blocks a tag names, following the tags it names in turn. */
	private static void readTag(String tag, List<String> ids, Set<String> seen) {
		if (!seen.add(tag)) {
			return;
		}
		int colon = tag.indexOf(':');
		String namespace = colon < 0 ? "minecraft" : tag.substring(0, colon);
		String path = colon < 0 ? tag : tag.substring(colon + 1);
		//? >=1.21.1 {
		String file = "data/" + namespace + "/tags/block/" + path + ".json";
		//?} else {
		/*String file = "data/" + namespace + "/tags/blocks/" + path + ".json";
		*///?}
		try {
			for (byte[] source : ModTemplate.xplat().readAll(file)) {
				try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(source), StandardCharsets.UTF_8)) {
					JsonElement values = JsonParser.parseReader(reader).getAsJsonObject().get("values");
					if (values == null) {
						continue;
					}
					for (JsonElement value : values.getAsJsonArray()) {
						String entry = value.isJsonObject()
								? ((JsonObject) value).get("id").getAsString() : value.getAsString();
						if (entry.startsWith("#")) {
							readTag(entry.substring(1), ids, seen);
						} else {
							ids.add(entry);
						}
					}
				}
			}
		} catch (Exception e) {
			ModTemplate.LOGGER.warn("Could not read the block tag {} for the waving whitelist", tag, e);
		}
	}
}
