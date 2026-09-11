package net.karto.mc2.mc2_interactivefoliage.platform;

public interface Platform {
	boolean isModLoaded(String modId);

	/** Where this loader keeps mod config files. */
	java.nio.file.Path configDir();

	ModLoader loader();

	String mcVersion();

	boolean isDevelopmentEnvironment();

	default boolean isDebug() {
		return isDevelopmentEnvironment();
	}

	enum ModLoader {
		FABRIC, NEOFORGE, FORGE, QUILT
	}
}
