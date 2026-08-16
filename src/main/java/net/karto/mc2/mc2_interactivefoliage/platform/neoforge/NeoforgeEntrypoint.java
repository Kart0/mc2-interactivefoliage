package net.karto.mc2.mc2_interactivefoliage.platform.neoforge;

//? neoforge {

/*import net.karto.mc2.mc2_interactivefoliage.FoliageConfigScreen;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.function.Supplier;

@Mod(ModTemplate.MOD_ID)
public class NeoforgeEntrypoint {

	public NeoforgeEntrypoint(IEventBus modEventBus, ModContainer modContainer) {
		ModTemplate.onInitialize();
		modContainer.registerExtensionPoint(
				IConfigScreenFactory.class,
				(Supplier<IConfigScreenFactory>) () -> (client, parent) -> new FoliageConfigScreen(parent)
		);
	}
}
*///?}
