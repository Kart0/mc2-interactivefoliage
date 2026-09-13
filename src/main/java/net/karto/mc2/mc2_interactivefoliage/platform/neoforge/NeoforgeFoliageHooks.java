package net.karto.mc2.mc2_interactivefoliage.platform.neoforge;

//? neoforge && >=1.21.1 {

/*import com.github.razorplay01.sway.api.SwayAPI;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
import net.minecraft.client.multiplayer.ClientLevel;
//? >=26.1.2 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
//?} elif >=1.21.11 {
/^import net.minecraft.client.renderer.block.model.BlockStateModel;
^///?} else {
/^import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import java.util.HashMap;
^///?}
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.Map;

/^*
 * Where the GPU foliage renderer meets NeoForge: the model wrapper, the draw, and chunks coming and going.
 ^/
@EventBusSubscriber(modid = ModTemplate.MOD_ID, value = Dist.CLIENT)
public final class NeoforgeFoliageHooks {

	private NeoforgeFoliageHooks() {
	}

	//? >=1.21.11 {
	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent.AfterOpaqueBlocks event) {
		GpuFoliageRenderer.draw(event.getLevelRenderState());
	}
	//?} else {
	/^// Straight after the cutout terrain, the last of the opaque layers. Sodium fires the same stage as it
	// draws its own layers. The camera, the frustum and the terrain's matrices come with it.
	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
			GpuFoliageRenderer.draw(event.getCamera().getPosition(), event.getFrustum(),
					event.getModelViewMatrix(), event.getProjectionMatrix());
		}
	}
	^///?}

	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load event) {
		// Loaded client chunks are always full ones, though before 1.21.11 the event only promises a ChunkAccess.
		if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
			GpuFoliageRenderer.discoverChunk(level, chunk);
		}
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
			GpuFoliageRenderer.forgetChunk(level, chunk);
		}
	}

	//? >=1.21.11 {
	/^*
	 * Wraps the model of every block Sway animates, first of all: Sway wraps afterwards, so ours ends up
	 * inside its wrapper. That is what lets the renderer mesh plain geometry while the chunk mesh still gets
	 * Sway's deformation on everything our wrapper hands over.
	 ^/
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
		Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
		for (Map.Entry<BlockState, BlockStateModel> entry : new ArrayList<>(models.entrySet())) {
			if (!SwayAPI.isInteractive(entry.getKey().getBlock())) {
				continue;
			}
			NeoforgeFoliageModel wrapped = new NeoforgeFoliageModel(entry.getValue());
			GpuFoliageSplit.registerGpuModel(entry.getKey(), wrapped);
			models.put(entry.getKey(), wrapped);
		}
	}
	//?} else {
	/^// The models of the blocks Sway animates, as they were baked, by the id they were baked under. Kept only
	// from one handler to the next within a single bake.
	private static final Map<ModelResourceLocation, BakedModel> PLAIN_MODELS = new HashMap<>();

	// First of all, before Sway wraps them: the plain models are what the renderer meshes from, so its
	// geometry carries no deformation of Sway's on top of the one its shader adds.
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void keepPlainModels(ModelEvent.ModifyBakingResult event) {
		PLAIN_MODELS.clear();
		for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
			if (isFoliageBlockModel(entry.getKey())) {
				PLAIN_MODELS.put(entry.getKey(), entry.getValue());
			}
		}
	}

	// Last of all, once Sway has wrapped them: see NeoforgeFoliageModel for why it sits outside.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void wrapModels(ModelEvent.ModifyBakingResult event) {
		Map<ModelResourceLocation, BakedModel> models = event.getModels();
		for (ModelResourceLocation id : PLAIN_MODELS.keySet()) {
			BakedModel model = models.get(id);
			if (model != null) {
				models.put(id, new NeoforgeFoliageModel(model));
			}
		}
		// The renderer looks models up by block state, so each state is matched to the id its model has.
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!SwayAPI.isInteractive(block)) {
				continue;
			}
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				BakedModel plain = PLAIN_MODELS.get(BlockModelShaper.stateToModelLocation(state));
				if (plain != null) {
					GpuFoliageSplit.registerGpuModel(state, plain);
				}
			}
		}
		PLAIN_MODELS.clear();
	}

	// The foliage shader is loaded with the game's own core shaders, and again on every resource reload.
	@SubscribeEvent
	public static void registerShaders(RegisterShadersEvent event) throws IOException {
		event.registerShader(
				new ShaderInstance(event.getResourceProvider(),
						ResourceLocation.fromNamespaceAndPath(ModTemplate.MOD_ID, "foliage_legacy"),
						GpuFoliageRenderer.legacyVertexFormat()),
				GpuFoliageRenderer::onLegacyShaderLoaded);
	}

	// A block state's model is baked under the block's id and the state's variant; item models are left alone.
	private static boolean isFoliageBlockModel(ModelResourceLocation id) {
		if (ModelResourceLocation.INVENTORY_VARIANT.equals(id.variant())) {
			return false;
		}
		return BuiltInRegistries.BLOCK.getOptional(id.id()).map(SwayAPI::isInteractive).orElse(false);
	}
	^///?}
}
*///?}
