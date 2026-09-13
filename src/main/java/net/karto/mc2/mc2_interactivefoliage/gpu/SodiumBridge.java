package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.21.1 {

import net.karto.mc2.mc2_interactivefoliage.ModTemplate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

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
final class SodiumBridge {

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
