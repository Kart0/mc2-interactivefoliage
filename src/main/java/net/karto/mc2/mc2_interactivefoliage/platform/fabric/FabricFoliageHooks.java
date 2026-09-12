package net.karto.mc2.mc2_interactivefoliage.platform.fabric;

//? fabric && >=1.21.11 {

import com.github.razorplay01.sway.api.SwayAPI;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
//? >=26.1.2 {
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
*///?}
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageModel;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;

/** Where the GPU foliage renderer meets Fabric: the model wrapper, the draw, and chunks coming and going. */
public final class FabricFoliageHooks {

	private FabricFoliageHooks() {
	}

	public static void register() {
		// Wrapped outside Sway's own wrapper, so everything our wrapper does not withhold still goes
		// through Sway. Here that costs nothing extra: Fabric's meshing asks models through the rendering
		// API, which Sway hooks, while the renderer meshes through vanilla's path, which it does not.
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
				ModelModifier.WRAP_LAST_PHASE,
				(model, bake) -> SwayAPI.isInteractive(bake.state().getBlock()) ? new GpuFoliageModel(model) : model));

		//? >=26.1.2 {
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(context -> GpuFoliageRenderer.draw(context.levelState()));
		//?} else {
		/*// There is no event for the moment the opaque terrain is done before 26.1.2. The one before the
		// translucent pass is the nearest: the opaque blocks are drawn by then, and the foliage still lands
		// before anything see-through, which is what it has to be behind.
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> GpuFoliageRenderer.draw(context.worldState()));
		*///?}

		// Sodium overwrites every vanilla route that marks a section dirty when a chunk arrives, so chunk
		// loads are followed through Fabric's own event, which fires whichever renderer is used.
		ClientChunkEvents.CHUNK_LOAD.register(GpuFoliageRenderer::discoverChunk);
		ClientChunkEvents.CHUNK_UNLOAD.register(GpuFoliageRenderer::forgetChunk);
	}
}
//?}
