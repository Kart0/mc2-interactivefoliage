package net.karto.mc2.mc2_interactivefoliage.platform.neoforge;

//? neoforge {

/*import net.karto.mc2.mc2_interactivefoliage.FoliageConfigScreen;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
//? <= 1.21.1 {
/^import net.karto.mc2.mc2_interactivefoliage.ModCompatRegistry;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
^///?}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.function.Supplier;

@Mod(ModTemplate.MOD_ID)
public class NeoforgeEntrypoint {

	public NeoforgeEntrypoint(IEventBus modEventBus, ModContainer modContainer) {
		ModTemplate.onInitialize();
		modEventBus.addListener(this::onClientSetup);
		modContainer.registerExtensionPoint(
				IConfigScreenFactory.class,
				(Supplier<IConfigScreenFactory>) () -> (client, parent) -> new FoliageConfigScreen(parent)
		);
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			ModTemplate.onInitializeClient();

			// The constructor runs during mod construction, before any mod has registered its
			// blocks, so third-party lookups have to wait. Client setup is the earliest point that
			// is both past the registry events and guaranteed client-only: the compat registry
			// touches Sway's client behaviour classes, which must not load on a dedicated server.
			ModTemplate.onRegistriesReady();

			//? <= 1.21.1 {
			/^// Sway wraps every registered block's BakedModel in its own SwayModel, which does not
			// delegate NeoForge's getRenderTypes(). Wrapped models therefore fall back to the
			// ItemBlockRenderTypes lookup, losing any "render_type" declared in the model JSON and
			// rendering foliage in the solid layer (transparent texels turn black). Populating the
			// lookup for the blocks we register makes that fallback resolve correctly.
			//
			// 1.21.11+ replaced BakedModel with BlockStateModel, where the render layer travels
			// inside each BlockStateModelPart instead of a model-level method, so Sway's wrapper
			// cannot drop it there and no workaround is needed.
			for (Block block : ModCompatRegistry.registeredBlocks()) {
				ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutoutMipped());
			}
			^///?}
		});
	}
}
*///?}
