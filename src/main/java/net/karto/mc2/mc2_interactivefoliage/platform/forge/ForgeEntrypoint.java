package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import net.karto.mc2.mc2_interactivefoliage.FoliageConfigScreen;
import net.karto.mc2.mc2_interactivefoliage.ModCompatRegistry;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(ModTemplate.MOD_ID)
public class ForgeEntrypoint {

	public ForgeEntrypoint() {
		ModTemplate.onInitialize();

		// These have to be subscribed here rather than through @Mod.EventBusSubscriber: that
		// annotation defaults to the game bus, while FMLClientSetupEvent and
		// RegisterKeyMappingsEvent are mod bus events and would silently never fire -- which is
		// why the key bindings were missing from the controls screen.
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		modEventBus.addListener(this::onClientSetup);
		modEventBus.addListener(this::onRegisterKeyMappings);
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeFoliageHooks.register(modEventBus));
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModLoadingContext.get().registerExtensionPoint(
				ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory(
						(client, parent) -> new FoliageConfigScreen(parent)
				)
		));
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		// Same reason as NeoForge: Sway's model wrapper drops the model's own render type, so the
		// ItemBlockRenderTypes fallback has to be populated or cutout foliage renders solid/black.
		event.enqueueWork(() -> {
			ModTemplate.onInitializeClient();

			// Past the registry events and client-only, same reasoning as NeoForge.
			ModTemplate.onRegistriesReady();

			// Only the plants Sway bends: a block it only pushes keeps its own layers -- Snow! Real Magic's snow draws
			// its snow in the solid layer and the plant inside in the plant's own, and on cutout alone it draws nothing.
			for (Block block : ModCompatRegistry.registeredBlocks()) {
				if (GpuFoliageSplit.bends(block)) {
					ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutoutMipped());
				}
			}
		});
	}

	private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		ForgeKeyBindings.register(event::register);
	}
}
*///?}
