package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.20.1 && <1.21.11 {
/*import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL31;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/^*
 * Draws foliage with the cutout shaders the chunk mesh draws plants with -- vanilla's, or the ones a resource pack puts
 * in their place -- so foliage is lit and coloured as the terrain around it. The shader system before 1.21.11 loads a
 * program from its JSON and two sources, so the mod hands it its own: the cutout layer's JSON with the mod's uniforms
 * added, the cutout fragment shader as it is, and the cutout vertex shader with the sway spliced in, the way
 * {@link TerrainFoliageShader} does it for newer versions.
 * <p>
 * Built the first time foliage is drawn after each resource reload. A cutout shader that cannot be read that way, or
 * that does not compile once it is, leaves the renderer on its own shader.
 ^/
final class LegacyTerrainShader {

	/^* The name the program is loaded under, and its two sources, all served by the mod. ^/
	private static final String NAME = "mc2_foliage_cutout";
	private static final String CUTOUT = "rendertype_cutout";
	private static final String CORE = "shaders/core/";

	private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
	private static final Pattern POSITION = Pattern.compile("\\bPosition\\b");
	private static final Pattern RENAMED_INPUT = Pattern.compile(
			"((?:layout\\s*\\([^)]*\\)\\s*)?)in\\s+vec3\\s+mc2_Position\\s*;");

	/^* The mod's inputs and uniforms, then sway.glsl; the same the Iris copy of a pack's shader gets on this version. ^/
	private static final String ADDITIONS = """
			in float SwayCell;
			in float SwayWeights;
			uniform float mc2_SwayIntensity;
			uniform float mc2_CalmSway;
			uniform vec4 mc2_SwayEdge;
			uniform vec4 mc2_Weather;
			uniform ivec3 mc2_CameraBlockPos;
			uniform vec3 mc2_CameraOffset;
			uniform float mc2_GameTime;
			#define MC2_MAX_CELLS 128
			layout(std140) uniform FoliageInteraction {
			    int mc2_CellCount;
			    vec4 mc2_CellBoundsMin;
			    vec4 mc2_CellBoundsMax;
			    vec4 mc2_CellPosition[MC2_MAX_CELLS];
			    vec4 mc2_CellForce[MC2_MAX_CELLS];
			};
			""";

	// The region's corner relative to the camera is the chunk offset, which SwayCell is measured from.
	private static final String SWAYED_MAIN = """

			void main() {
			    mc2_Position = mc2_sway(Position + ChunkOffset, ChunkOffset, mc2_CameraBlockPos, mc2_CameraOffset,
			            mc2_GameTime, SwayCell, SwayWeights) - ChunkOffset;
			    mc2_terrainMain();
			}
			""";

	/^* The uniforms the mod sets, added to the cutout layer's own: name, type, how many values. ^/
	private static final String[][] UNIFORMS = {
			{"mc2_SwayIntensity", "float", "1"}, {"mc2_CalmSway", "float", "1"}, {"mc2_SwayEdge", "float", "4"},
			{"mc2_Weather", "float", "4"}, {"mc2_CameraBlockPos", "int", "3"}, {"mc2_CameraOffset", "float", "3"},
			{"mc2_GameTime", "float", "1"}, {"ChunkOffset", "float", "3"}};

	private static ShaderInstance shader;
	/^* Whether the shader is due to be built, as it is once after every resource reload. ^/
	private static boolean stale = true;

	private LegacyTerrainShader() {
	}

	/^* Called on every resource reload: the next draw builds the shader again from the shaders then in use. ^/
	static void invalidate() {
		stale = true;
	}

	/^* The shader to draw foliage with, or null to keep to the mod's own. Render thread only. ^/
	static ShaderInstance get(VertexFormat format, int interactionBinding) {
		if (stale) {
			stale = false;
			if (shader != null) {
				shader.close();
				shader = null;
			}
			shader = build(Minecraft.getInstance().getResourceManager(), format);
			if (shader != null) {
				int index = GL31.glGetUniformBlockIndex(shader.getId(), GpuFoliageInteraction.UNIFORM);
				if (index != GL31.GL_INVALID_INDEX) {
					GL31.glUniformBlockBinding(shader.getId(), index, interactionBinding);
				}
			}
		}
		return shader;
	}

	private static ShaderInstance build(ResourceManager resources, VertexFormat format) {
		try {
			Resource json = resources.getResourceOrThrow(vanilla(CORE + CUTOUT + ".json"));
			JsonObject cutout = JsonParser.parseString(read(json)).getAsJsonObject();
			// Which two sources the cutout layer is drawn from is the JSON's to say: a resource pack may point it at
			// shaders of its own under any name, as a pack that shades the world in the fragment shader does.
			String vertex = splice(read(resources, source(cutout, "vertex", ".vsh")), readSway());
			if (vertex == null) {
				ModTemplate.LOGGER.warn("The cutout vertex shader in use can't be read by the GPU foliage renderer; foliage "
						+ "near the player is drawn with the renderer's own shader, which may not match a resource pack's look");
				return null;
			}
			String program = programJson(cutout);
			String fragment = read(resources, source(cutout, "fragment", ".fsh"));
			ResourceProvider provider = location -> {
				if (location.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
					String path = location.getPath();
					if (path.equals(CORE + NAME + ".json")) {
						return Optional.of(served(json, program));
					}
					if (path.equals(CORE + NAME + ".vsh")) {
						return Optional.of(served(json, vertex));
					}
					if (path.equals(CORE + NAME + ".fsh")) {
						return Optional.of(served(json, fragment));
					}
				}
				return resources.getResource(location);
			};
			return new ShaderInstance(provider, NAME, format);
		} catch (Exception e) {
			ModTemplate.LOGGER.warn("The GPU foliage renderer couldn't build its shader from the cutout shaders in use; "
					+ "foliage near the player is drawn with the renderer's own shader", e);
			return null;
		}
	}

	/^* A file under the game's own shaders, by path. ^/
	private static ResourceLocation vanilla(String path) {
		//? >=1.21.1 {
		return ResourceLocation.withDefaultNamespace(path);
		//?} else {
		/^return new ResourceLocation(path);
		^///?}
	}

	private static Resource served(Resource like, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		return new Resource(like.source(), () -> new ByteArrayInputStream(bytes));
	}

	/^* Where one of a program's two sources lives: {@code namespace:name} in its JSON, or a name under the game's own. ^/
	private static ResourceLocation source(JsonObject program, String which, String extension) {
		String name = program.get(which).getAsString();
		int colon = name.indexOf(':');
		//? >=1.21.1 {
		return colon < 0 ? ResourceLocation.withDefaultNamespace(CORE + name + extension)
				: ResourceLocation.fromNamespaceAndPath(name.substring(0, colon), CORE + name.substring(colon + 1) + extension);
		//?} else {
		/^return colon < 0 ? new ResourceLocation(CORE + name + extension)
				: new ResourceLocation(name.substring(0, colon), CORE + name.substring(colon + 1) + extension);
		^///?}
	}

	private static String read(ResourceManager resources, ResourceLocation file) throws IOException {
		return read(resources.getResourceOrThrow(file));
	}

	private static String read(Resource resource) throws IOException {
		try (Reader reader = resource.openAsReader()) {
			return IOUtils.toString(reader);
		}
	}

	/^* The cutout layer's JSON, reading the mod's sources, with the uniforms the mod sets added where it lacks them. ^/
	private static String programJson(JsonObject cutout) {
		JsonObject json = cutout.deepCopy();
		json.addProperty("vertex", NAME);
		json.addProperty("fragment", NAME);
		JsonArray uniforms = json.has("uniforms") ? json.getAsJsonArray("uniforms") : new JsonArray();
		for (String[] uniform : UNIFORMS) {
			boolean present = false;
			for (JsonElement existing : uniforms) {
				present |= uniform[0].equals(existing.getAsJsonObject().get("name").getAsString());
			}
			if (present) {
				continue;
			}
			JsonObject added = new JsonObject();
			added.addProperty("name", uniform[0]);
			added.addProperty("type", uniform[1]);
			int count = Integer.parseInt(uniform[2]);
			added.addProperty("count", count);
			JsonArray values = new JsonArray();
			for (int i = 0; i < count; i++) {
				values.add(0.0);
			}
			added.add("values", values);
			uniforms.add(added);
		}
		json.add("uniforms", uniforms);
		//? <1.21.1 {
		/^// Before 1.21.1 a shader lists the attributes it is fed, in the order the vertex format holds them.
		if (json.has("attributes")) {
			JsonArray attributes = json.getAsJsonArray("attributes");
			attributes.add("Padding");
			attributes.add("SwayCell");
			attributes.add("SwayWeights");
		}
		^///?}
		return json.toString();
	}

	/^* The cutout vertex shader with the mod's additions spliced in, or null if it isn't shaped the way that needs. ^/
	static String splice(String cutout, String sway) {
		if (!MAIN.matcher(cutout).find() || !Pattern.compile("\\bChunkOffset\\b").matcher(cutout).find()) {
			return null;
		}
		String renamed = POSITION.matcher(MAIN.matcher(cutout).replaceFirst("void mc2_terrainMain()"))
				.replaceAll("mc2_Position");
		Matcher input = RENAMED_INPUT.matcher(renamed);
		if (!input.find()) {
			return null;
		}
		String declarations = input.group(1) + "in vec3 Position;\nvec3 mc2_Position;\n" + ADDITIONS + sway + "\n";
		return renamed.substring(0, input.start()) + declarations + renamed.substring(input.end()) + SWAYED_MAIN;
	}

	private static String readSway() throws IOException {
		String path = "/assets/" + ModTemplate.MOD_ID + "/shaders/include/sway.glsl";
		try (InputStream in = LegacyTerrainShader.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("Missing " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
*///?}
