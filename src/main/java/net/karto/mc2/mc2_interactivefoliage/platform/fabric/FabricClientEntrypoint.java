package net.karto.mc2.mc2_interactivefoliage.platform.fabric;

//? fabric {

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;

@Entrypoint("client")
public class FabricClientEntrypoint implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ModTemplate.onInitializeClient();
		// Loader runs every mod's "main" entrypoint before any "client" one, so by now all blocks
		// are in the registry. Registering from the main entrypoint instead would depend on mod
		// load order and silently miss mods that initialize after this one.
		ModTemplate.onRegistriesReady();
		//? >=1.21.1 {
		FabricFoliageHooks.register();
		//?}
		FoliageKeyBindings.register();
		ClientTickEvents.END_CLIENT_TICK.register(FoliageKeyBindings::tick);
	}

}
//?}
