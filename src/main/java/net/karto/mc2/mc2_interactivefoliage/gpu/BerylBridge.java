package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.20.1 {

import net.karto.mc2.mc2_interactivefoliage.ModTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Lights the foliage as Beryl lights the terrain while Beryl's shaders are on.
 * <p>
 * Beryl is a shader renderer for VulkanMod with one set of shaders of its own, which work the light, the shadows and
 * the fog out themselves. It draws the game's entities and particles through them by putting one of its Vulkan
 * pipelines under the game's render pipeline for as long as its shaders are on; the mod's pipeline is not among those,
 * so the foliage was drawn by the mod's own shader, in none of Beryl's light. The mod does for its pipeline what Beryl
 * does for the game's: it builds a Vulkan pipeline from Beryl's terrain shaders, the vertex one with the sway spliced in
 * ({@link BerylFoliageShader}), and puts it under its own while Beryl draws the terrain, and takes it away again
 * otherwise.
 * <p>
 * The pipeline is built as Beryl builds its own. The two blocks Beryl's shaders share are filled by VulkanMod from
 * values it looks up by name, as they are for the terrain; the four the sway reads are bound from what the mod hands the
 * render pass, which VulkanMod finds by the name it gives a block declared by its size alone: {@code "UBO "} and its
 * binding. The textures are found by name too, and the shadow map is the one Beryl has bound by then.
 * <p>
 * Neither mod is compiled against: everything is reached by name, on Beryl 0.2.1 and VulkanMod 0.6.8. Whatever is not
 * found, or does not compile, leaves the foliage on the mod's own shader, as it is without this class.
 */
final class BerylBridge {

	/** The name VulkanMod finds the region's transform by in what the render pass was handed. */
	static final String REGION = "UBO " + BerylFoliageShader.REGION_BINDING;
	/** The name Beryl's terrain pipeline reads the lightmap by. */
	static final String LIGHT_TEXTURE = "LightTexture";

	private static final boolean LOADED = ModTemplate.xplat().isModLoaded("beryl")
			&& ModTemplate.xplat().isModLoaded("vulkanmod");
	/** Vertex shader stage alone, as Vulkan numbers it. */
	private static final int VERTEX_STAGE = 1;

	/** The render pipelines Beryl's lit pipeline is under at the moment. */
	private static final Set<Object> REDIRECTED = Collections.newSetFromMap(new IdentityHashMap<>());
	private static Object litPipeline;
	/** Which of Beryl's options the lit pipeline was built for; they change what its shaders declare. */
	private static String builtFor;
	/** The first of the three bindings after Beryl's own, which the lit pipeline reads the sway's other blocks at. */
	private static int firstFreeBinding;
	private static boolean failed;

	private BerylBridge() {
	}

	/**
	 * Puts Beryl's lighting under the mod's render pipelines if Beryl is drawing the terrain with its shaders, and
	 * takes it away if not. Returns whether they are lit by Beryl now, so the caller hands the pass what the lit
	 * pipeline reads. Render thread only, and before the pass is opened.
	 *
	 * @param vertexFormat the format of the foliage's vertices
	 * @param swaySize the size of the block of sway settings
	 * @param interactionSize the size of the block of pushes
	 * @param pipelines the render pipelines the foliage is about to be drawn with, each already compiled
	 */
	static boolean light(Object vertexFormat, int swaySize, int interactionSize, Object... pipelines) {
		if (!LOADED || failed) {
			return false;
		}
		try {
			if (!Names.drawsTerrain()) {
				unlight();
				return false;
			}
			String options = Names.options();
			if (litPipeline == null || !options.equals(builtFor)) {
				unlight();
				if (litPipeline != null) {
					Names.SCHEDULE_CLEAN_UP.invoke(litPipeline);
					litPipeline = null;
				}
				litPipeline = Names.build(vertexFormat, swaySize, interactionSize);
				builtFor = options;
				if (litPipeline == null) {
					failed = true;
					ModTemplate.LOGGER.warn("Beryl's terrain shader is not the one the GPU foliage renderer knows how to "
							+ "follow; foliage is drawn without Beryl's light and shadows");
					return false;
				}
			}
			// All of them or none: a lit pipeline drawn without what it reads has nothing to fall back on. Beryl keeps
			// what was under a render pipeline the first time, to put it back, so that has to be there already -- which
			// it is from the first time the pipeline was drawn with.
			for (Object pipeline : pipelines) {
				if (!REDIRECTED.contains(pipeline) && Names.GET_PIPELINE.invoke(pipeline) == null) {
					unlight();
					return false;
				}
			}
			for (Object pipeline : pipelines) {
				if (REDIRECTED.add(pipeline)) {
					Names.REDIRECT.invoke(pipeline, litPipeline);
				}
			}
			return true;
		} catch (Throwable e) {
			failed = true;
			REDIRECTED.clear();
			ModTemplate.LOGGER.warn("The GPU foliage renderer can't draw through Beryl's shaders; foliage is drawn without "
					+ "Beryl's light and shadows", e);
			return false;
		}
	}

	/** The names VulkanMod finds the game's globals, the sway's settings and the pushes by, as {@link #REGION}. */
	static String globals() {
		return "UBO " + firstFreeBinding;
	}

	static String sway() {
		return "UBO " + (firstFreeBinding + 1);
	}

	static String interaction() {
		return "UBO " + (firstFreeBinding + 2);
	}

	/**
	 * Whether Beryl is turning the colours written into a buffer builder from the screen's curve into plain light, as it
	 * does for as long as it draws a frame with its shaders. Its shaders do the same to the colours of the terrain, which
	 * are not written through a buffer builder, so a plant meshed in the middle of a frame would have it done twice --
	 * and come out darker than one meshed outside it.
	 */
	static boolean straightensColours() {
		if (!LOADED || failed) {
			return false;
		}
		try {
			return (boolean) Names.SRGB_COLOURS.invoke(null);
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * A colour that Beryl's straightening brings back to the one given: each of red, green and blue raised to the
	 * power that undoes it. Alpha is left alone, as Beryl leaves it.
	 */
	static int beforeStraightening(int packed) {
		return packed & 0xFF000000
				| CURVED[packed >> 16 & 0xFF] << 16
				| CURVED[packed >> 8 & 0xFF] << 8
				| CURVED[packed & 0xFF];
	}

	private static final int[] CURVED = new int[256];

	static {
		for (int i = 0; i < CURVED.length; i++) {
			CURVED[i] = (int) Math.round(Math.pow(i / 255.0, 1.0 / 2.2) * 255.0);
		}
	}

	/** Puts back what each render pipeline had under it. */
	private static void unlight() throws ReflectiveOperationException {
		for (Object pipeline : REDIRECTED) {
			Names.RESET.invoke(pipeline);
		}
		REDIRECTED.clear();
	}

	/** Found the first time the foliage is drawn with Beryl installed, when both mods' classes are certainly loaded. */
	private static final class Names {
		private static final String VULKAN = "net.vulkanmod.vulkan.shader.";
		static final Method USING_SHADERS;
		static final Method STAGE;
		static final Method[] OPTIONS;
		static final Method SRGB_COLOURS;
		static final Method GET_PIPELINE;
		static final Method REDIRECT;
		static final Method RESET;
		static final Method SCHEDULE_CLEAN_UP;

		static {
			try {
				Class<?> rendering = Class.forName("net.beryl.render.RenderingPipeline");
				USING_SHADERS = rendering.getMethod("isUsingShaderPipeline");
				STAGE = rendering.getMethod("getRenderingStage");
				SRGB_COLOURS = rendering.getMethod("useSrgbColors");
				OPTIONS = new Method[] {rendering.getMethod("coloredShadows"), rendering.getMethod("texturedSun"),
						rendering.getMethod("wavingWater"), rendering.getMethod("SSREnabled")};
				Class<?> graphics = Class.forName(VULKAN + "GraphicsPipeline");
				GET_PIPELINE = Class.forName("net.vulkanmod.interfaces.shader.ExtendedRenderPipeline").getMethod("getPipeline");
				Class<?> redirecting = Class.forName("net.beryl.render.ShaderRenderPipeline");
				REDIRECT = redirecting.getMethod("redirectPipeline", graphics);
				RESET = redirecting.getMethod("resetPipeline");
				SCHEDULE_CLEAN_UP = Class.forName(VULKAN + "Pipeline").getMethod("scheduleCleanUp");
			} catch (ReflectiveOperationException e) {
				throw new ExceptionInInitializerError(e);
			}
		}

		/** The defines Beryl compiles its terrain shaders with, in its own order. */
		private static final String[] DEFINES = {"COLORED_SHADOWS", "TEXTURED_SUN", "WATER_WAVING", "SSR"};

		static boolean drawsTerrain() throws ReflectiveOperationException {
			return (boolean) USING_SHADERS.invoke(null) && "TERRAIN".equals(String.valueOf(STAGE.invoke(null)));
		}

		static String options() throws ReflectiveOperationException {
			StringBuilder options = new StringBuilder();
			for (Method option : OPTIONS) {
				options.append((boolean) option.invoke(null) ? '1' : '0');
			}
			return options.toString();
		}

		/** Beryl's terrain pipeline over the foliage's vertices, or null where its vertex shader is not the one known. */
		static Object build(Object vertexFormat, int swaySize, int interactionSize) throws ReflectiveOperationException {
			String options = options();
			List<String> defines = new ArrayList<>();
			for (int i = 0; i < DEFINES.length; i++) {
				if (options.charAt(i) == '1') {
					defines.add(DEFINES[i]);
				}
			}
			boolean coloredShadows = options.charAt(0) == '1';

			// The sources, read and given their defines as Beryl reads its own.
			Class<?> loader = Class.forName("net.vulkanmod.render.shader.ShaderLoadUtil");
			String shaders = (String) Class.forName("net.beryl.render.util.SUtil").getField("SHADERS_PATH").get(null);
			Object path = loader.getMethod("resolveShaderPath", String.class, String.class).invoke(null, shaders, "terrain");
			Method load = loader.getMethod("loadShader", String.class, String.class, List.class);
			String terrainVertex = (String) load.invoke(null, path, "terrain.vsh", defines);
			String fragment = (String) load.invoke(null, path, "terrain.fsh", defines);
			if (terrainVertex == null || fragment == null) {
				return null;
			}
			// Beryl's textures are at 3 to 5, and with coloured shadows two more follow them.
			int free = coloredShadows ? 8 : 6;
			String vertex = BerylFoliageShader.vertex(terrainVertex, readSway(), free);
			if (vertex == null) {
				return null;
			}

			// What the pipeline reads, as Beryl's terrain pipeline says it: its two blocks, then the sway's four by
			// their size alone, and its textures.
			Class<?> configs = Class.forName("net.beryl.render.shader.BerylPipelineConfigs");
			Class<?> block = Class.forName(VULKAN + "PipelineConfig$UB");
			Object config = Class.forName(VULKAN + "PipelineConfig").getMethod("builder").invoke(null);
			Method addBlock = config.getClass().getMethod("addUB", block);
			addBlock.invoke(config, configs.getField("COMMON_UB0").get(null));
			addBlock.invoke(config, configs.getField("COMMON_UB1").get(null));
			int[][] sized = {
					{BerylFoliageShader.REGION_BINDING, BerylFoliageShader.REGION_SIZE},
					{free, BerylFoliageShader.GLOBALS_SIZE},
					{free + 1, swaySize},
					{free + 2, interactionSize}};
			firstFreeBinding = free;
			Method blockBuilder = block.getMethod("builder", int.class, int.class);
			for (int[] one : sized) {
				Object builder = blockBuilder.invoke(null, one[0], VERTEX_STAGE);
				builder.getClass().getMethod("setSize", int.class).invoke(builder, one[1]);
				addBlock.invoke(config, builder.getClass().getMethod("build").invoke(builder));
			}
			Method addTexture = config.getClass().getMethod("addImageDescriptor", int.class, String.class, String.class,
					int.class);
			addTexture.invoke(config, 3, "sampler2D", "Sampler0", 0);
			addTexture.invoke(config, 4, "sampler2D", LIGHT_TEXTURE, 2);
			addTexture.invoke(config, 5, "sampler2D", "ShadowMap", 3);
			if (coloredShadows) {
				addTexture.invoke(config, 6, "sampler2D", "ShadowMap1", 4);
				addTexture.invoke(config, 7, "sampler2D", "ShadowColor", 5);
			}
			Object built = config.getClass().getMethod("build").invoke(config);

			Class<?> builderClass = Class.forName(VULKAN + "Pipeline$Builder");
			Object builder = null;
			for (var constructor : builderClass.getConstructors()) {
				Class<?>[] parameters = constructor.getParameterTypes();
				if (parameters.length == 2 && parameters[1] == String.class) {
					builder = constructor.newInstance(vertexFormat, "mc2_foliage_beryl");
				}
			}
			if (builder == null) {
				return null;
			}
			builderClass.getMethod("applyConfig", built.getClass()).invoke(builder, built);
			Class<?> kind = Class.forName(VULKAN + "SPIRVUtils$ShaderKind");
			Method setSource = builderClass.getMethod("setShaderSrc", kind, String.class);
			setSource.invoke(builder, enumValue(kind, "VERTEX_SHADER"), vertex);
			setSource.invoke(builder, enumValue(kind, "FRAGMENT_SHADER"), fragment);
			return builderClass.getMethod("createGraphicsPipeline").invoke(builder);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private static Object enumValue(Class<?> type, String name) {
			return Enum.valueOf((Class) type, name);
		}

		private static String readSway() {
			String path = "/assets/" + ModTemplate.MOD_ID + "/shaders/include/sway.glsl";
			try (InputStream in = BerylBridge.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IllegalStateException("Missing " + path);
				}
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IllegalStateException("Could not read " + path, e);
			}
		}
	}
}
//?}
