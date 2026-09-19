package net.karto.mc2.mc2_interactivefoliage.platform;

public interface Platform {
	boolean isModLoaded(String modId);

	/** Where this loader keeps mod config files. */
	java.nio.file.Path configDir();

	ModLoader loader();

	enum ModLoader {
		FABRIC, NEOFORGE, FORGE
	}
}
