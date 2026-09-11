package net.karto.mc2.mc2_interactivefoliage;

//? fabric && >=26.2 {

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mod's own settings, next to Sway's.
 * <p>
 * Sway's {@code SwayConfig} is written by serialising its own class, so anything added to its file would be
 * dropped on the next save. These live in a file of their own instead.
 */
public final class FoliageSettings {

	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(ModTemplate.MOD_ID + ".json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The file's contents. Fields missing from an older file keep the defaults set here. */
	private static final class Values {
		boolean wavingFoliage = true;
	}

	private static Values values = load();

	private FoliageSettings() {
	}

	/** Whether foliage near the player is drawn and swayed on the GPU. */
	public static boolean wavingFoliage() {
		return values.wavingFoliage;
	}

	public static void setWavingFoliage(boolean enabled) {
		values.wavingFoliage = enabled;
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(values, writer);
			}
		} catch (IOException e) {
			ModTemplate.LOGGER.error("Failed to save {}", PATH, e);
		}
	}

	private static Values load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				Values loaded = GSON.fromJson(reader, Values.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				ModTemplate.LOGGER.error("Failed to load {}, using defaults", PATH, e);
			}
		}
		return new Values();
	}
}
//?}
