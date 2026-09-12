package net.karto.mc2.mc2_interactivefoliage;

import com.github.razorplay01.sway.config.SwayConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FoliageConfigScreen extends Screen {

	private final Screen parent;
	private final SwayConfig config = SwayConfig.INSTANCE;

	/** Sway accepts weaker than this, but below half strength the interaction is barely visible. */
	private static final float MIN_INTENSITY = 0.5f;
	private static final float DEFAULT_INTENSITY = 1.0f;
	private static final float MAX_INTENSITY = 2.0f;

	private IntensitySlider intensitySlider;
	private RadiusSlider radiusSlider;
	private Button resetIntensityBtn;
	private Button resetRadiusBtn;
	//? >=1.21.11 {
	private CycleButton<Boolean> wavingFoliageBtn;
	private WavingIntensitySlider wavingIntensitySlider;
	private Button resetWavingIntensityBtn;
	//?}

	public FoliageConfigScreen(Screen parent) {
		super(Component.translatable("config.mc2_interactivefoliage.title"));
		this.parent = parent;
	}

	/**
	 * Where the first option goes, so the whole screen sits centred whatever its height: the window's, the
	 * interface scale's, or the mod's own, since some versions have fewer options than others.
	 * <p>
	 * The title sits 20 above this, the options follow 30 apart, and the save button comes 40 after the last
	 * one. Options that are hidden keep their row, so the height never changes while the screen is open.
	 */
	private int topOfOptions() {
		int rows = 3;
		//? >=1.21.11 {
		rows += 3;
		//?}
		int height = 20 + (rows - 1) * 30 + 60;
		return Math.max((this.height - height) / 2 + 20, 20);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = topOfOptions();

		// ── Toggle ON/OFF ──────────────────────────────────────────────────────
		this.addRenderableWidget(
				CycleButton.booleanBuilder(
						Component.translatable("config.mc2_interactivefoliage.on"),
						Component.translatable("config.mc2_interactivefoliage.off")
						//? > 1.21.1 {
						,config.enabled
						 //?}
				).create(cx - 100, y, 200, 20,
						Component.translatable("config.mc2_interactivefoliage.enabled"),
						(btn, val) -> config.enabled = val
				)
		);

		y += 30;

		// ── Intensidad ─────────────────────────────────────────────────────────
		final int intensityY = y;
		intensitySlider = new IntensitySlider(cx - 100, y, 178, 20,
				toSliderPosition(config.intensity, MIN_INTENSITY, DEFAULT_INTENSITY, MAX_INTENSITY)
		);
		this.addRenderableWidget(intensitySlider);

		// Resets in place: rebuilding the screen flashes the world behind it black for a few frames.
		resetIntensityBtn = Button.builder(
				Component.translatable("config.mc2_interactivefoliage.reset"),
				btn -> intensitySlider.reset()
		).bounds(cx + 82, intensityY, 18, 20).build();
		resetIntensityBtn.visible =
				Math.abs(config.intensity - DEFAULT_INTENSITY) > 0.01f;
		this.addRenderableWidget(resetIntensityBtn);

		y += 30;

		// ── Radio de visibilidad ───────────────────────────────────────────────
		final int radiusY = y;
		radiusSlider = new RadiusSlider(cx - 100, y, 178, 20, radiusToSlider(config.maxDistance));
		this.addRenderableWidget(radiusSlider);

		resetRadiusBtn = Button.builder(
				Component.translatable("config.mc2_interactivefoliage.reset"),
				btn -> radiusSlider.reset()
		).bounds(cx + 82, radiusY, 18, 20).build();
		resetRadiusBtn.visible = !isDefaultRadius();
		this.addRenderableWidget(resetRadiusBtn);

		//? >=1.21.11 {
		y += 30;

		// ── Renderer: the chunk mesh as always, or the mod's own on the GPU ────
		this.addRenderableWidget(
				CycleButton.booleanBuilder(
						// Written out rather than translated: the two read the same in every language.
						Component.literal("GPU"),
						Component.literal("CPU"),
						FoliageSettings.gpuRenderer()
				).create(cx - 100, y, 200, 20,
						Component.translatable("config.mc2_interactivefoliage.renderer"),
						(btn, val) -> {
							FoliageSettings.setGpuRenderer(val);
							updateGpuOptions();
						}
				)
		);

		y += 30;

		// ── Wind, shown only with the GPU renderer on ──────────────────────────
		wavingFoliageBtn = CycleButton.booleanBuilder(
				Component.translatable("config.mc2_interactivefoliage.on"),
				Component.translatable("config.mc2_interactivefoliage.off"),
				FoliageSettings.wavingFoliage()
		).create(cx - 100, y, 200, 20,
				Component.translatable("config.mc2_interactivefoliage.waving_foliage"),
				(btn, val) -> {
					FoliageSettings.setWavingFoliage(val);
					updateGpuOptions();
				}
		);
		this.addRenderableWidget(wavingFoliageBtn);

		y += 30;

		// ── Wind strength, shown only while the wind is on ─────────────────────
		final int wavingIntensityY = y;
		wavingIntensitySlider = new WavingIntensitySlider(cx - 100, y, 178, 20,
				wavingIntensityToSlider(FoliageSettings.wavingIntensity())
		);
		this.addRenderableWidget(wavingIntensitySlider);

		resetWavingIntensityBtn = Button.builder(
				Component.translatable("config.mc2_interactivefoliage.reset"),
				btn -> wavingIntensitySlider.reset()
		).bounds(cx + 82, wavingIntensityY, 18, 20).build();
		this.addRenderableWidget(resetWavingIntensityBtn);
		updateGpuOptions();
		//?}

		y += 40;

		// ── Guardar ────────────────────────────────────────────────────────────
		this.addRenderableWidget(Button.builder(
				Component.translatable("config.mc2_interactivefoliage.save"),
				btn -> {
					SwayConfig.save();
					//? >=1.21.11 {
					FoliageSettings.save();
					//?}
					//? >=26.2{
					this.minecraft.setScreenAndShow(parent);
					 //?} else{
					/*this.minecraft.setScreen(parent);
					*///?}
				}
		).bounds(cx - 100, y, 200, 20).build());
	}

	@Override
	public void onClose() {
		SwayConfig.save();
		//? >=1.21.11 {
		FoliageSettings.save();
		//?}
		//? >=26.2{
		this.minecraft.setScreenAndShow(parent);
		 //?} else{
		/*this.minecraft.setScreen(parent);
		*///?}
	}

	//? <= 1.21.11 {
	/*@Override
	public void render(
			net.minecraft.client.gui.GuiGraphics graphics,
			int mouseX, int mouseY, float delta
	) {
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(
				this.font, this.title,
				this.width / 2, topOfOptions() - 20,
				0xFFFFFF
		);
	}
	*///?}
	//? > 1.21.11 {
	@Override
	public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title,
				this.width / 2, topOfOptions() - 20,
				0xFFFFFF);
	}
	//?}


	/**
	 * Where a value sits on a slider that holds its default in the middle: the left half runs down to the
	 * minimum and the right half up to the maximum, so weaker and stronger each get half the travel.
	 */
	private static double toSliderPosition(float value, float min, float def, float max) {
		double position = value <= def
				? 0.5 * (value - min) / (def - min)
				: 0.5 + 0.5 * (value - def) / (max - def);
		// A config file edited by hand may hold a value from outside the slider's range. Math.clamp would say
		// this better, but this screen is shared with 1.20.1, which is built against Java 17.
		return Math.max(0.0, Math.min(1.0, position));
	}

	/** The inverse of {@link #toSliderPosition}, snapped to steps of 0.1. */
	private static float fromSliderPosition(double position, float min, float def, float max) {
		double value = position <= 0.5
				? min + position / 0.5 * (def - min)
				: def + (position - 0.5) / 0.5 * (max - def);
		return Math.round(value * 10.0) / 10.0f;
	}

	private class IntensitySlider extends AbstractSliderButton {
		public IntensitySlider(int x, int y, int w, int h, double initialValue) {
			super(x, y, w, h, Component.empty(), initialValue);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(
					"config.mc2_interactivefoliage.intensity",
					String.format("%.1f", config.intensity)
			));
			if (resetIntensityBtn != null) {
				resetIntensityBtn.visible =
						Math.abs(config.intensity - DEFAULT_INTENSITY) > 0.01f;
			}
		}

		@Override
		protected void applyValue() {
			config.intensity = fromSliderPosition(value, MIN_INTENSITY, DEFAULT_INTENSITY, MAX_INTENSITY);
		}

		/** Back to the default in place, without rebuilding the screen. */
		void reset() {
			config.intensity = DEFAULT_INTENSITY;
			value = toSliderPosition(DEFAULT_INTENSITY, MIN_INTENSITY, DEFAULT_INTENSITY, MAX_INTENSITY);
			updateMessage();
		}
	}
	private class RadiusSlider extends AbstractSliderButton {
		public RadiusSlider(int x, int y, int w, int h, double initialValue) {
			super(x, y, w, h, Component.empty(), initialValue);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(
					"config.mc2_interactivefoliage.radius",
					String.format("%.0f", config.maxDistance)
			));
			if (resetRadiusBtn != null) {
				resetRadiusBtn.visible = !isDefaultRadius();
			}
		}

		@Override
		protected void applyValue() {
			// The handle slides freely; the radius itself moves a step at a time, as the intensities do.
			float range = FoliageSettings.MAX_INTERACTION_RADIUS - FoliageSettings.MIN_INTERACTION_RADIUS;
			config.maxDistance = FoliageSettings.snapInteractionRadius(
					FoliageSettings.MIN_INTERACTION_RADIUS + (float) value * range);
		}

		/** Back to the default in place, without rebuilding the screen. */
		void reset() {
			config.maxDistance = FoliageSettings.DEFAULT_INTERACTION_RADIUS;
			value = radiusToSlider(FoliageSettings.DEFAULT_INTERACTION_RADIUS);
			updateMessage();
		}
	}

	/** Where a radius sits along the slider, which runs straight from the minimum to the maximum. */
	private static double radiusToSlider(float radius) {
		double position = (radius - FoliageSettings.MIN_INTERACTION_RADIUS)
				/ (FoliageSettings.MAX_INTERACTION_RADIUS - FoliageSettings.MIN_INTERACTION_RADIUS);
		return Math.max(0.0, Math.min(1.0, position));
	}

	private boolean isDefaultRadius() {
		return Math.abs(config.maxDistance - FoliageSettings.DEFAULT_INTERACTION_RADIUS) < 0.1f;
	}

	//? >=1.21.11 {
	/**
	 * The default sits in the middle of the slider: the left half runs down to the minimum and the right half
	 * up to the maximum, so weaker and stronger each get half the travel.
	 */
	private static double wavingIntensityToSlider(float intensity) {
		return toSliderPosition(intensity, FoliageSettings.MIN_WAVING_INTENSITY,
				FoliageSettings.DEFAULT_WAVING_INTENSITY, FoliageSettings.MAX_WAVING_INTENSITY);
	}

	/**
	 * What the GPU renderer owns shows only while it is on: the wind, and under it the wind's strength while the
	 * wind itself is on. The strength's reset button also waits until the value sits off its default. Hidden
	 * widgets are neither drawn nor clickable, and their row is left empty.
	 */
	private void updateGpuOptions() {
		boolean gpu = FoliageSettings.gpuRenderer();
		if (wavingFoliageBtn != null) {
			wavingFoliageBtn.visible = gpu;
		}
		boolean wind = gpu && FoliageSettings.wavingFoliage();
		if (wavingIntensitySlider != null) {
			wavingIntensitySlider.visible = wind;
		}
		if (resetWavingIntensityBtn != null) {
			resetWavingIntensityBtn.visible = wind && !FoliageSettings.isDefaultWavingIntensity();
		}
	}

	private static float sliderToWavingIntensity(double value) {
		return fromSliderPosition(value, FoliageSettings.MIN_WAVING_INTENSITY,
				FoliageSettings.DEFAULT_WAVING_INTENSITY, FoliageSettings.MAX_WAVING_INTENSITY);
	}

	private class WavingIntensitySlider extends AbstractSliderButton {
		public WavingIntensitySlider(int x, int y, int w, int h, double initialValue) {
			super(x, y, w, h, Component.empty(), initialValue);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			// The arrow marks it as belonging to the row above it.
			setMessage(Component.literal("⤷ ").append(Component.translatable(
					"config.mc2_interactivefoliage.intensity",
					String.format("%.1f", FoliageSettings.wavingIntensity())
			)));
			updateGpuOptions();
		}

		@Override
		protected void applyValue() {
			FoliageSettings.setWavingIntensity(sliderToWavingIntensity(value));
		}

		/** Back to the default in place, without rebuilding the screen. */
		void reset() {
			FoliageSettings.setWavingIntensity(FoliageSettings.DEFAULT_WAVING_INTENSITY);
			value = wavingIntensityToSlider(FoliageSettings.DEFAULT_WAVING_INTENSITY);
			updateMessage();
		}
	}
	//?}
}
