package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.20.1 {

//? iris {
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.world.level.block.state.BlockState;
//? >=1.21.11 {
import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.util.List;
//?} else {
/*import net.minecraft.client.renderer.ShaderInstance;
*///?}
//?}

/**
 * What the renderer asks about shader packs, answered without Iris's classes ever being loaded when Iris is not
 * installed: every call that reaches {@link IrisFoliageShaders} checks first. Where the shader pack support has not
 * been written yet, a shader pack never counts as loaded.
 */
final class IrisCompat {

	//? iris {
	private static final boolean IRIS = ModTemplate.xplat().isModLoaded("iris");
	//?}

	private IrisCompat() {
	}

	/** Whether a shader pack is loaded, which the renderer then draws through. */
	static boolean shaderPackInUse() {
		//? iris {
		return IRIS && IrisFoliageShaders.shaderPackInUse();
		//?} else {
		/*return false;
		*///?}
	}

	/** Whether the programs for the loaded pack are built; builds them the first time a pack is asked about. */
	static boolean programsReady() {
		//? iris {
		return IRIS && IrisFoliageShaders.ready();
		//?} else {
		/*return false;
		*///?}
	}

	static boolean hasShadowProgram() {
		//? iris {
		return IRIS && IrisFoliageShaders.hasShadowProgram();
		//?} else {
		/*return false;
		*///?}
	}

	/** Changes each time a pack loads, so what was meshed for another pack is meshed again. */
	static int programGeneration() {
		//? iris {
		return IRIS ? IrisFoliageShaders.generation() : 0;
		//?} else {
		/*return 0;
		*///?}
	}

	//? iris {
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
	 * blocks (bind group layouts from 26.2, uniform descriptions before) and its shadow draw to the Iris side.
	 */
	//? >=1.21.11 {
	static void setUp(RenderPipeline pipeline, RenderPipeline shadowPipeline, List<?> layouts,
			ShadowDraw drawShadow) {
		if (IRIS) {
			IrisFoliageShaders.setUp(pipeline, shadowPipeline, layouts, drawShadow);
		}
	}
	//?} else {
	/*static void setUp(ShadowDraw drawShadow) {
		if (IRIS) {
			IrisFoliageShaders.setUp(drawShadow);
		}
	}

	/^* The pack's program the foliage is drawn with, in its own pass or its shadow pass, or null if there is none. ^/
	static ShaderInstance program(boolean shadowPass) {
		return IRIS ? IrisFoliageShaders.program(shadowPass) : null;
	}

	/^* Sets the sway's own uniforms on a pack's program, once it is applied. ^/
	static void setSwayUniforms(ShaderInstance program, float intensity, org.joml.Vector4f edge, int cameraX, int cameraY,
			int cameraZ, float offsetX, float offsetY, float offsetZ, float gameTime) {
		if (IRIS) {
			IrisFoliageShaders.setSwayUniforms(program, intensity, edge.x, edge.y, edge.z, edge.w, cameraX, cameraY,
					cameraZ, offsetX, offsetY, offsetZ, gameTime);
		}
	}
	*///?}

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
