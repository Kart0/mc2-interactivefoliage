package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ModTemplate.MOD_ID, value = Dist.CLIENT)
public class ForgeClientEventSubscriber {

	@SubscribeEvent
	public static void onClientSetup(final FMLClientSetupEvent event) {
		ModTemplate.onInitializeClient();
	}

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		ForgeKeyBindings.register(event::register);
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		ForgeKeyBindings.tick(Minecraft.getInstance());
	}
}
*///?}
