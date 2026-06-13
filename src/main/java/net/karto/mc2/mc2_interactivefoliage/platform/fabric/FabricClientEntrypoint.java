package net.karto.mc2.mc2_interactivefoliage.platform.fabric;

//? fabric {

/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;

@Entrypoint("client")
public class FabricClientEntrypoint implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ModTemplate.onInitializeClient();
		FoliageKeyBindings.register();
		ClientTickEvents.END_CLIENT_TICK.register(FoliageKeyBindings::tick);
	}

}
*///?}
