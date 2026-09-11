package net.karto.mc2.mc2_interactivefoliage.gpu;

//? fabric && >=26.2 {

import net.fabricmc.loader.api.FabricLoader;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Asks Sodium whether a box survived its occlusion culling, when Sodium is installed.
 * <p>
 * Sodium has no public API for this. {@code SodiumWorldRenderer.instanceNullable()} and
 * {@code isBoxVisible(double...)} are public methods on an internal class, but they have kept the same
 * signatures from Sodium 0.5 through 0.9, and Sodium relies on them itself to cull entities. They are
 * reached by reflection so the mod neither compiles against Sodium nor needs it; if either cannot be
 * found, culling falls back to the camera frustum alone.
 * <p>
 * A section Sodium holds no geometry for is reported visible, so foliage standing on its own is never
 * hidden by mistake -- it only misses out on being culled.
 */
final class SodiumOcclusion {

	private static final String RENDERER = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";

	private static final MethodHandle INSTANCE;
	private static final MethodHandle IS_BOX_VISIBLE;

	static {
		MethodHandle instance = null;
		MethodHandle isBoxVisible = null;
		if (FabricLoader.getInstance().isModLoaded("sodium")) {
			try {
				Class<?> renderer = Class.forName(RENDERER);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				instance = lookup.findStatic(renderer, "instanceNullable", MethodType.methodType(renderer))
						.asType(MethodType.methodType(Object.class));
				isBoxVisible = lookup.findVirtual(renderer, "isBoxVisible", MethodType.methodType(boolean.class,
								double.class, double.class, double.class, double.class, double.class, double.class))
						.asType(MethodType.methodType(boolean.class, Object.class,
								double.class, double.class, double.class, double.class, double.class, double.class));
			} catch (ReflectiveOperationException e) {
				ModTemplate.LOGGER.warn("Sodium is installed but its occlusion culling could not be reached; "
						+ "foliage will be culled by the camera frustum only", e);
				instance = null;
				isBoxVisible = null;
			}
		}
		INSTANCE = instance;
		IS_BOX_VISIBLE = isBoxVisible;
	}

	private SodiumOcclusion() {
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
