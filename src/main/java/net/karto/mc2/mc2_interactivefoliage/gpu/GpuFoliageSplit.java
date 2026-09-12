package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.21.11 {

import com.github.razorplay01.sway.api.SwayAPI;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
//? >=26.1.2 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter;
*///?}
//? >=26.1.2 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
//?} else {
/*import net.minecraft.client.renderer.block.model.BlockStateModel;
*///?}
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Decides who draws each piece of foliage: {@link GpuFoliageRenderer} near the player, where its sway can be
 * seen, and the chunk mesh further out, where it cannot and the chunk mesh draws it for almost nothing.
 * <p>
 * The split is made in the block model. Every foliage model is wrapped in a {@link GpuFoliageModel}, and while
 * a chunk mesher asks it for the geometry of a block near the player it hands over nothing. Meshers ask through
 * Fabric's rendering API, which carries the block's position -- vanilla does, and so does Sodium from 0.6 on --
 * so the split needs nothing from inside either of them.
 * <p>
 * Each time a mesher builds a section the decision is recorded, and the renderer draws only the sections
 * recorded as meshed without their foliage. That keeps the two in step as the player moves: a section is never
 * drawn by both, and never left to neither.
 */
public final class GpuFoliageSplit {

	/**
	 * Sodium's snapshot of the level for a chunk build, under the same name from Sodium 0.6 through 0.9. Null
	 * without Sodium, where vanilla's {@link RenderSectionRegion} is the only snapshot there is.
	 */
	private static final Class<?> SODIUM_LEVEL_SLICE = findClass("net.caffeinemc.mods.sodium.client.world.LevelSlice");

	/** The chunks around the player whose foliage the GPU renderer draws. Replaced whole, never edited. */
	private record Area(int centreX, int centreZ, int radius) {
		boolean contains(int chunkX, int chunkZ) {
			return Math.max(Math.abs(chunkX - centreX), Math.abs(chunkZ - centreZ)) <= radius;
		}
	}

	private record MeshDecision(long sectionKey, boolean leftFoliage) {
	}

	/** The last decision a meshing thread recorded, so a build records once rather than once per block. */
	private static final class LastDecision {
		long sectionKey;
		boolean leftFoliage;
		int generation = -1;
	}

	private static volatile Area area;
	/**
	 * Bumped whenever a recorded decision may no longer stand -- the area moved, or a section was forgotten --
	 * so meshing threads record their next decisions again instead of skipping them as already known.
	 */
	private static volatile int generation;

	private static final ThreadLocal<LastDecision> LAST_DECISION = ThreadLocal.withInitial(LastDecision::new);
	/** Decisions reported by meshing threads, waiting for the render thread to apply them. */
	private static final ConcurrentLinkedQueue<MeshDecision> DECISIONS = new ConcurrentLinkedQueue<>();
	/** Render thread only: sections whose chunk mesh was built without their foliage. */
	private static final LongOpenHashSet MESHED_WITHOUT_FOLIAGE = new LongOpenHashSet();

	/**
	 * The models the mod wrapped, by state. Where Sway deforms inside the model rather than through a
	 * rendering API -- NeoForge -- meshing through vanilla's model set would bake its own deformation into
	 * geometry the GPU is about to move again. Meshing goes through these instead: they sit inside Sway's
	 * wrapper and hand over plain geometry.
	 */
	private static final Map<BlockState, BlockStateModel> GPU_MODELS = new IdentityHashMap<>();

	private static Set<Block> foliage;

	private GpuFoliageSplit() {
	}

	/**
	 * Called by a foliage model while its geometry is requested: whether to hand over nothing because the GPU
	 * renderer draws this block. Only chunk builds are split. Anything else that asks -- a block pushed by a
	 * piston, a falling block, the GPU renderer meshing its own sections -- always gets the full geometry.
	 */
	public static boolean leaveToGpu(BlockAndTintGetter level, BlockPos pos) {
		if (!(level instanceof RenderSectionRegion) && (SODIUM_LEVEL_SLICE == null || !SODIUM_LEVEL_SLICE.isInstance(level))) {
			return false;
		}
		int sectionX = SectionPos.blockToSectionCoord(pos.getX());
		int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
		Area current = area;
		boolean near = current != null && current.contains(sectionX, sectionZ);
		record(SectionPos.asLong(sectionX, SectionPos.blockToSectionCoord(pos.getY()), sectionZ), near);
		return near;
	}

	private static void record(long sectionKey, boolean leftFoliage) {
		LastDecision last = LAST_DECISION.get();
		int currentGeneration = generation;
		if (last.sectionKey == sectionKey && last.leftFoliage == leftFoliage && last.generation == currentGeneration) {
			return;
		}
		last.sectionKey = sectionKey;
		last.leftFoliage = leftFoliage;
		last.generation = currentGeneration;
		DECISIONS.add(new MeshDecision(sectionKey, leftFoliage));
	}

	/** Render thread: applies the decisions recorded since the last frame. */
	static void applyMeshDecisions() {
		MeshDecision decision;
		while ((decision = DECISIONS.poll()) != null) {
			if (decision.leftFoliage()) {
				MESHED_WITHOUT_FOLIAGE.add(decision.sectionKey());
			} else {
				MESHED_WITHOUT_FOLIAGE.remove(decision.sectionKey());
			}
		}
	}

	/** Render thread: whether this section's chunk mesh was built without its foliage. */
	static boolean chunkMeshLeftFoliage(long sectionKey) {
		return MESHED_WITHOUT_FOLIAGE.contains(sectionKey);
	}

	/** Remembers a model the mod wrapped, so the renderer can mesh from it. */
	public static void registerGpuModel(BlockState state, BlockStateModel model) {
		GPU_MODELS.put(state, model);
	}

	/** The model to mesh this block from: the one the mod wrapped where there is one, or vanilla's. */
	static BlockStateModel modelFor(BlockState state, BlockStateModel fallback) {
		BlockStateModel model = GPU_MODELS.get(state);
		return model == null ? fallback : model;
	}

	/** Render thread: the section was unloaded, and its next build has to be recorded afresh. */
	static void forgetSection(long sectionKey) {
		if (MESHED_WITHOUT_FOLIAGE.remove(sectionKey)) {
			generation++;
		}
	}

	/** Whether the GPU renderer draws the foliage of this chunk column. */
	static boolean isNear(int chunkX, int chunkZ) {
		Area current = area;
		return current != null && current.contains(chunkX, chunkZ);
	}

	static boolean hasArea() {
		return area != null;
	}

	static int centreX() {
		Area current = area;
		return current == null ? 0 : current.centreX();
	}

	static int centreZ() {
		Area current = area;
		return current == null ? 0 : current.centreZ();
	}

	static int radius() {
		Area current = area;
		return current == null ? 0 : current.radius();
	}

	/** Render thread: moves the area, published as one object so no thread ever reads half of a move. */
	static void setArea(int centreX, int centreZ, int radius) {
		area = new Area(centreX, centreZ, radius);
		generation++;
	}

	/** Render thread: no area at all, so every chunk build keeps its foliage. */
	static void clearArea() {
		area = null;
		generation++;
	}

	/**
	 * Whether this block is foliage the GPU renderer draws: every block Sway animates, the vanilla foliage it
	 * registers itself plus whatever {@link net.karto.mc2.mc2_interactivefoliage.ModCompatRegistry} found.
	 * <p>
	 * Resolved once and cached, because {@code isInteractive} walks Sway's registries. Render thread only.
	 */
	static boolean isFoliage(BlockState state) {
		if (foliage == null) {
			Set<Block> found = Collections.newSetFromMap(new IdentityHashMap<>());
			for (Block block : BuiltInRegistries.BLOCK) {
				if (SwayAPI.isInteractive(block)) {
					found.add(block);
				}
			}
			foliage = found;
		}
		return foliage.contains(state.getBlock());
	}

	private static Class<?> findClass(String name) {
		try {
			return Class.forName(name, false, GpuFoliageSplit.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			return null;
		}
	}
}
//?}
