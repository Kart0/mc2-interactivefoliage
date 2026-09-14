package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.20.1 {

//? fabric && >=26.2 {
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
//?}

/**
 * What the renderer asks about shader packs, answered without Iris's classes ever being loaded when Iris is not
 * installed: every call that reaches {@link IrisFoliageShaders} checks first. Where the shader pack support has not
 * been written yet, a shader pack never counts as loaded.
 */
final class IrisCompat {

	//? fabric && >=26.2 {
	private static final boolean IRIS = ModTemplate.xplat().isModLoaded("iris");
	//?}

	private IrisCompat() {
	}

	/** Whether a shader pack is loaded, which the renderer then draws through. */
	static boolean shaderPackInUse() {
		//? fabric && >=26.2 {
		return IRIS && IrisFoliageShaders.shaderPackInUse();
		//?} else {
		/*return false;
		*///?}
	}

	/** Whether the programs for the loaded pack are built; builds them the first time a pack is asked about. */
	static boolean programsReady() {
		//? fabric && >=26.2 {
		return IRIS && IrisFoliageShaders.ready();
		//?} else {
		/*return false;
		*///?}
	}

	static boolean hasShadowProgram() {
		//? fabric && >=26.2 {
		return IRIS && IrisFoliageShaders.hasShadowProgram();
		//?} else {
		/*return false;
		*///?}
	}

	/** Changes each time a pack loads, so what was meshed for another pack is meshed again. */
	static int programGeneration() {
		//? fabric && >=26.2 {
		return IRIS ? IrisFoliageShaders.generation() : 0;
		//?} else {
		/*return 0;
		*///?}
	}

	//? fabric && >=26.2 {
	/** The vertex format a pack's programs read, which the renderer's region buffers hold while one is loaded. */
	static VertexFormat shaderPackFormat() {
		return IrisFoliageShaders.FORMAT;
	}

	/** The format a section comes out of meshing in while a pack is loaded: Iris's terrain format. */
	static VertexFormat shaderPackMeshFormat() {
		return net.irisshaders.iris.vertices.IrisVertexFormats.TERRAIN;
	}

	/** Draws the foliage into a shader pack's shadow map, from the camera Iris rendered the shadow pass for. */
	@FunctionalInterface
	interface ShadowDraw {
		void draw(org.joml.Matrix4f modelView, org.joml.Matrix4f projection, double cameraX, double cameraY, double cameraZ);
	}

	/**
	 * Hands the renderer's shader pack pipelines -- for the pack's own pass and for its shadow map -- their extra uniform
	 * blocks and its shadow draw to the Iris side.
	 */
	static void setUp(RenderPipeline pipeline, RenderPipeline shadowPipeline, List<BindGroupLayout> layouts,
			ShadowDraw drawShadow) {
		if (IRIS) {
			IrisFoliageShaders.setUp(pipeline, shadowPipeline, layouts, drawShadow);
		}
	}

	static void beginBlock(BufferBuilder builder, BlockState state, int x, int y, int z) {
		if (IRIS) {
			IrisFoliageShaders.beginBlock(builder, state, x, y, z);
		}
	}

	static void endBlock(BufferBuilder builder) {
		if (IRIS) {
			IrisFoliageShaders.endBlock(builder);
		}
	}

	/** Runs a draw as terrain cutout for the pack. */
	static void inTerrainPhase(Runnable draw) {
		if (!IRIS) {
			draw.run();
			return;
		}
		Object previous = IrisFoliageShaders.beginTerrainPhase();
		try {
			draw.run();
		} finally {
			IrisFoliageShaders.endTerrainPhase(previous);
		}
	}
	//?}
}
//?}
