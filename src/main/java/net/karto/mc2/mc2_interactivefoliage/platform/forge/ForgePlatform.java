package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import net.karto.mc2.mc2_interactivefoliage.platform.Platform;
import net.minecraftforge.fml.ModList;

public class ForgePlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public java.nio.file.Path configDir() {
		return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
	}

	@Override
	public ModLoader loader() {
		return ModLoader.FORGE;
	}

	/^*
	 * Every mod file's own copy, Minecraft's among them: the mods are modules here, and a file in a module is not found
	 * through the class loader unless the module opens its folder.
	 ^/
	@Override
	public java.util.List<byte[]> readAll(String path) {
		java.util.List<byte[]> found = new java.util.ArrayList<>();
		for (net.minecraftforge.forgespi.language.IModFileInfo file : ModList.get().getModFiles()) {
			try {
				java.nio.file.Path at = file.getFile().findResource(path);
				if (java.nio.file.Files.exists(at)) {
					found.add(java.nio.file.Files.readAllBytes(at));
				}
			} catch (java.io.IOException e) {
				// A file that cannot be read adds nothing.
			}
		}
		return found;
	}
}
*///?}
