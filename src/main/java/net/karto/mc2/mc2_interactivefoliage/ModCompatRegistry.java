package net.karto.mc2.mc2_interactivefoliage;

import com.github.razorplay01.sway.api.SwayAPI;
import com.github.razorplay01.sway.client.behavior.BuiltinBehaviors;
import com.github.razorplay01.sway.client.behavior.multiblock.HangingVineMultiblockBehavior;
import com.github.razorplay01.sway.client.behavior.multiblock.SugarCaneMultiblockBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources./*? >= 1.21.11 {*/ Identifier /*?} else {*/ /*ResourceLocation *//*?} */;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Optional compatibility registrations for third-party mods.
 * Every block is looked up by id and silently skipped when the mod is not present.
 */
public class ModCompatRegistry {
	private static final List<Block> REGISTERED = new ArrayList<>();

	private static boolean initialized;
	private static int found;
	private static int missing;

	private ModCompatRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		found = 0;
		missing = 0;
		REGISTERED.clear();

		registerBiomesOPlenty();
		registerFarmersDelight();
		registerSereneShrubbery();
		registerNoMansLand();
		registerExtraVanilla();

		ModTemplate.LOGGER.info("Foliage compat: {} blocks registered, {} not present", found, missing);
	}

	/**
	 * Blocks that were actually found and handed to Sway, in registration order.
	 * <p>
	 * Only meaningful once {@link #initialize()} has run.
	 */
	public static List<Block> registeredBlocks() {
		return Collections.unmodifiableList(REGISTERED);
	}

	// ------------------------------------------------------------------
	// Biomes O' Plenty
	// ------------------------------------------------------------------

	private static void registerBiomesOPlenty() {
		registerPlant(
				"biomesoplenty:dune_grass",
				"biomesoplenty:desert_grass",
				"biomesoplenty:dead_grass",
				"biomesoplenty:tundra_shrub",
				"biomesoplenty:bush",
				"biomesoplenty:enderphyte",
				"biomesoplenty:tiny_cactus",
				"biomesoplenty:yellow_maple_sapling",
				"biomesoplenty:orange_maple_sapling",
				"biomesoplenty:red_maple_sapling",
				"biomesoplenty:pine_sapling",
				"biomesoplenty:fir_sapling",
				"biomesoplenty:origin_oak_sapling",
				"biomesoplenty:origin_sapling",
				"biomesoplenty:snowblossom_sapling",
				"biomesoplenty:cypress_sapling",
				"biomesoplenty:flowering_oak_sapling",
				"biomesoplenty:empyreal_sapling",
				"biomesoplenty:hellbark_sapling",
				"biomesoplenty:magic_sapling",
				"biomesoplenty:violet",
				"biomesoplenty:lavender",
				"biomesoplenty:umbran_sapling",
				"biomesoplenty:palm_sapling",
				"biomesoplenty:jacaranda_sapling",
				"biomesoplenty:mahogany_sapling",
				"biomesoplenty:redwood_sapling",
				"biomesoplenty:flower_bud",
				"biomesoplenty:toadstool",
				"biomesoplenty:white_lavender",
				"biomesoplenty:orange_cosmos",
				"biomesoplenty:pink_daffodil",
				"biomesoplenty:pink_hibiscus",
				"biomesoplenty:origin_dandelion",
				"biomesoplenty:willow_sapling",
				"biomesoplenty:dead_sapling",
				"biomesoplenty:marigold",
				"biomesoplenty:origin_rose",
				"biomesoplenty:glowshroom",
				"biomesoplenty:brimstone_bud",
				"biomesoplenty:endbloom",
				"biomesoplenty:blackstone_bulb",
				"biomesoplenty:blackstone_spines",
				"biomesoplenty:glowflower",
				"biomesoplenty:wilted_lily",
				"biomesoplenty:burning_blossom",
				"biomesoplenty:sprout",
				"biomesoplenty:hair"
		);

		registerPlant(
				"biomesoplenty:sea_oats",
				"biomesoplenty:cattail",
				"biomesoplenty:reed",
				"biomesoplenty:watergrass",
				"biomesoplenty:goldenrod",
				"biomesoplenty:tall_white_lavender",
				"biomesoplenty:blue_hydrangea",
				"biomesoplenty:tall_lavender",
				"biomesoplenty:icy_iris",
				"biomesoplenty:brimstone_cluster"
		);

		// Same random-variant problem as the cobwebs below: eyebulb's closed state uses
		// eyebulb_bottom_closed / eyebulb_top_closed, whose names Sway's path parsing cannot map
		// back to the block, so on Fabric up to 1.21.1 only the open state would animate.
		//? !fabric || >1.21.1 {
		registerPlant(
				"biomesoplenty:eyebulb"
		);
		//?}

		registerStackable(
				"biomesoplenty:lumaloop",
				"biomesoplenty:lumaloop_plant",
				"biomesoplenty:high_grass",
				"biomesoplenty:high_grass_plant"
		);

		registerHanging(
				"biomesoplenty:spanish_moss",
				"biomesoplenty:spanish_moss_plant"
		);

		// Cobwebs and tendons use blockstates that pick one of several models at random per
		// position. Up to and including Fabric 1.21.1, Sway identifies a model's block by parsing
		// the model file path, so every variant whose file name carries an extra suffix
		// (_broken, _single, _alt) is never wrapped: only one model in three animates and a strand
		// ends up with motionless blocks between bending ones. Leaving them unregistered there
		// looks better than animating them in pieces. Every other platform and version resolves
		// the block reliably, so they stay registered.
		//? !fabric || >1.21.1 {
		registerHanging(
				"biomesoplenty:flesh_tendons",
				"biomesoplenty:flesh_tendons_strand",
				"biomesoplenty:hanging_cobweb",
				"biomesoplenty:hanging_cobweb_strand"
		);
		//?}
	}

	// ------------------------------------------------------------------
	// Farmer's Delight
	// ------------------------------------------------------------------

	private static void registerFarmersDelight() {
		registerPlant(
				"farmersdelight:sandy_shrub",
				"farmersdelight:wild_cabbages",
				"farmersdelight:wild_onions",
				"farmersdelight:wild_tomatoes",
				"farmersdelight:wild_carrots",
				"farmersdelight:wild_potatoes",
				"farmersdelight:wild_beetroots",
				"farmersdelight:onions",
				"farmersdelight:budding_tomatoes",
				"farmersdelight:cabbages",
				"farmersdelight:brown_mushroom_colony",
				"farmersdelight:red_mushroom_colony"
		);

		registerPlant(
				"farmersdelight:wild_rice"
		);

		registerStackable(
				"farmersdelight:rice",
				"farmersdelight:rice_panicles"
		);
	}

	// ------------------------------------------------------------------
	// Serene Shrubbery
	// ------------------------------------------------------------------

	private static void registerSereneShrubbery() {
		registerPlant(
				"serene_shrubbery:red_pansies",
				"serene_shrubbery:white_pansies",
				"serene_shrubbery:yellow_pansies",
				"serene_shrubbery:orange_pansies",
				"serene_shrubbery:pink_pansies",
				"serene_shrubbery:purple_pansies",
				"serene_shrubbery:blue_frost_pansies",
				"serene_shrubbery:panola_pink_pansies",
				"serene_shrubbery:sunrise_pansies",
				"serene_shrubbery:halloween_pansies",
				"serene_shrubbery:hydrangea",
				"serene_shrubbery:pink_hydrangea",
				"serene_shrubbery:purple_hydrangea",
				"serene_shrubbery:red_hydrangea",
				"serene_shrubbery:white_hydrangea",
				"serene_shrubbery:halloween_hydrangea",
				"serene_shrubbery:green_hydrangea",
				"serene_shrubbery:butterfly_bush",
				"serene_shrubbery:white_butterfly_bush",
				"serene_shrubbery:pink_butterfly_bush",
				"serene_shrubbery:indigo_butterfly_bush",
				"serene_shrubbery:twinflower",
				"serene_shrubbery:blanketflower"
		);

		registerPlant(
				"serene_shrubbery:manhattan_lights_lupine",
				"serene_shrubbery:sky_blue_lupine",
				"serene_shrubbery:golden_lupine",
				"serene_shrubbery:purple_lupine",
				"serene_shrubbery:lupine_white",
				"serene_shrubbery:purple_foxglove",
				"serene_shrubbery:sunset_foxglove",
				"serene_shrubbery:peach_foxglove",
				"serene_shrubbery:halloween_foxglove",
				"serene_shrubbery:candy_mountain_foxglove",
				"serene_shrubbery:lavender_foxglove",
				"serene_shrubbery:lupine_pink",
				"serene_shrubbery:white_foxglove",
				"serene_shrubbery:fireweed"
		);
	}

	// ------------------------------------------------------------------
	// No Man's Land
	// ------------------------------------------------------------------

	private static void registerNoMansLand() {
		registerPlant(
				"nomansland:field_mushroom",
				"nomansland:frosted_grass",
				"nomansland:grass_sprouts",
				"nomansland:oat_grass",
				"nomansland:short_beachgrass",
				"nomansland:tall_beachgrass",
				"nomansland:dried_grass",
				"nomansland:fiddlehead",
				"nomansland:mycelium_sprouts",
				"nomansland:cave_weeds",
				"nomansland:mycelium_growths",
				"nomansland:yellow_birch_sapling",
				"nomansland:automnal_oak_sapling",
				"nomansland:pale_cherry_sapling",
				"nomansland:aconite",
				"nomansland:starflower",
				"nomansland:thistle",
				"nomansland:blue_lupine",
				"nomansland:red_lupine",
				"nomansland:yellow_lupine",
				"nomansland:pink_lupine",
				"nomansland:autumn_crocus",
				"nomansland:wild_mint",
				"nomansland:pickleweed",
				"nomansland:barrel_cactus",
				"nomansland:lavender_bush",
				"nomansland:succulent",
				"nomansland:pine_sapling",
				"nomansland:maple_sapling",
				"nomansland:red_maple_sapling",
				"nomansland:walnut_sapling",
				"nomansland:willow_sapling",
				"nomansland:field_mushroom_colony"
		);

		registerPlant(
				"nomansland:cattail",
				"nomansland:reeds"
		);

		registerHanging(
				"nomansland:beard_moss"
		);
	}

	// ------------------------------------------------------------------
	// Extra vanilla blocks added in newer versions
	// ------------------------------------------------------------------

	private static void registerExtraVanilla() {
		registerHanging(
				"minecraft:pale_hanging_moss",
				"minecraft:hanging_roots"
		);
	}

	// ------------------------------------------------------------------
	// Registration helpers. All lookups are optional.
	// ------------------------------------------------------------------

	private static void registerPlant(String... ids) {
		for (String id : ids) {
			lookup(id).ifPresent(block -> SwayAPI.register(block, 1.0F));
		}
	}

	private static void registerStackable(String... ids) {
		for (String id : ids) {
			lookup(id).ifPresent(block -> {
				SugarCaneMultiblockBehavior.addBlock(block);
				SwayAPI.setBlockPipeline(block, List.of(
						BuiltinBehaviors.ENTITY_COLLISION_KEY,
						BuiltinBehaviors.PROXIMITY_FORCE_KEY,
						BuiltinBehaviors.SUGAR_CANE_MULTIBLOCK_KEY,
						BuiltinBehaviors.SUGAR_CANE_DEFORMATION_KEY,
						BuiltinBehaviors.multiplierKey(1.0F)
				));
			});
		}
	}

	private static void registerHanging(String... ids) {
		for (String id : ids) {
			lookup(id).ifPresent(block -> {
				HangingVineMultiblockBehavior.addBlock(block);
				SwayAPI.setBlockPipeline(block, List.of(
						BuiltinBehaviors.ENTITY_COLLISION_KEY,
						BuiltinBehaviors.PROXIMITY_FORCE_KEY,
						BuiltinBehaviors.HANGING_VINE_MULTIBLOCK_KEY,
						BuiltinBehaviors.HANGING_VINE_DEFORMATION_KEY,
						BuiltinBehaviors.VINE_CLIMB_TENSION_KEY,
						BuiltinBehaviors.multiplierKey(1.0F)
				));
			});
		}
	}

	private static Optional<Block> lookup(String id) {
		try {
			/*? >= 1.21.11 {*/
			Identifier /*?} else {*/
					/*ResourceLocation *//*?} */ identifier = /*? >= 1.21.11 {*/ Identifier /*?} else {*/ /*ResourceLocation *//*?} */
					./*? >1.20.1 {*/parse/*?} else { */ /*tryParse*//*?} */(id);
			Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(identifier)
					.filter(b -> b != Blocks.AIR);
			if (block.isPresent()) {
				found++;
				REGISTERED.add(block.get());
			} else {
				missing++;
				ModTemplate.LOGGER.debug("Foliage compat: {} not present, skipping", id);
			}
			return block;
		} catch (Exception e) {
			missing++;
			return Optional.empty();
		}
	}
}
