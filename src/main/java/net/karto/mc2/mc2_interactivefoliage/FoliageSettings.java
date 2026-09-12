package net.karto.mc2.mc2_interactivefoliage;

import com.github.razorplay01.sway.config.SwayConfig;
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

	/** How far from the player entities push plants, in blocks. Sway itself refuses anything past 32. */
	public static final float MIN_INTERACTION_RADIUS = 8.0F;
	/** A chunk's width, so the plants around the player's whole chunk respond. */
	public static final float DEFAULT_INTERACTION_RADIUS = 16.0F;
	public static final float MAX_INTERACTION_RADIUS = 32.0F;
	public static final float INTERACTION_RADIUS_STEP = 4.0F;
	/** Sway's own default for the radius, in every supported version. */
	private static final float SWAY_DEFAULT_RADIUS = 8.0F;

	private static final Path PATH = ModTemplate.xplat().configDir().resolve(ModTemplate.MOD_ID + ".json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The file's contents. Fields missing from an older file keep the defaults set here. */
	private static final class Values {
		boolean gpuRenderer = true;
		boolean wavingFoliage = true;
		float wavingIntensity = DEFAULT_WAVING_INTENSITY;
		/** Whether the mod's default radius has been offered yet; see {@link #applyDefaultRadiusOnce}. */
		boolean defaultRadiusApplied;
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
		values.wavingIntensity = clamp(intensity, MIN_WAVING_INTENSITY, MAX_WAVING_INTENSITY);
	}

	public static boolean isDefaultWavingIntensity() {
		return Math.abs(values.wavingIntensity - DEFAULT_WAVING_INTENSITY) < 0.001F;
	}

	/** The interaction radius on the slider's steps, from the minimum up to where Sway stops. */
	public static float snapInteractionRadius(float radius) {
		float steps = Math.round((radius - MIN_INTERACTION_RADIUS) / INTERACTION_RADIUS_STEP);
		return clamp(MIN_INTERACTION_RADIUS + steps * INTERACTION_RADIUS_STEP, MIN_INTERACTION_RADIUS, MAX_INTERACTION_RADIUS);
	}

	/**
	 * Gives the interaction radius the mod's default, once. The radius is Sway's setting, and Sway starts it at
	 * 8; a fresh install should start at 16. It is moved only if it still holds Sway's own default, so a radius
	 * someone already chose is left alone. Called every client tick, by which time Sway has loaded its file --
	 * doing it any earlier would be overwritten as it does.
	 */
	public static void applyDefaultRadiusOnce() {
		if (values.defaultRadiusApplied) {
			return;
		}
		values.defaultRadiusApplied = true;
		if (Math.abs(SwayConfig.INSTANCE.maxDistance - SWAY_DEFAULT_RADIUS) < 0.001F) {
			SwayConfig.INSTANCE.maxDistance = DEFAULT_INTERACTION_RADIUS;
			SwayConfig.save();
		}
		save();
	}

	/** Math.clamp would say this better, but these settings are shared with 1.20.1, built against Java 17. */
	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
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
					loaded.wavingIntensity = clamp(loaded.wavingIntensity, MIN_WAVING_INTENSITY, MAX_WAVING_INTENSITY);
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				ModTemplate.LOGGER.error("Failed to load {}, using defaults", PATH, e);
			}
		}
		return new Values();
	}
}
