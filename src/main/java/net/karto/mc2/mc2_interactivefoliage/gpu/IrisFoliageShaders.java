package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=26.2 {

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.programs.ShaderSupplier;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.karto.mc2.mc2_interactivefoliage.mixin.iris.IrisRenderingPipelineAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws the GPU renderer's foliage through a loaded shader pack, so plants sway and get pushed exactly as without one
 * while the pack lights, shades and shadows them.
 * <p>
 * Iris replaces the program of every pipeline it knows with one of the pack's. The renderer's own pipeline is not one
 * of them, so for each pack the mod builds two programs of its own out of the pack's sources, through the very route
 * Iris builds the pack's programs: its terrain cutout program and its shadow cutout program. Both are copies; the pack's
 * own programs are never touched. After Iris has translated the vertex shader, the position the pack reads --
 * {@code iris_Position}, whether the pack wrote {@code gl_Vertex} or {@code vaPosition} -- is swapped for the same
 * position moved by {@code sway.glsl}, the file the mod's own vertex shader moves plants with. The fragment shader is
 * left as the pack wrote it.
 * <p>
 * Everything here reaches into Iris itself, so any step that goes wrong is caught, logged once, and leaves the
 * foliage to the chunk mesh until the next pack loads; the pack itself keeps working either way.
 */
public final class IrisFoliageShaders {

	/** Iris's terrain format, which a shader pack's terrain program reads, with the renderer's two values after it. */
	public static final VertexFormat FORMAT = format();

	/** Set while a program of the mod's is being built, so the hooks inside Iris change that one and no other. */
	private static final ThreadLocal<Boolean> BUILDING = ThreadLocal.withInitial(() -> false);

	private static final Pattern POSITION_INPUT = Pattern.compile("\\bin\\s+vec3\\s+iris_Position\\s*;");
	private static final Pattern POSITION = Pattern.compile("\\biris_Position\\b");
	private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
	/** What the pack reads in place of its position: the same position, moved. */
	private static final String SWAYED_POSITION = "mc2_swayedPosition";

	private static RenderPipeline pipeline;
	private static RenderPipeline shadowPipeline;
	private static List<BindGroupLayout> layouts = List.of();

	private static IrisRenderingPipeline owner;
	private static GlProgram main;
	private static GlProgram shadow;
	/** Bumped for every pack loaded, so sections meshed for the last one are meshed again with this one's block ids. */
	private static int generation;
	private static boolean shadowCallbackRegistered;

	private IrisFoliageShaders() {
	}

	private static VertexFormat format() {
		VertexFormat.Builder builder = VertexFormat.builder(0);
		for (VertexFormatElement element : IrisVertexFormats.TERRAIN.getElements()) {
			builder.addAttribute(element.name(), element.format());
		}
		return builder
				.addAttribute("SwayCell", GpuFormat.R32_FLOAT)
				.addAttribute("SwayWeights", GpuFormat.R32_FLOAT)
				.build();
	}

	/**
	 * The renderer's pipelines for drawing through a shader pack -- in the pack's own pass and into its shadow map -- and
	 * the uniform blocks they bind beyond Iris's own: the sway settings and the plant pushes.
	 */
	static void setUp(RenderPipeline foliagePipeline, RenderPipeline foliageShadowPipeline,
			List<BindGroupLayout> foliageLayouts, IrisCompat.ShadowDraw shadowDraw) {
		pipeline = foliagePipeline;
		shadowPipeline = foliageShadowPipeline;
		layouts = List.copyOf(foliageLayouts);
		if (!shadowCallbackRegistered) {
			shadowCallbackRegistered = true;
			// Called by Iris in its shadow pass once the terrain is in the shadow map, with the sun's matrices and the camera
			// it rendered from.
			IrisApi.getInstance().registerShadowRenderCallback(
					(modelView, projection, cameraX, cameraY, cameraZ, tickDelta) -> {
						if (owner != null && shadow != null) {
							shadowDraw.draw(modelView, projection, cameraX, cameraY, cameraZ);
						}
					});
		}
	}

	static boolean shaderPackInUse() {
		return IrisApi.getInstance().isShaderPackInUse();
	}

	/**
	 * Builds the programs for the pack loaded now, once per pack, and returns whether there are programs to draw with.
	 * Called on the render thread each frame the renderer draws through a pack: a pack can load before the renderer
	 * is ever used, and building here rather than as it loads never depends on which comes first.
	 */
	static boolean ready() {
		if (pipeline == null || !(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline current)) {
			// The pack was turned off: its programs go with it.
			if (owner != null) {
				closePrograms();
				owner = null;
			}
			return false;
		}
		if (current != owner) {
			closePrograms();
			owner = current;
			generation++;
			build(current);
		}
		return main != null;
	}

	static int generation() {
		return generation;
	}

	/** The program one of the renderer's pipelines draws with, or null for any other pipeline, left to Iris. */
	public static GlProgram programFor(RenderPipeline requested) {
		if (requested == null || Iris.getPipelineManager().getPipelineNullable() != owner) {
			return null;
		}
		if (requested == pipeline) {
			return main;
		}
		return requested == shadowPipeline ? shadow : null;
	}

	static boolean hasShadowProgram() {
		return shadow != null;
	}

	/**
	 * Tells Iris which block the next vertices belong to, as it is told for the chunk mesh, so the pack sees the same
	 * block ids, light emission and block centres on foliage the renderer draws.
	 */
	static void beginBlock(BufferBuilder builder, BlockState state, int x, int y, int z) {
		Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
		if (ids != null && (Object) builder instanceof BlockSensitiveBufferBuilder blocks) {
			blocks.beginBlock(ids.getOrDefault(state, -1), (byte) 0, (byte) state.getLightEmission(), x, y, z);
		}
	}

	static void endBlock(BufferBuilder builder) {
		if ((Object) builder instanceof BlockSensitiveBufferBuilder blocks) {
			blocks.endBlock();
		}
	}

	/** Marks what the renderer draws as terrain cutout for the pack, which some packs read to tell passes apart. */
	static Object beginTerrainPhase() {
		WorldRenderingPipeline current = Iris.getPipelineManager().getPipelineNullable();
		if (current == null) {
			return null;
		}
		WorldRenderingPhase previous = current.getPhase();
		current.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
		return previous;
	}

	/** Takes what {@link #beginTerrainPhase} returned, typed loosely so callers never name Iris's classes. */
	static void endTerrainPhase(Object previous) {
		WorldRenderingPipeline current = Iris.getPipelineManager().getPipelineNullable();
		if (current != null && previous instanceof WorldRenderingPhase phase) {
			current.setPhase(phase);
		}
	}

	/** Builds the mod's two programs for a pack; on any failure leaves none, and says why once. */
	private static void build(IrisRenderingPipeline created) {
		IrisRenderingPipelineAccessor access = (IrisRenderingPipelineAccessor) created;
		BUILDING.set(true);
		try {
			Optional<ProgramSource> terrain = access.mc2$resolver().resolve(ProgramId.TerrainCutout);
			if (terrain.isEmpty()) {
				ModTemplate.LOGGER.warn("The shader pack has no terrain program; foliage near the player is left to "
						+ "the chunk mesh while it is loaded");
				return;
			}
			main = finish(access.mc2$createShader("mc2_foliage", ShaderKey.TERRAIN_CUTOUT, terrain.get(),
					ProgramId.TerrainCutout, ShaderKey.TERRAIN_CUTOUT.getAlphaTest(), FORMAT,
					ShaderKey.TERRAIN_CUTOUT.getFogMode(), false, false, false, false, false, Patch.VANILLA));
			if (access.mc2$shadowRenderTargets() != null) {
				Optional<ProgramSource> shadowSource = access.mc2$resolver().resolve(ProgramId.ShadowCutout);
				if (shadowSource.isPresent()) {
					shadow = finish(access.mc2$createShadowShader("mc2_foliage_shadow", ShaderKey.SHADOW_TERRAIN_CUTOUT,
							shadowSource.get(), ProgramId.ShadowCutout, ShaderKey.SHADOW_TERRAIN_CUTOUT.getAlphaTest(),
							FORMAT, false, false, false, false, Patch.VANILLA));
				}
			}
		} catch (Throwable e) {
			closePrograms();
			ModTemplate.LOGGER.error("Could not build the foliage programs for this shader pack; foliage near the "
					+ "player is left to the chunk mesh while it is loaded", e);
		} finally {
			BUILDING.set(false);
		}
	}

	/** Links the program Iris compiled, or fails the same way Iris would for one of the pack's own. */
	private static GlProgram finish(ShaderSupplier supplier) {
		int program = supplier.id().program();
		if (GlStateManager.glGetProgrami(program, 35714 /* GL_LINK_STATUS */) == 0) {
			throw new IllegalStateException("Linking failed: " + GlStateManager.glGetProgramInfoLog(program, 32768));
		}
		return supplier.shader().get();
	}

	private static void closePrograms() {
		if (main != null) {
			main.close();
			main = null;
		}
		if (shadow != null) {
			shadow.close();
			shadow = null;
		}
	}

	/** Whether a program of the mod's is being built on this thread. */
	public static boolean isBuilding() {
		return BUILDING.get();
	}

	/** The uniform blocks the mod's programs bind on top of Iris's. */
	public static List<BindGroupLayout> withFoliageLayouts(List<BindGroupLayout> irisLayouts) {
		List<BindGroupLayout> all = new ArrayList<>(irisLayouts);
		all.addAll(layouts);
		return all;
	}

	/**
	 * The pack's vertex shader, as Iris translated it, reading a moved position in place of its own: the position
	 * input is kept under its name, since Iris binds it by that name, and every use of it reads the moved one instead,
	 * which a {@code main} of the mod's sets before running the pack's.
	 */
	public static String patchVertex(String vertex) {
		if (count(POSITION_INPUT, vertex) != 1) {
			throw new IllegalStateException("Expected one position input in the translated vertex shader");
		}
		if (count(MAIN, vertex) != 1) {
			throw new IllegalStateException("Expected one main function in the translated vertex shader");
		}
		String patched = POSITION.matcher(vertex).replaceAll(SWAYED_POSITION);
		patched = patched.replaceFirst("\\bin\\s+vec3\\s+" + SWAYED_POSITION + "\\s*;",
				"in vec3 iris_Position;\nvec3 " + SWAYED_POSITION + ";");
		patched = MAIN.matcher(patched).replaceFirst("void mc2_packMain()");
		return patched + "\n" + SWAY_SHADER;
	}

	private static int count(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		int found = 0;
		while (matcher.find()) {
			found++;
		}
		return found;
	}

	/**
	 * What is appended to the pack's vertex shader. The blocks are named with Iris's prefix, which it adds when it
	 * looks a program's uniform blocks up, and they hold what the mod's own vertex shader reads under the same names.
	 */
	private static final String SWAY_SHADER = """
			// Added by MC2 - Interactive Foliage: the foliage it draws sways as it does without a shader pack.
			in float SwayCell;
			in float SwayWeights;
			layout(std140) uniform iris_FoliageSway {
			    float mc2_SwayIntensity;
			    vec4 mc2_SwayEdge;
			};
			#define MC2_MAX_CELLS 128
			layout(std140) uniform iris_FoliageInteraction {
			    int mc2_CellCount;
			    vec4 mc2_CellBoundsMin;
			    vec4 mc2_CellBoundsMax;
			    vec4 mc2_CellPosition[MC2_MAX_CELLS];
			    vec4 mc2_CellForce[MC2_MAX_CELLS];
			};
			""" + readSway() + """

			void main() {
			    vec3 mc2_offset = iris_transforms.ModelOffset;
			    mc2_swayedPosition = mc2_sway(iris_Position + mc2_offset, mc2_offset, iris_globalInfo.CameraBlockPos,
			            iris_globalInfo.CameraOffset, iris_globalInfo.GameTime, SwayCell, SwayWeights) - mc2_offset;
			    mc2_packMain();
			}
			""";

	private static String readSway() {
		String path = "/assets/" + ModTemplate.MOD_ID + "/shaders/include/sway.glsl";
		try (InputStream in = IrisFoliageShaders.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}
}
//?}
