package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.21.1 {

import net.karto.mc2.mc2_interactivefoliage.ModTemplate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;

/**
 * What the mod needs from Sodium when Sodium owns the terrain: whether a box survived its occlusion
 * culling, and a way to ask it to build a chunk again.
 * <p>
 * Sodium has no public API for either. They are public methods on an internal class, but they have kept
 * the same signatures from Sodium 0.5 through 0.9, and Sodium relies on them itself. They are reached by
 * reflection so the mod neither compiles against Sodium nor needs it; without them, culling falls back to
 * the camera frustum alone and rebuilds fall back to vanilla's own route.
 * <p>
 * A section Sodium holds no geometry for is reported visible, so foliage standing on its own is never
 * hidden by mistake -- it only misses out on being culled.
 */
// Public for the mixin that reports Sodium's uploads.
public final class SodiumBridge {

	private static final String RENDERER = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";

	private static final MethodHandle INSTANCE;
	private static final MethodHandle IS_BOX_VISIBLE;
	private static final MethodHandle SCHEDULE_REBUILD;

	static {
		MethodHandle instance = null;
		MethodHandle isBoxVisible = null;
		MethodHandle scheduleRebuild = null;
		if (ModTemplate.xplat().isModLoaded("sodium")) {
			try {
				Class<?> renderer = Class.forName(RENDERER);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				instance = lookup.findStatic(renderer, "instanceNullable", MethodType.methodType(renderer))
						.asType(MethodType.methodType(Object.class));
				isBoxVisible = lookup.findVirtual(renderer, "isBoxVisible", MethodType.methodType(boolean.class,
								double.class, double.class, double.class, double.class, double.class, double.class))
						.asType(MethodType.methodType(boolean.class, Object.class,
								double.class, double.class, double.class, double.class, double.class, double.class));
				scheduleRebuild = lookup.findVirtual(renderer, "scheduleRebuildForChunk",
								MethodType.methodType(void.class, int.class, int.class, int.class, boolean.class))
						.asType(MethodType.methodType(void.class, Object.class,
								int.class, int.class, int.class, boolean.class));
			} catch (ReflectiveOperationException e) {
				ModTemplate.LOGGER.warn("Sodium is installed but could not be reached; foliage will be culled by "
						+ "the camera frustum only, and near foliage may keep being drawn by the chunk mesh "
						+ "until something else rebuilds it", e);
				instance = null;
				isBoxVisible = null;
				scheduleRebuild = null;
			}
		}
		INSTANCE = instance;
		IS_BOX_VISIBLE = isBoxVisible;
		SCHEDULE_REBUILD = scheduleRebuild;
	}

	private SodiumBridge() {
	}

	/** Sodium's world renderer, or null when there is none to ask. */
	static Object renderer() {
		if (INSTANCE == null) {
			return null;
		}
		try {
			return (Object) INSTANCE.invokeExact();
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * Asks Sodium to build a section again, which vanilla's own {@code setSectionDirty} does not do while
	 * Sodium owns the terrain. Without this a section keeps whatever the mesher decided about its foliage
	 * until something else happens to rebuild it -- a block placed, or the light changing.
	 */
	static void scheduleRebuild(int sectionX, int sectionY, int sectionZ) {
		Object renderer = renderer();
		if (renderer == null || SCHEDULE_REBUILD == null) {
			return;
		}
		try {
			SCHEDULE_REBUILD.invokeExact(renderer, sectionX, sectionY, sectionZ, false);
		} catch (Throwable e) {
			// Nothing to do about it: vanilla's route was asked as well, and one of the two is enough.
		}
	}

	/**
	 * Reports each section whose new chunk mesh Sodium has just uploaded, which is the moment it replaces the old one
	 * on screen. Only meshing results count: a translucency sort uploads too, but keeps the geometry it had.
	 */
	public static void onResultsUploaded(Collection<?> results) {
		if (MeshSwaps.BUILD_OUTPUT == null) {
			return;
		}
		try {
			for (Object output : results) {
				if (!MeshSwaps.BUILD_OUTPUT.isInstance(output)) {
					continue;
				}
				Object section = (Object) MeshSwaps.SECTION.invokeExact(output);
				GpuFoliageSplit.onChunkMeshSwapped(
						(int) MeshSwaps.CHUNK_X.invokeExact(section),
						(int) MeshSwaps.CHUNK_Y.invokeExact(section),
						(int) MeshSwaps.CHUNK_Z.invokeExact(section));
			}
		} catch (Throwable e) {
			// Nothing to do about it: a section not reported is let go of by GpuFoliageSplit's safety net.
		}
	}

	/**
	 * Sodium's build results, resolved on first use: the output of a meshing build, the section it belongs to, and
	 * where that section is. Public members of internal classes, under the same names through each release line.
	 */
	private static final class MeshSwaps {
		/** The field holding a build output's section: {@code render} through Sodium 0.8, {@code section} from 0.9. */
		//? >=26.1.2 {
		private static final String SECTION_FIELD = "section";
		//?} else {
		/*private static final String SECTION_FIELD = "render";
		*///?}

		static final Class<?> BUILD_OUTPUT;
		static final MethodHandle SECTION;
		static final MethodHandle CHUNK_X;
		static final MethodHandle CHUNK_Y;
		static final MethodHandle CHUNK_Z;

		static {
			Class<?> buildOutput = null;
			MethodHandle section = null;
			MethodHandle chunkX = null;
			MethodHandle chunkY = null;
			MethodHandle chunkZ = null;
			try {
				Class<?> taskOutput = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput");
				Class<?> renderSection = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.RenderSection");
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				section = lookup.findGetter(taskOutput, SECTION_FIELD, renderSection)
						.asType(MethodType.methodType(Object.class, Object.class));
				MethodType coordinate = MethodType.methodType(int.class);
				MethodType erased = MethodType.methodType(int.class, Object.class);
				chunkX = lookup.findVirtual(renderSection, "getChunkX", coordinate).asType(erased);
				chunkY = lookup.findVirtual(renderSection, "getChunkY", coordinate).asType(erased);
				chunkZ = lookup.findVirtual(renderSection, "getChunkZ", coordinate).asType(erased);
				buildOutput = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput");
			} catch (ReflectiveOperationException e) {
				ModTemplate.LOGGER.warn("Sodium's chunk build results could not be read; foliage handed back to the "
						+ "chunk mesh may blink out for a moment", e);
				buildOutput = null;
			}
			BUILD_OUTPUT = buildOutput;
			SECTION = section;
			CHUNK_X = chunkX;
			CHUNK_Y = chunkY;
			CHUNK_Z = chunkZ;
		}
	}

	/** Whether the box is visible to Sodium. Any failure answers yes, so nothing is hidden wrongly. */
	static boolean isVisible(Object renderer, double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {
		try {
			return (boolean) IS_BOX_VISIBLE.invokeExact(renderer, minX, minY, minZ, maxX, maxY, maxZ);
		} catch (Throwable e) {
			return true;
		}
	}
}
//?}
