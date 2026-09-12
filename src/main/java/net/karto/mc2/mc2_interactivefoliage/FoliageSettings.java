package net.karto.mc2.mc2_interactivefoliage;

//? >=26.1.2 {

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

	public static final float MIN_WAVING_INTENSITY = 0.5F;
	public static final float DEFAULT_WAVING_INTENSITY = 1.0F;
	public static final float MAX_WAVING_INTENSITY = 2.0F;

	private static final Path PATH = ModTemplate.xplat().configDir().resolve(ModTemplate.MOD_ID + ".json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The file's contents. Fields missing from an older file keep the defaults set here. */
	private static final class Values {
		boolean gpuRenderer = true;
		boolean wavingFoliage = true;
		float wavingIntensity = DEFAULT_WAVING_INTENSITY;
	}

	private static Values values = load();

	private FoliageSettings() {
	}

	/**
	 * Whether the mod draws the foliage near the player itself, on the GPU. Turned off, every plant is drawn by
	 * the chunk mesh as it always was, and the mod adds nothing to it.
	 */
	public static boolean gpuRenderer() {
		return values.gpuRenderer;
	}

	public static void setGpuRenderer(boolean enabled) {
		values.gpuRenderer = enabled;
	}

	/** Whether the wind sways the foliage the GPU renderer draws. */
	public static boolean wavingFoliage() {
		return values.wavingFoliage;
	}

	public static void setWavingFoliage(boolean enabled) {
		values.wavingFoliage = enabled;
	}

	/** How strongly the wind sways foliage, as a multiplier of the shader's own strength. */
	public static float wavingIntensity() {
		return values.wavingIntensity;
	}

	public static void setWavingIntensity(float intensity) {
		values.wavingIntensity = Math.clamp(intensity, MIN_WAVING_INTENSITY, MAX_WAVING_INTENSITY);
	}

	public static boolean isDefaultWavingIntensity() {
		return Math.abs(values.wavingIntensity - DEFAULT_WAVING_INTENSITY) < 0.001F;
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
					// A hand-edited file may hold anything.
					loaded.wavingIntensity = Math.clamp(loaded.wavingIntensity, MIN_WAVING_INTENSITY, MAX_WAVING_INTENSITY);
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
