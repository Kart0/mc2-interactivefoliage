package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModTemplate.MOD_ID, value = Dist.CLIENT)
public class ForgeClientEventSubscriber {

	// Only game bus events belong here. The mod bus ones (client setup, key mapping registration)
	// are subscribed explicitly in ForgeEntrypoint, because @Mod.EventBusSubscriber defaults to
	// the game bus and would drop them without warning.

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		ForgeKeyBindings.tick(Minecraft.getInstance());
	}
}
*///?}
