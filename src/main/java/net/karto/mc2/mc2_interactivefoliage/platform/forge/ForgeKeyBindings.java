package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import com.github.razorplay01.sway.config.SwayConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.karto.mc2.mc2_interactivefoliage.FoliageConfigScreen;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public class ForgeKeyBindings {

	public static final KeyMapping openConfig = new KeyMapping(
			"key.mc2_interactivefoliage.open_config",
			InputConstants.UNKNOWN.getValue(),
			"key.category.mc2_interactivefoliage.general"
	);
	public static final KeyMapping toggleMod = new KeyMapping(
			"key.mc2_interactivefoliage.toggle",
			InputConstants.UNKNOWN.getValue(),
			"key.category.mc2_interactivefoliage.general"
	);

	public static ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(ModTemplate.MOD_ID, "general");

	public static void register(Consumer<KeyMapping> registrar) {
		registrar.accept(openConfig);
		registrar.accept(toggleMod);
	}

	public static void tick(Minecraft mc) {

		while (openConfig.consumeClick()) {
			mc.setScreen(new FoliageConfigScreen(null));
		}

		while (toggleMod.consumeClick()) {

			SwayConfig.INSTANCE.enabled = !SwayConfig.INSTANCE.enabled;
			SwayConfig.save();

			if (mc.player != null) {
				mc.player.displayClientMessage(
						Component.translatable(
								SwayConfig.INSTANCE.enabled
										? "key.mc2_interactivefoliage.toggle.on"
										: "key.mc2_interactivefoliage.toggle.off"
						)
						, true
				);
			}
		}
	}
}
*///?}
