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

	private static final float DEFAULT_INTENSITY = 1.0f;
	/** Sway's own default for maxDistance in every supported version, so a fresh install already starts here. */
	private static final float DEFAULT_RADIUS = 8.0f;

	private IntensitySlider intensitySlider;
	private RadiusSlider radiusSlider;
	private Button resetIntensityBtn;
	private Button resetRadiusBtn;
	//? fabric && >=26.2 {
	private WavingIntensitySlider wavingIntensitySlider;
	private Button resetWavingIntensityBtn;
	//?}

	public FoliageConfigScreen(Screen parent) {
		super(Component.translatable("config.mc2_interactivefoliage.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 4;

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
				(config.intensity - 0.1f) / (2.0f - 0.1f)
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
		radiusSlider = new RadiusSlider(cx - 100, y, 178, 20,
				(config.maxDistance - 6.0f) / (32.0f - 6.0f)
		);
		this.addRenderableWidget(radiusSlider);

		resetRadiusBtn = Button.builder(
				Component.translatable("config.mc2_interactivefoliage.reset"),
				btn -> radiusSlider.reset()
		).bounds(cx + 82, radiusY, 18, 20).build();
		resetRadiusBtn.visible =
				Math.abs(config.maxDistance - DEFAULT_RADIUS) > 0.1f;
		this.addRenderableWidget(resetRadiusBtn);

		//? fabric && >=26.2 {
		y += 30;

		// ── Waving foliage (GPU) ───────────────────────────────────────────────
		this.addRenderableWidget(
				CycleButton.booleanBuilder(
						Component.translatable("config.mc2_interactivefoliage.on"),
						Component.translatable("config.mc2_interactivefoliage.off"),
						FoliageSettings.wavingFoliage()
				).create(cx - 100, y, 200, 20,
						Component.translatable("config.mc2_interactivefoliage.waving_foliage"),
						(btn, val) -> {
							FoliageSettings.setWavingFoliage(val);
							updateWavingIntensityVisibility();
						}
				)
		);

		y += 30;

		// ── Waving intensity (GPU), shown only while waving foliage is on ──────
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
		updateWavingIntensityVisibility();
		//?}

		y += 40;

		// ── Guardar ────────────────────────────────────────────────────────────
		this.addRenderableWidget(Button.builder(
				Component.translatable("config.mc2_interactivefoliage.save"),
				btn -> {
					SwayConfig.save();
					//? fabric && >=26.2 {
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
		//? fabric && >=26.2 {
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
				this.width / 2, this.height / 4 - 20,
				0xFFFFFF
		);
	}
	*///?}
	//? > 1.21.11 {
	@Override
	public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title,
				this.width / 2, this.height / 4 - 20,
				0xFFFFFF);
	}
	//?}


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
			config.intensity = 0.1f + (float) value * (2.0f - 0.1f);
		}

		/** Back to the default in place, without rebuilding the screen. */
		void reset() {
			config.intensity = DEFAULT_INTENSITY;
			value = (DEFAULT_INTENSITY - 0.1f) / (2.0f - 0.1f);
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
				resetRadiusBtn.visible =
						Math.abs(config.maxDistance - DEFAULT_RADIUS) > 0.1f;
			}
		}

		@Override
		protected void applyValue() {
			config.maxDistance = 6.0f + (float) value * (32.0f - 6.0f);
		}

		/** Back to the default in place, without rebuilding the screen. */
		void reset() {
			config.maxDistance = DEFAULT_RADIUS;
			value = (DEFAULT_RADIUS - 6.0f) / (32.0f - 6.0f);
			updateMessage();
		}
	}

	//? fabric && >=26.2 {
	/**
	 * The default sits in the middle of the slider: the left half runs down to the minimum and the right half
	 * up to the maximum, so weaker and stronger each get half the travel.
	 */
	private static double wavingIntensityToSlider(float intensity) {
		float min = FoliageSettings.MIN_WAVING_INTENSITY;
		float def = FoliageSettings.DEFAULT_WAVING_INTENSITY;
		float max = FoliageSettings.MAX_WAVING_INTENSITY;
		return intensity <= def
				? 0.5 * (intensity - min) / (def - min)
				: 0.5 + 0.5 * (intensity - def) / (max - def);
	}

	/**
	 * The intensity slider shows only while waving foliage is on, and its reset button only while it also sits
	 * off the default. Hidden widgets are neither drawn nor clickable, and their row is left empty.
	 */
	private void updateWavingIntensityVisibility() {
		boolean wavingOn = FoliageSettings.wavingFoliage();
		if (wavingIntensitySlider != null) {
			wavingIntensitySlider.visible = wavingOn;
		}
		if (resetWavingIntensityBtn != null) {
			resetWavingIntensityBtn.visible = wavingOn && !FoliageSettings.isDefaultWavingIntensity();
		}
	}

	/** Inverse of {@link #wavingIntensityToSlider}, snapped to steps of 0.05. */
	private static float sliderToWavingIntensity(double value) {
		float min = FoliageSettings.MIN_WAVING_INTENSITY;
		float def = FoliageSettings.DEFAULT_WAVING_INTENSITY;
		float max = FoliageSettings.MAX_WAVING_INTENSITY;
		double intensity = value <= 0.5
				? min + value / 0.5 * (def - min)
				: def + (value - 0.5) / 0.5 * (max - def);
		return Math.round(intensity * 20.0) / 20.0f;
	}

	private class WavingIntensitySlider extends AbstractSliderButton {
		public WavingIntensitySlider(int x, int y, int w, int h, double initialValue) {
			super(x, y, w, h, Component.empty(), initialValue);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(
					"config.mc2_interactivefoliage.waving_intensity",
					String.format("%.2f", FoliageSettings.wavingIntensity())
			));
			updateWavingIntensityVisibility();
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
