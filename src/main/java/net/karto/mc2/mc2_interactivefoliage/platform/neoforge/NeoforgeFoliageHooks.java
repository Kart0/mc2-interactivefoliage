package net.karto.mc2.mc2_interactivefoliage.platform.neoforge;

//? neoforge && >=26.2 {

import com.github.razorplay01.sway.api.SwayAPI;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.Map;

/**
 * Where the GPU foliage renderer meets NeoForge: the model wrapper, the draw, and chunks coming and going.
 */
@EventBusSubscriber(modid = ModTemplate.MOD_ID, value = Dist.CLIENT)
public final class NeoforgeFoliageHooks {

	private NeoforgeFoliageHooks() {
	}

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent.AfterOpaqueBlocks event) {
		GpuFoliageRenderer.draw(event.getLevelRenderState());
	}

	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load event) {
		if (event.getLevel() instanceof ClientLevel level) {
			GpuFoliageRenderer.discoverChunk(level, event.getChunk());
		}
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel() instanceof ClientLevel level) {
			GpuFoliageRenderer.forgetChunk(level, event.getChunk());
		}
	}

	/**
	 * Wraps the model of every block Sway animates, first of all: Sway wraps afterwards, so ours ends up
	 * inside its wrapper. That is what lets the renderer mesh plain geometry while the chunk mesh still gets
	 * Sway's deformation on everything our wrapper hands over.
	 */
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
}
//?}
