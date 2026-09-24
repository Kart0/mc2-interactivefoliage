package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.21.11 {

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws foliage with the terrain's own shaders -- vanilla's, or the ones a resource pack puts in their place -- so it is
 * lit and coloured exactly as the chunk mesh would draw it.
 * <p>
 * The fragment shader is the terrain's, used as it is. The vertex shader cannot be: it has to move each vertex first. The
 * mod builds it from the terrain's when the pipeline is compiled: every read of {@code Position} is pointed at a swayed
 * copy, the mod's inputs and {@code sway.glsl} go in next to {@code Position}'s declaration, and a new {@code main} sways
 * the vertex and then runs the shader's own. Whatever else the shader does to a vertex -- its light, its colour, its own
 * waving -- it keeps doing.
 * <p>
 * A terrain shader that cannot be read that way, or that does not compile once it is, leaves the renderer on its own
 * shaders, which draw foliage as vanilla does.
 */
final class TerrainFoliageShader {

	/** The pipeline's vertex shader: the mod's additions, which only compile once spliced into the terrain's. */
	static final Identifier VERTEX = Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "core/foliage_terrain");
	static final Identifier TERRAIN = Identifier.withDefaultNamespace("core/terrain");

	private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
	private static final Pattern POSITION = Pattern.compile("\\bPosition\\b");
	private static final Pattern VERSION = Pattern.compile("^\\s*#version[^\\n]*\\n", Pattern.MULTILINE);
	/** Position's declaration once renamed, with the layout it may have been given. */
	private static final Pattern RENAMED_INPUT = Pattern.compile(
			"((?:layout\\s*\\([^)]*\\)\\s*)?)in\\s+vec3\\s+mc2_Position\\s*;");
	/** What the new main needs from the terrain shader's own declarations. */
	private static final String[] REQUIRED = {"ChunkPosition", "CameraBlockPos", "CameraOffset", "GameTime"};

	// The section's corner relative to the camera, as the terrain shader works it out, which SwayCell is measured from.
	private static final String SWAYED_MAIN = """

			void main() {
			    vec3 mc2_sectionOffset = vec3(ChunkPosition - CameraBlockPos) + CameraOffset;
			    mc2_Position = mc2_sway(Position + mc2_sectionOffset, mc2_sectionOffset, CameraBlockPos, CameraOffset,
			            GameTime, SwayCell, SwayWeights) - mc2_sectionOffset;
			    mc2_terrainMain();
			}
			""";

	/** Hands the compiler the spliced vertex shader, and every other shader as the game holds it. */
	private static final ShaderSource SOURCE = (id, type) -> {
		String source = Minecraft.getInstance().getShaderManager().getShader(id, type);
		if (!id.equals(VERTEX) || type != ShaderType.VERTEX) {
			return source;
		}
		String terrain = Minecraft.getInstance().getShaderManager().getShader(TERRAIN, ShaderType.VERTEX);
		String spliced = terrain == null || source == null ? null : splice(terrain, source);
		if (spliced == null) {
			ModTemplate.LOGGER.warn("The terrain vertex shader in use can't be read by the GPU foliage renderer; foliage "
					+ "near the player is drawn with the renderer's own shaders, which may not match a resource pack's look");
		}
		return spliced;
	};

	private TerrainFoliageShader() {
	}

	/**
	 * Whether the pipeline compiles from the terrain shaders in use. It is compiled here the first time it is asked about
	 * after each resource reload -- the game forgets every compiled pipeline then -- and the answer is kept alongside it,
	 * so asking every frame costs a lookup.
	 */
	static boolean usable(RenderPipeline pipeline) {
		return RenderSystem.getDevice().precompilePipeline(pipeline, SOURCE).isValid();
	}

	/** The terrain vertex shader with the mod's additions spliced in, or null if it isn't shaped the way that needs. */
	static String splice(String terrain, String additions) {
		if (!MAIN.matcher(terrain).find()) {
			return null;
		}
		for (String name : REQUIRED) {
			if (!Pattern.compile("\\b" + name + "\\b").matcher(terrain).find()) {
				return null;
			}
		}
		String renamed = POSITION.matcher(MAIN.matcher(terrain).replaceFirst("void mc2_terrainMain()"))
				.replaceAll("mc2_Position");
		Matcher input = RENAMED_INPUT.matcher(renamed);
		if (!input.find()) {
			return null;
		}
		String declarations = input.group(1) + "in vec3 Position;\nvec3 mc2_Position;\n"
				+ VERSION.matcher(additions).replaceFirst("") + "\n";
		return renamed.substring(0, input.start()) + declarations + renamed.substring(input.end()) + SWAYED_MAIN;
	}
}
//?}
