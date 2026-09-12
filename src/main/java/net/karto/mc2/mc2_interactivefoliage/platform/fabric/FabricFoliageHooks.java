package net.karto.mc2.mc2_interactivefoliage.platform.fabric;

//? fabric && >=26.2 {

/*import com.github.razorplay01.sway.api.SwayAPI;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageModel;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;

/^* Where the GPU foliage renderer meets Fabric: the model wrapper, the draw, and chunks coming and going. ^/
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

		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(context -> GpuFoliageRenderer.draw(context.levelState()));

		// Sodium overwrites every vanilla route that marks a section dirty when a chunk arrives, so chunk
		// loads are followed through Fabric's own event, which fires whichever renderer is used.
		ClientChunkEvents.CHUNK_LOAD.register(GpuFoliageRenderer::discoverChunk);
		ClientChunkEvents.CHUNK_UNLOAD.register(GpuFoliageRenderer::forgetChunk);
	}
}
*///?}
