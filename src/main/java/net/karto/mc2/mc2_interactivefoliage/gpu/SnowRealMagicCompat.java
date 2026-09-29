package net.karto.mc2.mc2_interactivefoliage.gpu;

import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

//? <1.21.11 {
/*import net.minecraft.client.resources.model.BakedModel;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
*///?}

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Snow! Real Magic lets snow settle on plants: the plant's block becomes its snow layer block, and the plant lives on
 * inside that block's block entity, drawn through its own model at the same position before the snow. The renderer
 * meshes the plant inside instead of the block, so plants in snow sway near the player as they do further out; the
 * snow stays with the chunk mesh, which draws it on its own. Sway pushes the plant through the snow block, which the
 * mod registers for it (see ModCompatRegistry).
 * <p>
 * Read by name, as the mod is not compiled against: without it, or with a version that does not match, nothing here
 * applies and the plants in its snow stay with the chunk mesh.
 */
public final class SnowRealMagicCompat {

	private static final String MOD_ID = "snowrealmagic";
	/**
	 * The classes of the mod's snow blocks that hold a plant; its fences, stairs and walls hold what they were made
	 * from. Up to 10.x (1.20.1) one block, snowrealmagic:snow; from 12.x (1.21.1) several of one class: snow,
	 * snow_extra_collision, snowy_plant, and a tall plant's two halves, snowy_double_plant_lower and _upper.
	 */
	private static final String[] SNOW_BLOCKS = {"snownee.snow.block.EntitySnowLayerBlock", "snownee.snow.block.SRMSnowLayerBlock"};
	private static final String SNOW_BLOCK_ENTITY = "snownee.snow.block.entity.SnowBlockEntity";
	private static final String CORE_MODULE = "snownee.snow.CoreModule";
	/** How far the mod raises the plants it names in its offset_y tag, above the snow. */
	private static final float OFFSET_Y = 0.101F;

	private static final boolean LOADED = ModTemplate.xplat().isModLoaded(MOD_ID);
	/** The plant a snow block holds, or null while the mod is not installed. */
	private static final MethodHandle CONTAINED = LOADED ? findContained() : null;
	private static final TagKey<?> RAISED = LOADED ? findRaisedTag() : null;
	/**
	 * The snow blocks themselves, found once by their class and looked up by identity from then on: the renderer asks
	 * about every block of every section it scans. Empty until they are looked for, and when there are none.
	 */
	private static final Set<Block> SNOW_BLOCK_SET = Collections.newSetFromMap(new IdentityHashMap<>());
	private static boolean snowBlocksSought;
	private static boolean warned;

	private SnowRealMagicCompat() {
	}

	private static MethodHandle findContained() {
		try {
			Class<?> entity = Class.forName(SNOW_BLOCK_ENTITY, false, SnowRealMagicCompat.class.getClassLoader());
			return MethodHandles.publicLookup().findVirtual(entity, "getContainedState", MethodType.methodType(BlockState.class))
					.asType(MethodType.methodType(BlockState.class, Object.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			ModTemplate.LOGGER.warn("Snow! Real Magic is installed, but not a version the GPU renderer knows: plants in its "
					+ "snow are left to the chunk mesh", e);
			return null;
		}
	}

	private static TagKey<?> findRaisedTag() {
		try {
			return (TagKey<?>) Class.forName(CORE_MODULE, false, SnowRealMagicCompat.class.getClassLoader())
					.getField("OFFSET_Y").get(null);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			return null;
		}
	}

	/** Whether Snow! Real Magic is installed, and a version the renderer can read. */
	static boolean isAvailable() {
		return CONTAINED != null;
	}

	/** Whether this is one of the snow blocks that can hold a plant. */
	static boolean isSnowBlock(BlockState state) {
		if (CONTAINED == null) {
			return false;
		}
		if (!snowBlocksSought) {
			// Asked only once the world is drawn, when every block is registered.
			snowBlocksSought = true;
			for (String name : SNOW_BLOCKS) {
				Class<?> type;
				try {
					type = Class.forName(name, false, SnowRealMagicCompat.class.getClassLoader());
				} catch (ClassNotFoundException | LinkageError e) {
					continue;
				}
				for (Block block : BuiltInRegistries.BLOCK) {
					if (type.isInstance(block)) {
						SNOW_BLOCK_SET.add(block);
					}
				}
			}
		}
		return SNOW_BLOCK_SET.contains(state.getBlock());
	}

	/**
	 * The plant the snow block at this position holds and draws, or null for any other block, an empty one, or snow
	 * piled up to a full block, which the mod draws without the plant.
	 */
	static BlockState plantIn(BlockGetter level, BlockPos pos, BlockState state) {
		if (!isSnowBlock(state)
				|| state.hasProperty(SnowLayerBlock.LAYERS) && state.getValue(SnowLayerBlock.LAYERS) >= SnowLayerBlock.MAX_HEIGHT) {
			return null;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity == null) {
			return null;
		}
		try {
			BlockState plant = (BlockState) CONTAINED.invokeExact((Object) entity);
			return plant == null || plant.isAir() ? null : plant;
		} catch (Throwable e) {
			if (!warned) {
				warned = true;
				ModTemplate.LOGGER.warn("Could not read the plant inside Snow! Real Magic's snow", e);
			}
			return null;
		}
	}

	/** What stands at this position as far as the foliage goes: the plant inside the snow there, or the block. */
	static BlockState plantAt(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockState plant = plantIn(level, pos, state);
		return plant != null ? plant : state;
	}

	//? <1.21.11 {
	/*private static final String SNOW_VARIANT_MODEL = "snownee.snow.client.model.SnowVariantModel";
	private static final String SNOW_CLIENT_CONFIG = "snownee.snow.client.SnowClientConfig";
	/^*
	 * The fields the wrappers around a plant's model keep what they wrap in: Fabric's forwarding model (Snow! Real
	 * Magic's own wrappers, and the mod's on Fabric), Sway's, and Forge's (the mod's on Forge).
	 ^/
	private static final String[] WRAPPED_FIELDS = {"wrapped", "parent", "originalModel"};
	private static Field snowyVariantModel;
	private static Field snowVariantsOn;
	private static boolean variantsUnknown;
	/^*
	 * Each plant model's snowy variant, or NO_VARIANT where it has none, found once: the wrappers are walked by
	 * reflection. Weak, so the models of a resource reload before go with it -- which a value holding its own key would
	 * stop, hence the marker.
	 ^/
	private static final Map<BakedModel, Object> VARIANTS = new WeakHashMap<>();
	private static final Object NO_VARIANT = new Object();

	/^*
	 * The model Snow! Real Magic draws a plant with where snow touches it, as the chunk mesh shows it: its snowy
	 * variant, for the plant held in the snow and for the top half of a tall plant standing on it. The plant's own model
	 * where there is none, or where the player has switched the snowy variants off.
	 ^/
	static BakedModel snowyVariant(BakedModel model, BlockState state, BlockGetter level, BlockPos pos, boolean inSnow) {
		if (!inSnow && !standsOnSnow(level, pos, state)) {
			return model;
		}
		BakedModel variant = snowyVariantOf(model);
		return variant != null ? variant : model;
	}

	/^*
	 * The snowy variant of a plant's model, or null where it has none or the player has switched them off. Snow! Real
	 * Magic keeps it in a wrapper of its own around the plant's model, found among the wrappers around it -- the mod's,
	 * Sway's, Snow! Real Magic's.
	 ^/
	public static BakedModel snowyVariantOf(BakedModel model) {
		if (CONTAINED == null || variantsUnknown) {
			return null;
		}
		try {
			if (snowVariantsOn == null) {
				ClassLoader loader = SnowRealMagicCompat.class.getClassLoader();
				snowyVariantModel = Class.forName(SNOW_VARIANT_MODEL, false, loader).getDeclaredField("variantModel");
				snowyVariantModel.setAccessible(true);
				snowVariantsOn = Class.forName(SNOW_CLIENT_CONFIG, false, loader).getField("snowVariants");
			}
			if (!snowVariantsOn.getBoolean(null)) {
				return null;
			}
			Object known = VARIANTS.get(model);
			if (known != null) {
				return known == NO_VARIANT ? null : (BakedModel) known;
			}
			BakedModel found = null;
			Object current = model;
			for (int depth = 0; depth < 8 && current != null; depth++) {
				if (current.getClass().getName().equals(SNOW_VARIANT_MODEL)) {
					found = (BakedModel) snowyVariantModel.get(current);
					break;
				}
				current = wrappedBy(current);
			}
			VARIANTS.put(model, found != null ? found : NO_VARIANT);
			return found;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			variantsUnknown = true;
			ModTemplate.LOGGER.warn("Could not find Snow! Real Magic's snowy plant models: plants in its snow are drawn "
					+ "without snow", e);
			return null;
		}
	}

	/^* What a wrapper around a model wraps, or null for a model that wraps nothing this knows of. ^/
	private static Object wrappedBy(Object wrapper) throws IllegalAccessException {
		for (Class<?> type = wrapper.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (String name : WRAPPED_FIELDS) {
				Field field;
				try {
					field = type.getDeclaredField(name);
				} catch (NoSuchFieldException e) {
					continue;
				}
				if (BakedModel.class.isAssignableFrom(field.getType())) {
					field.setAccessible(true);
					Object inner = field.get(wrapper);
					return inner != wrapper ? inner : null;
				}
			}
		}
		return null;
	}

	//? forge {
	/^private static Object drawnInSnowProperty;
	private static boolean drawnInSnowUnknown;

	/^¹*
	 * Whether a plant's model is being drawn inside Snow! Real Magic's snow: it then draws the plant through the plant's
	 * own model, handed its snow's model data, which carries its options.
	 ¹^/
	public static boolean drawnInSnow(net.minecraftforge.client.model.data.ModelData data) {
		if (CONTAINED == null || drawnInSnowUnknown || data == null) {
			return false;
		}
		try {
			if (drawnInSnowProperty == null) {
				drawnInSnowProperty = Class.forName(SNOW_BLOCK_ENTITY, false, SnowRealMagicCompat.class.getClassLoader())
						.getField("OPTIONS").get(null);
			}
			return data.has((net.minecraftforge.client.model.data.ModelProperty<?>) drawnInSnowProperty);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			drawnInSnowUnknown = true;
			return false;
		}
	}
	^///?}
	*///?}

	/**
	 * Whether Snow! Real Magic draws the plant at this position snowy, as the chunk mesh shows it: a plant held in its
	 * snow (the level has the snow there, the plant being drawn from inside it), or the top half of a tall plant
	 * standing on it.
	 */
	public static boolean drawnSnowy(BlockGetter level, BlockPos pos, BlockState plant) {
		return CONTAINED != null && (isSnowBlock(level.getBlockState(pos)) && !isSnowBlock(plant)
				|| standsOnSnow(level, pos, plant));
	}

	/** Whether this is the top half of a tall plant whose lower half is held in Snow! Real Magic's snow. */
	public static boolean standsOnSnow(BlockGetter level, BlockPos pos, BlockState state) {
		return CONTAINED != null && state.hasProperty(DoublePlantBlock.HALF) && isSnowBlock(level.getBlockState(pos.below()));
	}

	/** How far the mod raises a plant it holds, which the chunk mesh shows: some small plants sit on the snow. */
	@SuppressWarnings("unchecked")
	static float liftOf(BlockState plant) {
		return RAISED != null && plant.is((TagKey<Block>) RAISED) ? OFFSET_Y : 0.0F;
	}
}
