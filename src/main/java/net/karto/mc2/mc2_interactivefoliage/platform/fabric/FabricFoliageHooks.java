package net.karto.mc2.mc2_interactivefoliage.platform.fabric;

//? fabric {

import com.github.razorplay01.sway.api.SwayAPI;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
//? >=26.1.2 {
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
//?} elif >=1.21.11 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
*///?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
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
		//? >=1.21.11 {
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
				ModelModifier.WRAP_LAST_PHASE,
				(model, bake) -> SwayAPI.isInteractive(bake.state().getBlock()) ? new GpuFoliageModel(model) : model));
		//?} else {
		/*// Before 1.21.11 a baked model is not told which block state it is for, only the id it was baked
		// under. A block state's model is baked under the block's own id and the state's variant, so the
		// block is read straight from that; item models and the models they are built from are left alone.
		ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register(
				ModelModifier.WRAP_LAST_PHASE,
				//? >=1.21.1 {
				(model, bake) -> isFoliageBlockModel(bake.topLevelId()) ? new GpuFoliageModel(model) : model));
				//?} else {
				/^(model, bake) -> isFoliageBlockModel(bake.id()) ? new GpuFoliageModel(model) : model));
				^///?}
		*///?}

		//? >=26.1.2 {
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(context -> GpuFoliageRenderer.draw(context.levelState()));
		//?} elif >=1.21.11 {
		/*// There is no event for the moment the opaque terrain is done before 26.1.2. The one before the
		// translucent pass is the nearest: the opaque blocks are drawn by then, and the foliage still lands
		// before anything see-through, which is what it has to be behind.
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> GpuFoliageRenderer.draw(context.worldState()));
		*///?} else {
		/*// Fired straight after the solid and cutout terrain, the same moment the newer event marks. The frustum
		// comes with it, since nothing else hands it out on this version.
		WorldRenderEvents.BEFORE_ENTITIES.register(context -> GpuFoliageRenderer.draw(
				//? >=1.21.1 {
				context.camera().getPosition(), context.frustum(), context.positionMatrix(), context.projectionMatrix()));
				//?} else {
				/^// The terrain's view matrix is the one on top of the pose stack it is drawn with.
				context.camera().getPosition(), context.frustum(), context.matrixStack().last().pose(),
				context.projectionMatrix()));
				^///?}

		// The foliage shader is loaded with the game's own core shaders, and again on every resource reload.
		CoreShaderRegistrationCallback.EVENT.register(shaders -> shaders.register(
				//? >=1.21.1 {
				ResourceLocation.fromNamespaceAndPath(ModTemplate.MOD_ID, "foliage_legacy"),
				//?} else {
				/^// Its own copy before 1.21.1, whose shader library measures fog from the view rather than the camera.
				new ResourceLocation(ModTemplate.MOD_ID, "foliage_legacy_1_20"),
				^///?}
				GpuFoliageRenderer.legacyVertexFormat(),
				GpuFoliageRenderer::onLegacyShaderLoaded));
		*///?}

		// Sodium overwrites every vanilla route that marks a section dirty when a chunk arrives, so chunk
		// loads are followed through Fabric's own event, which fires whichever renderer is used.
		ClientChunkEvents.CHUNK_LOAD.register(GpuFoliageRenderer::discoverChunk);
		ClientChunkEvents.CHUNK_UNLOAD.register(GpuFoliageRenderer::forgetChunk);
	}

	//? >=1.21.1 && <1.21.11 {
	/*private static boolean isFoliageBlockModel(ModelResourceLocation id) {
		if (id == null || ModelResourceLocation.INVENTORY_VARIANT.equals(id.variant())) {
			return false;
		}
		return BuiltInRegistries.BLOCK.getOptional(id.id()).map(SwayAPI::isInteractive).orElse(false);
	}
	*///?} elif <1.21.1 {
	/*// Before 1.21.1 every baked model comes through here, the ones block states and items are built from as well, and
	// only a model location says which variant it is; a block state's carries the block's id and the state.
	private static boolean isFoliageBlockModel(ResourceLocation id) {
		if (!(id instanceof ModelResourceLocation location) || "inventory".equals(location.getVariant())) {
			return false;
		}
		return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(location.getNamespace(), location.getPath()))
				.map(SwayAPI::isInteractive).orElse(false);
	}
	*///?}
}
//?}
