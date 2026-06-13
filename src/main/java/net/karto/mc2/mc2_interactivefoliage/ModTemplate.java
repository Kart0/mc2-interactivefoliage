package net.karto.mc2.mc2_interactivefoliage;

import net.karto.mc2.mc2_interactivefoliage.platform.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? fabric {
/*import net.karto.mc2.mc2_interactivefoliage.platform.fabric.FabricPlatform;
*///?} neoforge {
import net.karto.mc2.mc2_interactivefoliage.platform.neoforge.NeoforgePlatform;
 //?} forge {
/*import net.karto.mc2.mc2_interactivefoliage.platform.forge.ForgePlatform;
 *///?}

@SuppressWarnings("LoggingSimilarMessage")
public class ModTemplate {

	public static final String MOD_ID = /*$ mod_id*/ "mc2_interactivefoliage";
	public static final String MOD_VERSION = /*$ mod_version*/ "1.1.0";
	public static final String MOD_FRIENDLY_NAME = /*$ mod_name*/ "MC2 - Interactive Foliage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Platform PLATFORM = createPlatformInstance();

	public static void onInitialize() {
		LOGGER.info("Initializing {} on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
		Registry.initialize();
	}

	public static void onInitializeClient() {
		LOGGER.info("Initializing {} Client on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	static Platform xplat() {
		return PLATFORM;
	}

	private static Platform createPlatformInstance() {
		//? fabric {
		/*return new FabricPlatform();
		*///?} neoforge {
		return new NeoforgePlatform();
		 //?} forge {
		/*return new ForgePlatform();
		 *///?}
	}
}
