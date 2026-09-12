package net.karto.mc2.mc2_interactivefoliage;

import com.github.razorplay01.sway.registry.SwayRegistry;
import net.karto.mc2.mc2_interactivefoliage.platform.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? fabric {
import net.karto.mc2.mc2_interactivefoliage.platform.fabric.FabricPlatform;
//?} neoforge {
/*import net.karto.mc2.mc2_interactivefoliage.platform.neoforge.NeoforgePlatform;
 *///?} forge {
/*import net.karto.mc2.mc2_interactivefoliage.platform.forge.ForgePlatform;
 *///?}

@SuppressWarnings("LoggingSimilarMessage")
public class ModTemplate {

	public static final String MOD_ID = /*$ mod_id*/ "mc2_interactivefoliage";
	public static final String MOD_VERSION = /*$ mod_version*/ "1.3.1";
	public static final String MOD_FRIENDLY_NAME = /*$ mod_name*/ "MC2 - Interactive Foliage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Platform PLATFORM = createPlatformInstance();

	public static void onInitialize() {
		LOGGER.info("Initializing {} on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
		SwayRegistry.initialize();
	}

	/**
	 * Registers third-party block compatibility.
	 * <p>
	 * Must run only once every block registry is fully populated. On Fabric that is already the
	 * case inside the client entrypoint, but on NeoForge/Forge the {@code @Mod} constructor runs
	 * during mod construction, before any mod has registered its blocks, so those loaders call
	 * this from their client setup event instead.
	 */
	public static void onRegistriesReady() {
		ModCompatRegistry.initialize();
	}

	public static void onInitializeClient() {
		LOGGER.info("Initializing {} Client on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	public static Platform xplat() {
		return PLATFORM;
	}

	private static Platform createPlatformInstance() {
		//? fabric {
		return new FabricPlatform();
		//?} neoforge {
		/*return new NeoforgePlatform();
		 *///?} forge {
		/*return new ForgePlatform();
		 *///?}
	}
}
