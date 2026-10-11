package net.karto.mc2.mc2_interactivefoliage.gpu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the vertex shader the foliage is drawn with while Beryl's shaders are on, from the one Beryl draws its terrain
 * with, so a plant is lit, shadowed and fogged exactly as the chunk mesh around it: Beryl works all of that out in its
 * own shaders, from values the mod's shader holds none of.
 * <p>
 * Beryl's terrain shader reads VulkanMod's compressed vertices, a block id beside them, and where each section is from a
 * push constant and a block of its own. The copy reads the mod's vertices instead -- Beryl's source already carries the
 * declarations for plain ones -- takes the region's corner from the transform the mod writes for it, and sways the
 * position before anything is worked out from it. Everything the shader does from there on is left as it is, so it
 * hands Beryl's own fragment shader, which is used untouched, what the terrain's hands it. The shader Beryl draws the
 * terrain into its shadow map with is written the same way, and copied the same way.
 * <p>
 * Nothing here is Beryl's text: the copy is made as the game runs, from the file in Beryl's jar, and every change has
 * to find its place exactly once. Where one does not -- another Beryl -- there is no copy, and the foliage is drawn
 * with the mod's own shader, unlit by Beryl, as it is without this.
 */
final class BerylFoliageShader {

	/**
	 * The binding of the region's transform -- the game's block of that name, read for the region's corner alone: the
	 * one Beryl's block of section offsets leaves free. VulkanMod lays a pipeline's bindings out by their number, so
	 * they have to follow one another with none left out.
	 */
	static final int REGION_BINDING = 2;
	/** The size of the game's transform block: two matrices, a colour and an offset. */
	static final int REGION_SIZE = 160;
	/** The size of the game's block of globals, as laid out below. */
	static final int GLOBALS_SIZE = 56;

	/** The material Beryl gives grass, ferns and leaves: lit from both sides rather than by its normal alone. */
	private static final int FOLIAGE_MATERIAL = 13;

	private static final String INPUTS = """
			layout(location = 5) in float SwayCell;
			layout(location = 6) in float SwayWeights;
			""";

	private static final String UNIFORMS = """

			// The mod's: where the region is, what the sway goes by, and sway.glsl.
			layout(binding = %d) uniform MC2Region {
			    mat4 mc2_RegionView;
			    vec4 mc2_RegionColor;
			    vec3 mc2_RegionOffset;
			    mat4 mc2_RegionTexture;
			};

			layout(binding = %d) uniform MC2Globals {
			    ivec3 mc2_CameraBlockPos;
			    vec3 mc2_CameraOffset;
			    vec2 mc2_ScreenSize;
			    float mc2_GlintAlpha;
			    float mc2_GameTime;
			    int mc2_MenuBlurRadius;
			    int mc2_UseRgss;
			};

			layout(binding = %d) uniform FoliageSway {
			    float mc2_SwayIntensity;
			    float mc2_CalmSway;
			    vec4 mc2_SwayEdge;
			    vec4 mc2_Weather;
			};

			#define MC2_MAX_CELLS 128
			layout(binding = %d) uniform FoliageInteraction {
			    int mc2_CellCount;
			    vec4 mc2_CellBoundsMin;
			    vec4 mc2_CellBoundsMax;
			    vec4 mc2_CellPosition[MC2_MAX_CELLS];
			    vec4 mc2_CellForce[MC2_MAX_CELLS];
			};

			""";

	private static final String POSITION = "vec4 position = vec4(mc2_sway(Position + mc2_RegionOffset, mc2_RegionOffset, "
			+ "mc2_CameraBlockPos, mc2_CameraOffset, mc2_GameTime, SwayCell, SwayWeights), 1.0);";

	private BerylFoliageShader() {
	}

	/**
	 * The copy of Beryl's terrain vertex shader the foliage is drawn with, or null where the shader is not the one
	 * this was written against. The sway's other three blocks take the three bindings from firstFreeBinding on.
	 */
	static String vertex(String terrain, String sway, int firstFreeBinding) {
		return copy(terrain, sway, firstFreeBinding, true);
	}

	/**
	 * The copy of the vertex shader Beryl draws its terrain into its shadow map with, for the foliage to be drawn into
	 * it by: the same shader read the same way, which only places the vertex as the light sees it, so a plant's shadow
	 * sways with the plant. Null where the shader is not the one this was written against.
	 */
	static String shadowVertex(String shadow, String sway, int firstFreeBinding) {
		return copy(shadow, sway, firstFreeBinding, false);
	}

	private static String copy(String terrain, String sway, int firstFreeBinding, boolean lit) {
		// The game's block of globals, read for the camera and the time the sway goes by, the sway's settings and the
		// pushes, after the last of Beryl's own bindings.
		String uniforms = UNIFORMS.formatted(REGION_BINDING, firstFreeBinding, firstFreeBinding + 1, firstFreeBinding + 2);
		String source = terrain.replace("\r\n", "\n");
		String[][] changes = {
				// Plain vertices rather than VulkanMod's compressed ones: the declarations are Beryl's own.
				{"(?m)^#define COMPRESSED_VERTEX[ \\t]*\\n", ""},
				// Where a section is comes from the mod's transform, not from Beryl's push constant and section block.
				{"(?s)layout\\s*\\(binding = 2\\) uniform UBO2 \\{.*?\\};\\n", ""},
				{"(?s)layout\\s*\\(push_constant\\) uniform pushConstant \\{.*?\\};\\n", ""},
				{"(?s)vec3 getVertexPosition\\(\\) \\{.*?\\n\\}\\n", ""},
				// The block id's place goes to the mod's two values, and every plant is foliage.
				{"(?m)^layout\\s*\\(location = 5\\) in int BlockId;\\n", Matcher.quoteReplacement(INPUTS)},
				// The shadow's shader reads no material.
				lit ? new String[] {"getMaterial\\(BlockId\\)", "getMaterial(" + FOLIAGE_MATERIAL + ")"} : null,
				// What the compressed vertex was taken apart into is there as it is.
				{"(?m)^\\s*const vec4 Color = unpackUnorm4x8\\(PackedColor\\);\\n", ""},
				{"(?m)^\\s*const uint uv = Position\\.a;\\n", ""},
				{"(?m)^\\s*const ivec2 UV2 = ivec2\\(bitfieldExtract\\(uv, 0, 8\\), bitfieldExtract\\(uv, 8, 8\\)\\);\\n", ""},
				{"texCoord0 = UV0 \\* UV_INV;", "texCoord0 = UV0;"},
				// The position, swayed before anything is worked out from it.
				{"vec4 position = vec4\\(getVertexPosition\\(\\), 1\\.0\\);", Matcher.quoteReplacement(POSITION)},
				{"(?m)^void main\\(\\) \\{", Matcher.quoteReplacement(uniforms + sway.replace("\r\n", "\n") + "\nvoid main() {")},
		};
		for (String[] change : changes) {
			if (change == null) {
				continue;
			}
			Matcher matcher = Pattern.compile(change[0]).matcher(source);
			int found = 0;
			while (matcher.find()) {
				found++;
			}
			if (found != 1) {
				// Not there, or there twice: not the shader this was written against.
				return null;
			}
			source = matcher.replaceFirst(change[1]);
		}
		return source;
	}
}
