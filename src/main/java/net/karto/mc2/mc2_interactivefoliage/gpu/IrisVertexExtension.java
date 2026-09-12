package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=26.1.2 {

import net.karto.mc2.mc2_interactivefoliage.ModTemplate;

import java.lang.reflect.Field;

/**
 * Keeps Iris from widening the mod's own meshing buffer, when Iris is installed.
 * <p>
 * While a shader pack is loaded and the level is being drawn, Iris rewrites every {@link
 * com.mojang.blaze3d.vertex.BufferBuilder} asked for {@code DefaultVertexFormat.BLOCK} into its own, wider
 * terrain format: it appends the tangents, mid-texture coordinates and block ids a shader pack expects.
 * That is right for anything drawn by a shader pack and wrong for the foliage renderer, which writes its
 * vertices for a pipeline of its own and reads them back at the block format's stride.
 * <p>
 * Iris leaves a way out for exactly this, a thread-local it checks before widening anything, so meshing is
 * wrapped in {@link #begin()} and {@link #end(boolean)}. It is reached by reflection so the mod neither
 * compiles against Iris nor needs it; without Iris, and if the field ever moves, both calls do nothing.
 */
final class IrisVertexExtension {

	private static final String IMMEDIATE_STATE = "net.irisshaders.iris.vertices.ImmediateState";

	private static final ThreadLocal<Boolean> SKIP_EXTENSION;

	static {
		ThreadLocal<Boolean> skipExtension = null;
		if (ModTemplate.xplat().isModLoaded("iris")) {
			try {
				Field field = Class.forName(IMMEDIATE_STATE).getField("skipExtension");
				@SuppressWarnings("unchecked")
				ThreadLocal<Boolean> found = (ThreadLocal<Boolean>) field.get(null);
				skipExtension = found;
			} catch (ReflectiveOperationException | ClassCastException e) {
				ModTemplate.LOGGER.warn("Iris is installed but its vertex format extension could not be turned "
						+ "off for the foliage renderer; drawing foliage on the GPU may fail while a shader "
						+ "pack is loaded", e);
				skipExtension = null;
			}
		}
		SKIP_EXTENSION = skipExtension;
	}

	private IrisVertexExtension() {
	}

	/** Stops Iris widening buffers on this thread, and returns what to hand back to {@link #end(boolean)}. */
	static boolean begin() {
		if (SKIP_EXTENSION == null) {
			return false;
		}
		boolean previous = Boolean.TRUE.equals(SKIP_EXTENSION.get());
		SKIP_EXTENSION.set(Boolean.TRUE);
		return previous;
	}

	/** Puts back what {@link #begin()} found, so nothing else that asked for the same is cut short. */
	static void end(boolean previous) {
		if (SKIP_EXTENSION != null) {
			SKIP_EXTENSION.set(previous);
		}
	}
}
//?}
