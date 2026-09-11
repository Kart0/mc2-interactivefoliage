package net.karto.mc2.mc2_interactivefoliage.platform.fabric;

//? fabric {

import com.github.razorplay01.sway.config.SwayConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.karto.mc2.mc2_interactivefoliage.FoliageConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliagePrototype;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import net.minecraft.resources./*? >= 1.21.11 {*/ Identifier /*?} else {*/ /*ResourceLocation *//*?} */;
//? <=1.21.11{
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
 *///?}
//? >1.21.11{
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?}

public class FoliageKeyBindings {

	public static KeyMapping openConfig;
	public static KeyMapping toggleMod;
	//? >=26.2 {
	/** Development only: swaps foliage between the GPU renderer and the chunk mesh, to compare frame rates on the spot. */
	public static KeyMapping compareRenderer;
	//?}
	public static /*? >= 1.21.11 {*/ Identifier /*?} else {*/ /*ResourceLocation *//*?} */ resourceLocation = /*? <=1.20.1 {*//*new*//*?} */ /*? >= 1.21.11 {*/ Identifier /*?} else {*/ /*ResourceLocation *//*?} */
			/*? >1.20.1 {*/.fromNamespaceAndPath/*?} */("mc2_interactivefoliage", "general");

	public static void register() {
		//? > 1.21.1{
		KeyMapping.Category category = KeyMapping.Category.register(resourceLocation);
		//?}

		openConfig = /*? > 1.21.11 {*/ KeyMappingHelper.registerKeyMapping /*?} else {*/ /*KeyBindingHelper.registerKeyBinding *//*?} */(new KeyMapping(
				"key.mc2_interactivefoliage.open_config",
				InputConstants.UNKNOWN.getValue(),
				//? <= 1.21.1{
				/*"key.category.mc2_interactivefoliage.general"
				 *///?}
				//? > 1.21.1{
				category
				//?}
		));

		toggleMod = /*? > 1.21.11 {*/ KeyMappingHelper.registerKeyMapping /*?} else {*/ /*KeyBindingHelper.registerKeyBinding *//*?} */(new KeyMapping(
				"key.mc2_interactivefoliage.toggle",
				InputConstants.UNKNOWN.getValue(),
				//? <= 1.21.1{
				/*"key.category.mc2_interactivefoliage.general"
				 *///?}
				//? > 1.21.1{
				category
				//?}
		));

		//? >=26.2 {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			compareRenderer = KeyMappingHelper.registerKeyMapping(new KeyMapping(
					"key.mc2_interactivefoliage.compare_renderer",
					GLFW.GLFW_KEY_F7,
					category
			));
		}
		//?}
	}

	public static void tick(Minecraft mc) {
		while (openConfig.consumeClick()) {
			//? >=26.2{
			mc.setScreenAndShow(new FoliageConfigScreen(null));
			//?}else{
			/*mc.setScreen(new FoliageConfigScreen(null));
			*///?}
		}

		while (toggleMod.consumeClick()) {
			SwayConfig.INSTANCE.enabled = !SwayConfig.INSTANCE.enabled;
			SwayConfig.save();
			if (mc.player != null) {
				//? <=1.21.11{
				/*mc.player.displayClientMessage(
				 *///?}
				//? >1.21.11{
				mc.player.sendSystemMessage(
						//?}
						Component.translatable(
								SwayConfig.INSTANCE.enabled
										? "key.mc2_interactivefoliage.toggle.on"
										: "key.mc2_interactivefoliage.toggle.off"
						)
						//? <=1.21.11{
						/*,true
						 *///?}
				);
			}
		}

		//? >=26.2 {
		while (compareRenderer != null && compareRenderer.consumeClick()) {
			GpuFoliagePrototype.enabled = !GpuFoliagePrototype.enabled;
			// Every chunk is meshed again, the same as F3+A, so the foliage moves between the GPU
			// renderer and the chunk mesh and both are measured on the same view.
			mc.levelExtractor.allChanged();
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.translatable(GpuFoliagePrototype.enabled
						? "key.mc2_interactivefoliage.compare_renderer.gpu"
						: "key.mc2_interactivefoliage.compare_renderer.chunks"));
			}
		}
		//?}
	}
}
//?}
