package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.20.1 {

import com.github.razorplay01.sway.api.SwayAPI;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
//? >=26.1.2 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter;
*///?}
//? >=26.1.2 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
//?} elif >=1.21.11 {
/*import net.minecraft.client.renderer.block.model.BlockStateModel;
*///?} else {
/*import net.minecraft.client.resources.model.BakedModel;
*///?}
//? >=1.21.11 {
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
//?} else {
/*import net.minecraft.client.renderer.chunk.RenderChunkRegion;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongConsumer;

/**
 * Decides who draws each piece of foliage: {@link GpuFoliageRenderer} near the player, where its sway can be
 * seen, and the chunk mesh further out, where it cannot and the chunk mesh draws it for almost nothing.
 * <p>
 * The split is made in the block model. Every foliage model is wrapped in a {@link GpuFoliageModel}, and while
 * a chunk mesher asks it for the geometry of a block near the player it hands over nothing. Meshers ask through
 * Fabric's rendering API, which carries the block's position -- vanilla does, and so does Sodium from 0.6 on --
 * so the split needs nothing from inside either of them.
 * <p>
 * Each time a mesher builds a section the decision is recorded, and the mesher reports the moment the mesh it
 * built replaces the one on screen. The renderer changes what it draws for a section on that frame, not before,
 * which keeps the two in step as the player moves: a section is never drawn by both, and never left to neither.
 */
public final class GpuFoliageSplit {

	/**
	 * Sodium's snapshot of the level for a chunk build, under the same name from Sodium 0.6 through 0.9, and as
	 * {@code WorldSlice} in its old package before. Null without Sodium, where vanilla's own region is the only
	 * snapshot there is.
	 */
	//? >=1.21.1 {
	private static final Class<?> SODIUM_LEVEL_SLICE = findClass("net.caffeinemc.mods.sodium.client.world.LevelSlice");
	//?} else {
	/*private static final Class<?> SODIUM_LEVEL_SLICE = findClass("me.jellysquid.mods.sodium.client.world.WorldSlice");
	*///?}

	/** The chunks around the player whose foliage the GPU renderer draws. Replaced whole, never edited. */
	private record Area(int centreX, int centreZ, int radius) {
		boolean contains(int chunkX, int chunkZ) {
			return Math.max(Math.abs(chunkX - centreX), Math.abs(chunkZ - centreZ)) <= radius;
		}
	}

	private record MeshDecision(long sectionKey, boolean leftFoliage) {
	}

	/** A chunk mesh built for this section has just replaced the one on screen. */
	private record MeshSwap(long sectionKey) {
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
	/**
	 * Decisions reported by meshing threads, waiting for the render thread to apply them. The chunk mesh swaps
	 * travel in the same queue, so each is seen after the decisions its own build recorded.
	 */
	private static final ConcurrentLinkedQueue<Record> DECISIONS = new ConcurrentLinkedQueue<>();
	/** Render thread only: sections whose chunk mesh on screen was built without their foliage. */
	private static final LongOpenHashSet MESHED_WITHOUT_FOLIAGE = new LongOpenHashSet();

	/**
	 * Render thread only: sections handed back to the chunk mesh whose new mesh is not on screen yet, and when to
	 * stop waiting for it.
	 * <p>
	 * A section's decision is recorded while the chunk mesher reaches its foliage, not when the mesh it builds
	 * replaces the old one: the rest of the section still has to be meshed and uploaded. Letting go at the
	 * decision left the old mesh, the one without the foliage, on screen alone until then, and the foliage blinked
	 * out. The renderer keeps drawing the section until the chunk mesh reports its new mesh in place instead
	 * ({@link #onChunkMeshSwapped}), and stops on that very frame.
	 */
	private static final Long2LongOpenHashMap HANDING_BACK = new Long2LongOpenHashMap();
	/**
	 * A safety net, not the mechanism: should the new mesh never be reported -- its build dropped for good -- the
	 * section is let go of after this long, so it is never drawn twice for good.
	 */
	private static final long HAND_BACK_GIVE_UP_MILLIS = 5000L;

	/**
	 * Render thread only: near sections whose latest chunk build kept their foliage when it should have left it out,
	 * waiting for that build to be on screen so a rebuild can be asked for.
	 * <p>
	 * A build reads the near area as it starts, so one that started before the area reached its section keeps the
	 * foliage. Asking again on a timer cancels vanilla's build in progress each time, and a build that takes longer
	 * than the timer -- a heavy resource pack is enough -- never finishes. Asking once the build is in place never
	 * interrupts one, and Sodium accepts it too: it turns down a rebuild for a section it has not built yet, and by
	 * then the section has been built.
	 */
	private static final LongOpenHashSet NEEDS_REBUILD = new LongOpenHashSet();
	/** Render thread only: sections whose chunk mesh is to be built again, collected for the renderer to ask for. */
	private static final LongArrayList REBUILD_NOW = new LongArrayList();

	/**
	 * Sections the GPU renderer holds uploaded geometry for. Only these may be left out of the chunk mesh: until then the
	 * chunk mesh keeps their foliage, rather than leave it to a renderer with nothing to show yet -- as it would for the
	 * sections of a chunk that arrives inside the near area while the renderer is still working through its queue.
	 * Written by the render thread, read by the meshing threads.
	 */
	private static final Set<Long> GPU_READY = ConcurrentHashMap.newKeySet();

	/**
	 * Render thread only: sections whose chunk mesh has been built without their foliage, but is not on screen yet. The
	 * renderer starts drawing them the moment it is, and not before: until then the old mesh, still holding the
	 * foliage, is what is showing.
	 */
	private static final LongOpenHashSet TAKING_OVER = new LongOpenHashSet();

	/**
	 * The models the mod wrapped, by state. Where Sway deforms inside the model rather than through a
	 * rendering API -- NeoForge -- meshing through vanilla's model set would bake its own deformation into
	 * geometry the GPU is about to move again. Meshing goes through these instead: they sit inside Sway's
	 * wrapper and hand over plain geometry.
	 */
	//? >=1.21.11 {
	private static final Map<BlockState, BlockStateModel> GPU_MODELS = new IdentityHashMap<>();
	//?} else {
	/*// Before 1.21.11 a block's model is a BakedModel.
	private static final Map<BlockState, BakedModel> GPU_MODELS = new IdentityHashMap<>();
	*///?}

	private static Set<Block> foliage;

	//? forge {
	/*private static final String EMBEDDIUM_SLICE_VIEW = "org.embeddedt.embeddium.render.world.WorldSliceLocal";
	*///?}

	private GpuFoliageSplit() {
	}

	/**
	 * Called by a foliage model while its geometry is requested: whether to hand over nothing because the GPU
	 * renderer draws this block. Only chunk builds are split. Anything else that asks -- a block pushed by a
	 * piston, a falling block, the GPU renderer meshing its own sections -- always gets the full geometry.
	 */
	public static boolean leaveToGpu(BlockAndTintGetter level, BlockPos pos) {
		//? >=1.21.11 {
		boolean chunkBuild = level instanceof RenderSectionRegion;
		//?} else {
		/*boolean chunkBuild = level instanceof RenderChunkRegion;
		*///?}
		//? forge {
		/*// Embeddium hands a model a view of its level snapshot, a class it makes up as the game runs, rather than the
		// snapshot itself, so it is known by name.
		chunkBuild = chunkBuild || EMBEDDIUM_SLICE_VIEW.equals(level.getClass().getName());
		*///?}
		if (!chunkBuild &&(SODIUM_LEVEL_SLICE == null || !SODIUM_LEVEL_SLICE.isInstance(level))) {
			return false;
		}
		int sectionX = SectionPos.blockToSectionCoord(pos.getX());
		int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
		long sectionKey = SectionPos.asLong(sectionX, SectionPos.blockToSectionCoord(pos.getY()), sectionZ);
		// A build asks once per foliage block, so the answer is kept for the rest of its section until something it
		// depends on changes: the near area, or what the renderer holds.
		LastDecision last = LAST_DECISION.get();
		int currentGeneration = generation;
		if (last.sectionKey == sectionKey && last.generation == currentGeneration) {
			return last.leftFoliage;
		}
		Area current = area;
		boolean leave = current != null && current.contains(sectionX, sectionZ) && GPU_READY.contains(sectionKey);
		last.sectionKey = sectionKey;
		last.leftFoliage = leave;
		last.generation = currentGeneration;
		DECISIONS.add(new MeshDecision(sectionKey, leave));
		return leave;
	}

	/** Render thread: applies the decisions and chunk mesh swaps reported since the last frame, in order. */
	static void applyMeshDecisions() {
		Record event;
		while ((event = DECISIONS.poll()) != null) {
			if (event instanceof MeshSwap swap) {
				long key = swap.sectionKey();
				// The chunk mesh that took the foliage back is on screen now: the renderer can stop drawing it.
				HANDING_BACK.remove(key);
				// The chunk mesh that let the foliage go is on screen now: the renderer starts drawing it, on this frame.
				if (TAKING_OVER.remove(key)) {
					MESHED_WITHOUT_FOLIAGE.add(key);
				}
				// A near section built with its foliage still in: now that the build is in place, ask for one without.
				if (NEEDS_REBUILD.remove(key) && isNear(SectionPos.x(key), SectionPos.z(key))
						&& !MESHED_WITHOUT_FOLIAGE.contains(key)) {
					REBUILD_NOW.add(key);
				}
				continue;
			}
			MeshDecision decision = (MeshDecision) event;
			long key = decision.sectionKey();
			if (decision.leftFoliage()) {
				// Not drawn yet: the old mesh, which still holds the foliage, stays on screen until this build replaces it.
				// A section still being handed back keeps being drawn meanwhile, as that is what is showing.
				if (!MESHED_WITHOUT_FOLIAGE.contains(key)) {
					TAKING_OVER.add(key);
				}
				NEEDS_REBUILD.remove(key);
			} else {
				TAKING_OVER.remove(key);
				if (MESHED_WITHOUT_FOLIAGE.remove(key)) {
					HANDING_BACK.put(key, System.currentTimeMillis() + HAND_BACK_GIVE_UP_MILLIS);
				}
				// Kept on purpose while the renderer has nothing for it; kept by mistake once it has, and asked again.
				if (isNear(SectionPos.x(key), SectionPos.z(key)) && GPU_READY.contains(key)) {
					NEEDS_REBUILD.add(key);
				}
			}
		}
	}

	/**
	 * Render thread: the chunk mesh of a section has just been replaced by a newly built one. Called by the chunk
	 * mesher itself, vanilla's or Sodium's, as the new mesh takes the old one's place.
	 */
	public static void onChunkMeshSwapped(int sectionX, int sectionY, int sectionZ) {
		long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
		// Only a section the renderer draws, or a near one it is about to, can be waiting on its chunk mesh; every other
		// one is left out of the queue.
		if (MESHED_WITHOUT_FOLIAGE.contains(key) || HANDING_BACK.containsKey(key) || TAKING_OVER.contains(key)
				|| isNear(sectionX, sectionZ)) {
			DECISIONS.add(new MeshSwap(key));
		}
	}

	/**
	 * Any thread: as {@link #onChunkMeshSwapped}, for a chunk mesh that took its place off the render thread. Vanilla
	 * does that for a build with nothing to draw at all -- a section holding only foliage, once the foliage is left out
	 * of it -- so it cannot be skipped. Only the sections the renderer holds geometry for are queued.
	 */
	public static void onChunkMeshSwappedElsewhere(int sectionX, int sectionY, int sectionZ) {
		long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
		if (GPU_READY.contains(key)) {
			DECISIONS.add(new MeshSwap(key));
		}
	}

	/**
	 * Render thread: the GPU renderer now holds uploaded geometry for this section. Returns whether it did not before,
	 * in which case a near section's chunk mesh is due to be built again without its foliage.
	 */
	static boolean markGpuReady(long sectionKey) {
		if (!GPU_READY.add(sectionKey)) {
			return false;
		}
		generation++;
		return true;
	}

	/** Render thread: the GPU renderer no longer holds geometry for this section. */
	static void unmarkGpuReady(long sectionKey) {
		if (GPU_READY.remove(sectionKey)) {
			generation++;
		}
	}

	/** Whether the GPU renderer holds uploaded geometry for this section. */
	static boolean isGpuReady(long sectionKey) {
		return GPU_READY.contains(sectionKey);
	}

	/**
	 * Render thread: hands over each near section whose build has just come back, and is on screen, still holding
	 * its foliage, so a rebuild of it can be asked for.
	 */
	static void forEachRebuildDue(LongConsumer rebuild) {
		for (int i = 0; i < REBUILD_NOW.size(); i++) {
			rebuild.accept(REBUILD_NOW.getLong(i));
		}
		REBUILD_NOW.clear();
	}

	/** Render thread: whether the chunk mesh on screen for this section went without its foliage. */
	static boolean chunkMeshLeftFoliage(long sectionKey) {
		if (MESHED_WITHOUT_FOLIAGE.contains(sectionKey)) {
			return true;
		}
		// Handed back, but the chunk mesh holding the foliage again is not on screen yet.
		long giveUp = HANDING_BACK.get(sectionKey);
		if (giveUp == 0L) {
			return false;
		}
		if (System.currentTimeMillis() > giveUp) {
			HANDING_BACK.remove(sectionKey);
			return false;
		}
		return true;
	}

	//? >=1.21.11 {
	/** Remembers a model the mod wrapped, so the renderer can mesh from it. */
	public static void registerGpuModel(BlockState state, BlockStateModel model) {
		GPU_MODELS.put(state, model);
	}

	/** The model to mesh this block from: the one the mod wrapped where there is one, or vanilla's. */
	static BlockStateModel modelFor(BlockState state, BlockStateModel fallback) {
		BlockStateModel model = GPU_MODELS.get(state);
		return model == null ? fallback : model;
	}
	//?} else {
	/*/^* Remembers a model the mod wrapped, so the renderer can mesh from it. ^/
	public static void registerGpuModel(BlockState state, BakedModel model) {
		GPU_MODELS.put(state, model);
	}

	/^* The model to mesh this block from: the one the mod wrapped where there is one, or vanilla's. ^/
	static BakedModel modelFor(BlockState state, BakedModel fallback) {
		BakedModel model = GPU_MODELS.get(state);
		return model == null ? fallback : model;
	}
	*///?}

	/** Render thread: the section was unloaded, and its next build has to be recorded afresh. */
	static void forgetSection(long sectionKey) {
		HANDING_BACK.remove(sectionKey);
		NEEDS_REBUILD.remove(sectionKey);
		TAKING_OVER.remove(sectionKey);
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
		HANDING_BACK.clear();
		NEEDS_REBUILD.clear();
		REBUILD_NOW.clear();
		TAKING_OVER.clear();
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
