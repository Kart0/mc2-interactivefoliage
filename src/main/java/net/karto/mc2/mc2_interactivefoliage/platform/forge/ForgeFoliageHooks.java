package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import com.github.razorplay01.sway.api.SwayAPI;
import com.github.razorplay01.sway.platform.forge.util.SwayModel;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/^* Where the GPU foliage renderer meets Forge: the model wrapper, the shader, the draw, and chunks coming and going. ^/
public final class ForgeFoliageHooks {

	/^*
	 * The models of the blocks Sway animates, as they were baked, by the id they were baked under. Kept only from one
	 * handler to the next within a single bake.
	 ^/
	private static final Map<ResourceLocation, BakedModel> PLAIN_MODELS = new HashMap<>();

	private ForgeFoliageHooks() {
	}

	/^* Client only. The model and shader events are the mod bus's; the draw and the chunks are the game's. ^/
	public static void register(IEventBus modBus) {
		modBus.addListener(EventPriority.HIGHEST, ForgeFoliageHooks::keepPlainModels);
		modBus.addListener(EventPriority.LOWEST, ForgeFoliageHooks::wrapModels);
		modBus.addListener(ForgeFoliageHooks::registerShaders);
		MinecraftForge.EVENT_BUS.addListener(ForgeFoliageHooks::onRenderLevelStage);
		MinecraftForge.EVENT_BUS.addListener(ForgeFoliageHooks::onChunkLoad);
		MinecraftForge.EVENT_BUS.addListener(ForgeFoliageHooks::onChunkUnload);
	}

	/^*
	 * Straight after the cutout terrain, the last of the opaque layers. Embeddium fires the same stage as it draws its
	 * own layers. The camera, the frustum and the terrain's matrices come with it.
	 ^/
	private static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
			GpuFoliageRenderer.draw(event.getCamera().getPosition(), event.getFrustum(),
					event.getPoseStack().last().pose(), event.getProjectionMatrix());
		}
	}

	private static void onChunkLoad(ChunkEvent.Load event) {
		// Posted for the client's chunks too, as a packet fills them in; only those are of interest.
		if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
			GpuFoliageRenderer.discoverChunk(level, chunk);
		}
	}

	private static void onChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
			GpuFoliageRenderer.forgetChunk(level, chunk);
		}
	}

	/^*
	 * First of all, before Sway wraps them: the plain models are what the renderer meshes from, so its geometry carries
	 * no deformation of Sway's on top of the one its shader adds.
	 ^/
	private static void keepPlainModels(ModelEvent.ModifyBakingResult event) {
		PLAIN_MODELS.clear();
		for (Map.Entry<ResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
			if (isFoliageBlockModel(entry.getKey())) {
				PLAIN_MODELS.put(entry.getKey(), entry.getValue());
			}
		}
	}

	/^* Last of all, once Sway has wrapped them: see ForgeFoliageModel for why it sits outside. ^/
	private static void wrapModels(ModelEvent.ModifyBakingResult event) {
		Map<ResourceLocation, BakedModel> models = event.getModels();
		for (ResourceLocation id : PLAIN_MODELS.keySet()) {
			BakedModel model = models.get(id);
			if (model != null) {
				models.put(id, new ForgeFoliageModel(model));
			}
		}
		// The renderer looks models up by block state, so each state is matched to the id its model has.
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!GpuFoliageSplit.mayWave(block)) {
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
		// Sway wraps the model of every block it knows, the ones it only pushes and never bends as well: Snow! Real
		// Magic's snow, whose plant inside bends on its own. Its wrapper says the model is a plain one, so the snow's
		// own drawing -- the snow and the plant in it -- is skipped and the block is drawn as nothing. Those models
		// are handed back as they were.
		for (Map.Entry<ResourceLocation, BakedModel> entry : models.entrySet()) {
			if (entry.getValue() instanceof SwayModel sway && isPushedOnlyBlockModel(entry.getKey())) {
				BakedModel inside = insideSway(sway);
				if (inside != null) {
					entry.setValue(inside);
				}
			}
		}
	}

	private static Field swayParent;
	private static boolean swayParentMissing;

	/^* The model Sway wrapped, or null if its wrapper is not one this knows. ^/
	private static BakedModel insideSway(SwayModel sway) {
		if (swayParentMissing) {
			return null;
		}
		try {
			if (swayParent == null) {
				swayParent = SwayModel.class.getDeclaredField("parent");
				swayParent.setAccessible(true);
			}
			return (BakedModel) swayParent.get(sway);
		} catch (ReflectiveOperationException | RuntimeException e) {
			swayParentMissing = true;
			ModTemplate.LOGGER.warn("Could not unwrap Sway's model of a block it only pushes; Snow! Real Magic's snow "
					+ "may not be drawn", e);
			return null;
		}
	}

	/^* A block state's model, of a block Sway pushes but does not bend. ^/
	private static boolean isPushedOnlyBlockModel(ResourceLocation id) {
		if (!(id instanceof ModelResourceLocation location) || "inventory".equals(location.getVariant())) {
			return false;
		}
		return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(location.getNamespace(), location.getPath()))
				.map(block -> SwayAPI.isInteractive(block) && !GpuFoliageSplit.bends(block)).orElse(false);
	}

	/^*
	 * The foliage shader is loaded with the game's own core shaders, and again on every resource reload. It is the copy
	 * made for 1.20.1, whose shader library measures fog through the view matrix.
	 ^/
	private static void registerShaders(RegisterShadersEvent event) {
		try {
			event.registerShader(
					new ShaderInstance(event.getResourceProvider(),
							new ResourceLocation(ModTemplate.MOD_ID, "foliage_legacy_1_20"),
							GpuFoliageRenderer.legacyVertexFormat()),
					GpuFoliageRenderer::onLegacyShaderLoaded);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not load the foliage shader", e);
		}
	}

	/^* A block state's model is baked under the block's id and the state's variant; item models are left alone. ^/
	private static boolean isFoliageBlockModel(ResourceLocation id) {
		if (!(id instanceof ModelResourceLocation location) || "inventory".equals(location.getVariant())) {
			return false;
		}
		return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(location.getNamespace(), location.getPath()))
				.map(GpuFoliageSplit::mayWave).orElse(false);
	}
}
*///?}
