package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.20.1 {

import com.github.razorplay01.sway.api.SwayAPI;
import com.github.razorplay01.sway.api.behavior.BehaviorPipeline;
import com.github.razorplay01.sway.api.behavior.contributors.DeformationContributor;
import com.github.razorplay01.sway.api.behavior.contributors.MultiBlockContributor;
import com.github.razorplay01.sway.client.behavior.multiblock.HangingVineMultiblockBehavior;
//? >=26.2 {
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
//?}
//? >=1.21.11 {
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
//?}
//? >=26.2 {
import com.mojang.blaze3d.pipeline.BindGroupLayout;
//?} elif >=26.1.2 {
/*import com.mojang.blaze3d.pipeline.DepthStencilState;
*///?} elif >=1.21.11 {
/*import com.mojang.blaze3d.platform.DepthTestFunction;
*///?}
//? >=1.21.11 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
//?} else {
/*import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
*///?}
import com.mojang.blaze3d.vertex.BufferBuilder;
//? <1.21.11 {
/*import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
*///?}
//? >=1.21.1 {
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
//?} else {
/*import com.google.common.collect.ImmutableMap;
*///?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.karto.mc2.mc2_interactivefoliage.FoliageSettings;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? >=1.21.11 {
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.RenderPipelines;
//?}
//? >=26.1.2 {
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
*///?}
//? >=1.21.11 && <26.1.2 {
/*import net.minecraft.client.renderer.block.model.BlockModelPart;
*///?}
//? <1.21.11 {
/*import net.minecraft.client.resources.model.BakedModel;
*///?}
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.culling.Frustum;
//? >=26.1.2 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} elif >=1.21.11 {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}
//? >=1.21.11 {
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
//? >=1.21.11 {
import net.minecraft.resources.Identifier;
//?}
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
//? >=1.21.1 {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?} else {
/*import net.minecraft.world.level.chunk.ChunkStatus;
*///?}
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
//? <26.2 {
/*import java.util.OptionalInt;
*///?}
import java.util.Set;
import java.util.function.Predicate;

/**
 * Draws the foliage near the player, the part {@link GpuFoliageSplit} took out of the chunk mesh, and
 * sways it on the GPU.
 * <p>
 * Geometry comes from {@link ModelBlockRenderer#tesselateBlock}, the very method the section compiler
 * uses, so ambient occlusion, light coordinates and biome tint are produced by vanilla and not
 * reimplemented here.
 * <p>
 * Each chunk section is meshed on its own, so a change only rebuilds the section it happened in.
 * Sections are then packed into regions of 8x4x8 for drawing -- the grouping Sodium uses -- so the GPU
 * sees a handful of buffers and draw calls instead of one of each per section. Within a region, visible
 * sections that sit next to each other in its buffer go out in a single draw call.
 */
public final class GpuFoliageRenderer {

	/** Rebuilds run on the render thread, so only a few are allowed per frame. */
	private static final int REBUILD_BUDGET = 6;
	//? <1.21.11 {
	/*/^*
	 * A region is uploaded once the sections queued for it are built, so one whose sections arrive over several frames
	 * is uploaded once rather than once a frame. If sections keep arriving for longer than this -- flying over new
	 * ground -- it is uploaded anyway, so what is built reaches the screen.
	 ^/
	private static final long REGION_UPLOAD_MAX_WAIT_MILLIS = 500L;
	*///?}
	//? <1.21.11 {
	/*/^* And only for this long a frame, after the first one, so a frame is never held up by meshing. ^/
	private static final long REBUILD_BUDGET_NANOS = 4_000_000L;
	*///?}
	/**
	 * Padding on cull boxes so foliage leaning into view is not culled away: room for the sway at its strongest
	 * plus the strongest push from an entity, on a tall plant with Sway's intensity turned up.
	 */
	private static final double SWAY_MARGIN = 2.5D;
	private static final int SECTION_SIZE = 16;
	/** The near area never shrinks below this many chunks, however short the render distance. */
	private static final int MIN_NEAR_RADIUS = 2;

	/**
	 * Up to this render distance, in chunks, the near area reaches half of it. Further than this the share
	 * shrinks, since a sway is too small to see far off while the foliage the renderer holds grows with the
	 * square of the distance.
	 */
	private static final int FULL_NEAR_SHARE_UP_TO = 8;
	private static final float FULL_NEAR_SHARE = 0.5F;
	/** How much the share shrinks for each chunk of render distance past that: 30% by 16. */
	private static final float NEAR_SHARE_DROP_PER_CHUNK = 0.025F;
	/**
	 * The smallest share, reached at 18 chunks. At 32 it leaves the near area 8 chunks out, about as far as a
	 * sway of a third of a block still shows on screen.
	 */
	private static final float MIN_NEAR_SHARE = 0.25F;
	/** Sections are built this many chunks past the near area, so ones crossing into it are ready. */
	private static final int BUILD_MARGIN = 2;

	/** A region spans 8 sections along X and Z and 4 along Y. */
	private static final int REGION_WIDTH_SHIFT = 3;
	private static final int REGION_HEIGHT_SHIFT = 2;
	private static final int REGION_WIDTH = 1 << REGION_WIDTH_SHIFT;
	private static final int REGION_HEIGHT = 1 << REGION_HEIGHT_SHIFT;
	private static final int SECTIONS_PER_REGION = REGION_WIDTH * REGION_HEIGHT * REGION_WIDTH;

	/**
	 * How far from its anchor a vertex has to be before it sways at full strength, in blocks.
	 * Anything beyond this saturates, so a tall strand keeps bending along its whole length instead
	 * of whipping at the tip.
	 */
	private static final float SWAY_SPAN = 3.0F;

	/**
	 * How much of its sway a column loses per block of length, and how far that can go: a strand of
	 * five blocks or more keeps only a fifth of its movement.
	 */
	private static final float LENGTH_DAMPING_PER_BLOCK = 0.2F;
	private static final float MAX_LENGTH_DAMPING = 0.8F;

	/**
	 * What the shader needs to move each vertex: how freely it may sway, from 0 at the anchor to 1 at the far
	 * end, and which block that anchor is, relative to the region, so every vertex of a plant looks up the
	 * same push. Either way vanilla writes the standard attributes itself, untouched.
	 */
	// They ride with the block's own attributes in a single format, interleaved as a region is uploaded.
	// A binding of their own would read better, and 26.2 allows one, but Iris rewrites the bindings of any
	// pipeline whose first one is the block format, collapsing them to the single one a shader pack wants.
	// Sharing one format keeps this pipeline unmistakably the mod's own, and matches what older versions,
	// which read one vertex buffer per pipeline, can do anyway.
	//? >=26.2 {
	private static final VertexFormat FOLIAGE_FORMAT = foliageFormat();

	private static VertexFormat foliageFormat() {
		VertexFormat.Builder builder = VertexFormat.builder(0);
		// The block's attributes first and in their own order, so they keep the offsets vanilla writes them at.
		for (VertexFormatElement element : DefaultVertexFormat.BLOCK.getElements()) {
			builder.addAttribute(element.name(), element.format());
		}
		return builder
				.addAttribute("SwayCell", GpuFormat.R32_FLOAT)
				.addAttribute("SwayWeights", GpuFormat.R32_FLOAT)
				.build();
	}
	//?} elif >=1.21.1 {
	/*//? >=26.1.2 {
	/^private static final VertexFormatElement SWAY_CELL_ELEMENT =
			VertexFormatElement.register(10, 0, VertexFormatElement.Type.FLOAT, false, 1);
	private static final VertexFormatElement SWAY_WEIGHTS_ELEMENT =
			VertexFormatElement.register(11, 0, VertexFormatElement.Type.FLOAT, false, 1);
	^///?} else {
	private static final VertexFormatElement SWAY_CELL_ELEMENT =
			VertexFormatElement.register(10, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);
	private static final VertexFormatElement SWAY_WEIGHTS_ELEMENT =
			VertexFormatElement.register(11, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);
	//?}
	private static final VertexFormat FOLIAGE_FORMAT = foliageFormat();

	private static VertexFormat foliageFormat() {
		VertexFormat block = DefaultVertexFormat.BLOCK;
		VertexFormat.Builder builder = VertexFormat.builder();
		int written = 0;
		for (VertexFormatElement element : block.getElements()) {
			builder.add(block.getElementName(element), element);
			written += element.byteSize();
		}
		// Some versions round a block's vertex up with a byte or two of padding past its last attribute:
		// 1.21.11 does, 26.1.2 does not. Rebuilding from the elements alone would drop it, and then every
		// vertex would be read at a stride it was never written at.
		if (written < block.getVertexSize()) {
			builder.padding(block.getVertexSize() - written);
		}
		return builder
				.add("SwayCell", SWAY_CELL_ELEMENT)
				.add("SwayWeights", SWAY_WEIGHTS_ELEMENT)
				.build();
	}
	*///?} else {
	/*// Before 1.21.1 a format is a map of named elements, and a generic attribute is read from the location its place
	// in the map gives it -- the block's padding counts as a place -- so the shader lists its attributes the same way.
	private static final VertexFormat FOLIAGE_FORMAT = foliageFormat();

	private static VertexFormat foliageFormat() {
		VertexFormat block = DefaultVertexFormat.BLOCK;
		ImmutableMap.Builder<String, VertexFormatElement> elements = ImmutableMap.builder();
		// The block's names and elements come in the same order, the one they are written in.
		for (int i = 0; i < block.getElements().size(); i++) {
			elements.put(block.getElementAttributeNames().get(i), block.getElements().get(i));
		}
		return new VertexFormat(elements
				.put("SwayCell", new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1))
				.put("SwayWeights", new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1))
				.build());
	}
	*///?}

	/**
	 * The {@code FoliageSway} uniform block: the settings the shader reads that the player can change while
	 * playing. They are data in a buffer rather than constants in the shader, so a change shows on the next
	 * frame without recompiling anything.
	 */
	private static final String SWAY_SETTINGS_UNIFORM = "FoliageSway";
	//? >=26.2 {
	private static final BindGroupLayout SWAY_SETTINGS = BindGroupLayout.builder()
			.withUniform(SWAY_SETTINGS_UNIFORM, UniformType.UNIFORM_BUFFER)
			.build();
	//?}
	//? >=1.21.11 {
	/** A whole vec4 for one float, so the buffer is never smaller than the block once a driver pads it. */
	private static final int SWAY_SETTINGS_SIZE = new Std140SizeCalculator().putVec4().get();
	//?}

	/**
	 * Our own pipeline, so the vertex shader can displace foliage on the GPU.
	 * <p>
	 * It inherits everything from vanilla's block snippet -- vertex format, bind groups, depth and
	 * blend state -- and only swaps in our shaders and adds the sway weight binding and the sway settings.
	 * The shader files are discovered by resource pack scanning, so no registration call is needed.
	 */
	//? >=26.2 {
	private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "pipeline/foliage"))
			.withVertexShader(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "core/foliage"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "core/foliage"))
			.withVertexBinding(0, FOLIAGE_FORMAT)
			.withBindGroupLayout(SWAY_SETTINGS)
			.withBindGroupLayout(GpuFoliageInteraction.LAYOUT)
			.withShaderDefine("ALPHA_CUTOUT", 0.5F)
			.build();
	//?} elif >=1.21.11 {
	/*// Vanilla's block snippet is private before 26.2, so the same state is spelled out here: the samplers
	// and uniforms its shaders read, one vertex format, and the depth state every block pipeline uses.
	private static final RenderPipeline PIPELINE = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "pipeline/foliage"))
			.withVertexShader(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "core/foliage"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "core/foliage"))
			.withVertexFormat(FOLIAGE_FORMAT, VertexFormat.Mode.QUADS)
			.withSampler("Sampler0")
			.withSampler("Sampler2")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withUniform("Fog", UniformType.UNIFORM_BUFFER)
			.withUniform("Globals", UniformType.UNIFORM_BUFFER)
			.withUniform(SWAY_SETTINGS_UNIFORM, UniformType.UNIFORM_BUFFER)
			.withUniform(GpuFoliageInteraction.UNIFORM, UniformType.UNIFORM_BUFFER)
			//? >=26.1.2 {
			.withDepthStencilState(DepthStencilState.DEFAULT)
			//?} else {
			/^.withDepthWrite(true)
			.withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
			^///?}
			.withShaderDefine("ALPHA_CUTOUT", 0.5F)
			.build();
	*///?}

	private static final int VERTEX_BYTES = DefaultVertexFormat.BLOCK.getVertexSize();
	/** Our own per-vertex data: the plant's anchor block and its two weights, one float each. */
	private static final int WEIGHT_BYTES = Float.BYTES * 2;

	/**
	 * A float carries whole numbers up to 2^24 exactly, which is room for three bytes. Both of the mod's
	 * per-vertex values fit in that, so each travels packed into one float and is taken apart in the shader.
	 * Eight bytes a vertex instead of twenty, which is bandwidth the GPU pays for on every vertex of every
	 * frame -- far more than the handful of instructions it costs to unpack.
	 */
	private static final float PACK_BYTE = 256.0F;
	/** Anchors may sit a little outside their region, so a coordinate is shifted before it is packed. */
	private static final int CELL_BIAS = 64;
	/** Each weight keeps twelve bits, so the two together stay inside a float's exact whole numbers. */
	private static final float WEIGHT_SCALE = 4095.0F;

	/**
	 * The anchor block, relative to the region, as one float: a byte per axis. A region is 128 blocks wide
	 * and 64 tall, and an anchor can be a few blocks outside it, which the bias leaves room for.
	 */
	private static float packCell(int x, int y, int z) {
		int cx = Mth.clamp(x + CELL_BIAS, 0, 255);
		int cy = Mth.clamp(y + CELL_BIAS, 0, 255);
		int cz = Mth.clamp(z + CELL_BIAS, 0, 255);
		return (cx * 256 + cy) * 256 + cz;
	}

	/** The wind weight and the push weight, both between 0 and 1, as one float: twelve bits each. */
	private static float packWeights(float wave, float push) {
		int w = Math.round(Mth.clamp(wave, 0.0F, 1.0F) * WEIGHT_SCALE);
		int p = Math.round(Mth.clamp(push, 0.0F, 1.0F) * WEIGHT_SCALE);
		return w * 4096 + p;
	}
	private static final int INITIAL_SCRATCH_QUADS = 1024;

	private static final Map<Long, Region> REGIONS = new HashMap<>();
	/** Sections waiting to be meshed, by section key. */
	private static final Set<Long> DIRTY = new LinkedHashSet<>();
	/** Regions whose buffers no longer match their sections. */
	private static final Set<Region> DIRTY_REGIONS = new LinkedHashSet<>();

	private static final Predicate<BlockState> FOLIAGE = GpuFoliageSplit::isFoliage;

	private static ModelBlockRenderer modelRenderer;
	/** Whether the renderer is in charge of the near foliage, following the waving foliage setting. */
	private static boolean active;
	//? <26.1.2 {
	/*/^* The frustum vanilla culls the terrain with this frame, caught on its way past. ^/
	private static Frustum cullFrustum;

	/^* Called as vanilla prepares its cull frustum, which the camera state does not carry before 26.1.2. ^/
	public static void onCullFrustum(Frustum frustum) {
		cullFrustum = frustum;
	}
	*///?}
	/** Set when every buffer was thrown away, so the chunks already loaded get queued again. */
	private static boolean reseedPending;

	//? >=1.21.11 {
	/** Holds the sway settings the shader reads; rewritten only when one of them changes. */
	private static GpuBuffer swaySettings;
	private static float uploadedIntensity = Float.NaN;
	//?}

	/** Reused by every rebuild and grown to the largest section seen, so no rebuild has a size limit. */
	//? >=1.21.1 {
	private static ByteBufferBuilder vertexScratch;
	//?} else {
	/*private static BufferBuilder vertexScratch;
	*///?}
	private static ByteBuffer weightScratch;

	/** This frame's draw calls, three ints each: index into the drawn regions, first vertex, vertex count. */
	private static int[] draws = new int[256 * 3];
	private static int drawsSize;
	/** Regions with at least one draw this frame, in the order their draws were queued. */
	private static final List<Region> DRAWN = new ArrayList<>();

	private GpuFoliageRenderer() {
	}

	/** One section's geometry, kept in memory so its region can be reassembled without re-meshing. */
	private static final class Section {
		final long key;
		final BlockPos origin;
		final AABB bounds;
		/** The block's attributes and ours, interleaved, ready to be copied into a region as it stands. */
		final ByteBuffer data;
		final int vertexCount;

		Section(long key, BlockPos origin, ByteBuffer data, int vertexCount) {
			this.key = key;
			this.origin = origin;
			this.bounds = new AABB(
					origin.getX() - SWAY_MARGIN,
					origin.getY() - SWAY_MARGIN,
					origin.getZ() - SWAY_MARGIN,
					origin.getX() + SECTION_SIZE + SWAY_MARGIN,
					origin.getY() + SECTION_SIZE + SWAY_MARGIN,
					origin.getZ() + SECTION_SIZE + SWAY_MARGIN);
			this.data = data;
			this.vertexCount = vertexCount;
		}

		void free() {
			MemoryUtil.memFree(data);
		}
	}

	/**
	 * A block of sections drawn from one pair of buffers. Its sections' vertices are relative to the
	 * region's corner, which keeps them small enough for float precision however far out it sits.
	 */
	private static final class Region {
		//? <1.21.11 {
		/*final long key;
		*///?}
		final BlockPos origin;
		final AABB bounds;
		/** The region's blocks without padding, for asking whether it lies wholly inside the frustum. */
		final BoundingBox blockBounds;
		final Section[] sections = new Section[SECTIONS_PER_REGION];
		/** Where each section starts in the region's buffers, valid once uploaded. */
		final int[] firstVertex = new int[SECTIONS_PER_REGION];
		/** The occupied slots in buffer order, so drawing never walks the empty ones. Valid once uploaded. */
		final int[] occupied = new int[SECTIONS_PER_REGION];
		int occupiedCount;
		int sectionCount;
		//? <1.21.11 {
		/*/^*
		 * The sections as they were when the region was last uploaded, which is what its buffer holds and what is drawn
		 * from. A section rebuilt since keeps its old counterpart here until the region is uploaded again.
		 ^/
		final Section[] uploadedSections = new Section[SECTIONS_PER_REGION];
		/^* When the region first had a change waiting to be uploaded. ^/
		long pendingSince;
		*///?}
		//? >=1.21.11 {
		GpuBuffer vertices;
		//?} else {
		/*VertexBuffer vertices;
		*///?}

		Region(long regionKey) {
			//? <1.21.11 {
			/*key = regionKey;
			*///?}
			origin = regionOrigin(regionKey);
			bounds = new AABB(
					origin.getX() - SWAY_MARGIN,
					origin.getY() - SWAY_MARGIN,
					origin.getZ() - SWAY_MARGIN,
					origin.getX() + REGION_WIDTH * SECTION_SIZE + SWAY_MARGIN,
					origin.getY() + REGION_HEIGHT * SECTION_SIZE + SWAY_MARGIN,
					origin.getZ() + REGION_WIDTH * SECTION_SIZE + SWAY_MARGIN);
			blockBounds = new BoundingBox(
					origin.getX(),
					origin.getY(),
					origin.getZ(),
					origin.getX() + REGION_WIDTH * SECTION_SIZE - 1,
					origin.getY() + REGION_HEIGHT * SECTION_SIZE - 1,
					origin.getZ() + REGION_WIDTH * SECTION_SIZE - 1);
		}

		void put(int slot, Section section) {
			if (sections[slot] == null) {
				sectionCount++;
			} else {
				sections[slot].free();
			}
			sections[slot] = section;
		}

		/** Returns whether there was a section in the slot to remove. */
		boolean remove(int slot) {
			Section section = sections[slot];
			if (section == null) {
				return false;
			}
			GpuFoliageSplit.unmarkGpuReady(section.key);
			section.free();
			sections[slot] = null;
			sectionCount--;
			return true;
		}

		void upload() {
			// Before 1.21.11 the buffer is uploaded over rather than deleted and made again.
			//? >=1.21.11 {
			closeBuffers();
			//?}
			int vertexCount = 0;
			occupiedCount = 0;
			for (int slot = 0; slot < SECTIONS_PER_REGION; slot++) {
				//? <1.21.11 {
				/*uploadedSections[slot] = sections[slot];
				*///?}
				if (sections[slot] != null) {
					firstVertex[slot] = vertexCount;
					vertexCount += sections[slot].vertexCount;
					occupied[occupiedCount++] = slot;
				}
			}
			// Its sections are already interleaved, so each one is a single copy. A region is rebuilt
			// whenever any of its sections changes, and there are up to sixteen of them, so doing the
			// per-vertex work here instead would repeat it for every section that did not change.
			int stride = VERTEX_BYTES + WEIGHT_BYTES;
			//? >=1.21.11 {
			// Staging memory is borrowed rather than asked for: uploads happen one after another on the
			// render thread, and a region can be hundreds of kilobytes, so this saves an allocation and a
			// free every time any section in any region changes.
			ByteBuffer vertexData = uploadScratch(vertexCount * stride);
			for (int i = 0; i < occupiedCount; i++) {
				int slot = occupied[i];
				Section section = sections[slot];
				vertexData.put(firstVertex[slot] * stride, section.data, 0, section.vertexCount * stride);
			}
			vertices = RenderSystem.getDevice().createBuffer(
					() -> "MC2 foliage vertices",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
					vertexData);
			//?} else {
			/*// Handed over as a mesh, the way vanilla uploads a chunk section, so the vertex buffer sets up its
			// attributes from the mod's format and its indices from vanilla's shared quad index buffer. The
			// staging builder is borrowed, as above, and is empty again once the mesh is closed.
			//? >=1.21.1 {
			ByteBufferBuilder staging = uploadBuilder();
			long pointer = staging.reserve(vertexCount * stride);
			for (int i = 0; i < occupiedCount; i++) {
				int slot = occupied[i];
				Section section = sections[slot];
				MemoryUtil.memCopy(MemoryUtil.memAddress(section.data, 0),
						pointer + (long) firstVertex[slot] * stride, (long) section.vertexCount * stride);
			}
			MeshData mesh = new MeshData(staging.build(), new MeshData.DrawState(FOLIAGE_FORMAT, vertexCount,
					indexCountFor(vertexCount), VertexFormat.Mode.QUADS, VertexFormat.IndexType.least(vertexCount)));
			//?} elif fabric {
			/^// Before 1.21.1 a mesh only comes out of a buffer builder, and the builder has no way to take a block of
			// vertices written elsewhere; it is opened up for this (see the access widener), and written straight into.
			BufferBuilder staging = uploadBuilder();
			staging.begin(VertexFormat.Mode.QUADS, FOLIAGE_FORMAT);
			staging.ensureCapacity(vertexCount * stride + stride);
			long pointer = MemoryUtil.memAddress(staging.buffer, staging.nextElementByte);
			for (int i = 0; i < occupiedCount; i++) {
				int slot = occupied[i];
				Section section = sections[slot];
				MemoryUtil.memCopy(MemoryUtil.memAddress(section.data, 0),
						pointer + (long) firstVertex[slot] * stride, (long) section.vertexCount * stride);
			}
			staging.nextElementByte += vertexCount * stride;
			staging.vertices = vertexCount;
			BufferBuilder.RenderedBuffer mesh = staging.end();
			^///?} else {
			/^// Before 1.21.1 a mesh only comes out of a buffer builder. Forge's takes a block of vertices whole, so each
			// section goes in as it stands, in the order the region's layout was worked out in.
			BufferBuilder staging = uploadBuilder();
			staging.begin(VertexFormat.Mode.QUADS, FOLIAGE_FORMAT);
			for (int i = 0; i < occupiedCount; i++) {
				ByteBuffer data = sections[occupied[i]].data;
				staging.putBulkData(data);
				// Read to its end by the copy; set back for the region's next upload.
				data.rewind();
			}
			BufferBuilder.RenderedBuffer mesh = staging.end();
			^///?}
			if (vertices == null) {
				vertices = new VertexBuffer(VertexBuffer.Usage.STATIC);
			}
			vertices.bind();
			// Closes the mesh once it is on the GPU.
			vertices.upload(mesh);
			VertexBuffer.unbind();
			*///?}
			markSectionsReady();
		}

		/**
		 * Every section now on the GPU may have its foliage left out of the chunk mesh. A near one that was not ready
		 * before still has it there, so its chunk mesh is asked to be built again.
		 */
		void markSectionsReady() {
			Minecraft minecraft = Minecraft.getInstance();
			for (int i = 0; i < occupiedCount; i++) {
				long key = sections[occupied[i]].key;
				if (GpuFoliageSplit.markGpuReady(key) && GpuFoliageSplit.isNear(SectionPos.x(key), SectionPos.z(key))) {
					rebuildChunkMesh(minecraft, SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
				}
			}
		}

		void closeBuffers() {
			if (vertices != null) {
				vertices.close();
				vertices = null;
			}
		}

		void free() {
			closeBuffers();
			for (int slot = 0; slot < SECTIONS_PER_REGION; slot++) {
				remove(slot);
			}
		}
	}

	/**
	 * Turns Sway's multiblock topology into a per-vertex sway weight.
	 * <p>
	 * Sway already knows where a plant is held: the bottom of a sugar cane stack, the ceiling a vine
	 * hangs from, or the block itself for ordinary foliage. Measuring each vertex against that
	 * anchor is what keeps a tall strand bending as one piece rather than every block swaying on its
	 * own, which is how it would look if the weight came from the height within a single block.
	 */
	private static final class SwayAnchor {
		/** Column length is shared by every block of a strand, so it is resolved once per anchor. */
		private final Map<BlockPos, Float> dampingByAnchor = new HashMap<>();

		private float anchorY;
		/** The anchor block, where Sway keeps the force for the whole plant. */
		private int cellX;
		private int cellY;
		private int cellZ;
		private boolean hanging;
		private float damping = 1.0F;
		/** Sway's own deformation for this plant, which decides how far a push moves each vertex. */
		private DeformationContributor deformation;
		private float deformationScale;
		private BlockState state;
		private BlockPos pos;

		void prepare(BlockState state, BlockPos pos, ClientLevel level) {
			hanging = HangingVineMultiblockBehavior.isHangingVine(state);
			BlockPos anchor = pos;
			MultiBlockContributor multiblock = null;
			BehaviorPipeline pipeline = SwayAPI.getBehaviorPipeline(state.getBlock());
			if (pipeline != null) {
				for (MultiBlockContributor contributor : pipeline.getMultiBlockContributors()) {
					if (contributor.appliesTo(state)) {
						multiblock = contributor;
						anchor = contributor.getAnchorPosition(pos, state);
						break;
					}
				}
			}
			deformation = null;
			if (pipeline != null) {
				for (DeformationContributor contributor : pipeline.getDeformationContributors()) {
					if (contributor.appliesTo(state)) {
						deformation = contributor;
						break;
					}
				}
			}
			deformationScale = deformation == null ? 0.0F : deformation.getDeformationScale(state, pos);
			this.state = state;
			this.pos = pos;
			// Hanging plants are held at the top of their anchor block, everything else at the base.
			anchorY = hanging ? anchor.getY() + 1 : anchor.getY();
			cellX = anchor.getX();
			cellY = anchor.getY();
			cellZ = anchor.getZ();
			damping = multiblock == null ? 1.0F : dampingFor(multiblock, anchor, level);
		}

		/**
		 * Long strands sway less as a whole.
		 * <p>
		 * Past the span the weight saturates, so without this every block above it moves at full
		 * strength and a tall cane or vine thrashes rather than drifts. Damping the column by its
		 * length keeps short plants lively while long ones stay heavy.
		 */
		private float dampingFor(MultiBlockContributor multiblock, BlockPos anchor, ClientLevel level) {
			Float cached = dampingByAnchor.get(anchor);
			if (cached != null) {
				return cached;
			}
			BlockState anchorState = level.getBlockState(anchor);
			int length = multiblock.getLinkedBlocks(anchor, anchorState, level).size() + 1;
			float value = 1.0F - Math.min(MAX_LENGTH_DAMPING, (length - 1) * LENGTH_DAMPING_PER_BLOCK);
			dampingByAnchor.put(anchor.immutable(), value);
			return value;
		}

		/**
		 * How far a push moves this vertex, straight from Sway: the same curve and scale the chunk mesh uses,
		 * so a plant bends the same whoever draws it. The wind's own weight is a different shape, spread over
		 * several blocks, which would leave a short plant barely pushed.
		 *
		 * @param localY the vertex's height within its own block, as Sway measures it
		 */
		float pushWeightAt(float localY) {
			return deformation == null ? 0.0F : deformation.getVertexWeight(localY, state, pos) * deformationScale;
		}

		float weightAt(float worldY) {
			float distance = hanging ? anchorY - worldY : worldY - anchorY;
			return Mth.clamp(distance / SWAY_SPAN, 0.0F, 1.0F) * damping;
		}
	}

	/** Whether this renderer is drawing the near foliage, which is while it is switched on in a world. */
	static boolean isActive() {
		return active;
	}

	/** Light arrived or changed in a section. Only sections already holding foliage care. */
	public static void onLightChanged(int sectionX, int sectionY, int sectionZ) {
		long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
		Region region = REGIONS.get(regionKeyOf(key));
		if (region != null && region.sections[slotOf(key)] != null) {
			DIRTY.add(key);
		}
	}

	/**
	 * A block changed. The sections above and below are rebuilt with its own because a column's sway
	 * depends on its length and anchor, and a column can cross a section boundary; sideways
	 * neighbours are only touched when the block sits on that edge.
	 */
	public static void onBlockChanged(BlockPos pos) {
		if (!active) {
			return;
		}
		int sectionX = SectionPos.blockToSectionCoord(pos.getX());
		int sectionY = SectionPos.blockToSectionCoord(pos.getY());
		int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
		int localX = pos.getX() & (SECTION_SIZE - 1);
		int localZ = pos.getZ() & (SECTION_SIZE - 1);
		for (int dy = -1; dy <= 1; dy++) {
			DIRTY.add(SectionPos.asLong(sectionX, sectionY + dy, sectionZ));
		}
		if (localX == 0) {
			DIRTY.add(SectionPos.asLong(sectionX - 1, sectionY, sectionZ));
		} else if (localX == SECTION_SIZE - 1) {
			DIRTY.add(SectionPos.asLong(sectionX + 1, sectionY, sectionZ));
		}
		if (localZ == 0) {
			DIRTY.add(SectionPos.asLong(sectionX, sectionY, sectionZ - 1));
		} else if (localZ == SECTION_SIZE - 1) {
			DIRTY.add(SectionPos.asLong(sectionX, sectionY, sectionZ + 1));
		}
	}

	/** Everything changed: resource packs reloaded, a video option altered, world swapped. */
	public static void discardAll() {
		REGIONS.values().forEach(Region::free);
		REGIONS.clear();
		DIRTY.clear();
		DIRTY_REGIONS.clear();
		// The renderer snapshots options such as smooth lighting, which are among the things that
		// land here, so it is rebuilt from the current options on next use.
		modelRenderer = null;
		// Nothing queues the chunks that are already loaded again: with Sodium in charge a resource
		// reload rebuilds its own meshes without passing any hook. They are rediscovered on next draw.
		reseedPending = true;
		GpuFoliageInteraction.reset();
	}

	/** Queues the sections of a chunk whose palette could hold foliage. */
	public static void discoverChunk(ClientLevel level, LevelChunk chunk) {
		if (!active) {
			return;
		}
		int chunkX = SectionPos.blockToSectionCoord(chunk.getPos().getMinBlockX());
		int chunkZ = SectionPos.blockToSectionCoord(chunk.getPos().getMinBlockZ());
		LevelChunkSection[] sections = chunk.getSections();
		for (int index = 0; index < sections.length; index++) {
			if (mayHoldFoliage(sections[index])) {
				DIRTY.add(SectionPos.asLong(chunkX, level.getSectionYFromSectionIndex(index), chunkZ));
			}
		}
	}

	public static void forgetChunk(ClientLevel level, LevelChunk chunk) {
		int chunkX = SectionPos.blockToSectionCoord(chunk.getPos().getMinBlockX());
		int chunkZ = SectionPos.blockToSectionCoord(chunk.getPos().getMinBlockZ());
		for (int sectionY = minSectionY(level); sectionY <= maxSectionY(level); sectionY++) {
			long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
			DIRTY.remove(key);
			removeSection(key);
			GpuFoliageSplit.forgetSection(key);
		}
	}

	private static void reseedLoadedChunks(Minecraft minecraft, ClientLevel level) {
		int radius = buildRadius();
		int centreX = SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
		int centreZ = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
		for (int chunkX = centreX - radius; chunkX <= centreX + radius; chunkX++) {
			for (int chunkZ = centreZ - radius; chunkZ <= centreZ + radius; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk != null) {
					discoverChunk(level, chunk);
				}
			}
		}
	}

	/**
	 * Whether a section is worth scanning block by block. The palette check is conservative -- it may
	 * answer yes for a block that has since gone, never no for one that is there -- and it skips whole
	 * sections of stone or air without looking at a single block.
	 */
	private static boolean mayHoldFoliage(LevelChunkSection section) {
		return !section.hasOnlyAir() && section.maybeHas(FOLIAGE);
	}

	private static boolean mayHoldFoliage(ClientLevel level, long key) {
		int sectionY = SectionPos.y(key);
		if (sectionY < minSectionY(level) || sectionY > maxSectionY(level)) {
			return false;
		}
		return mayHoldFoliage(level.getChunk(SectionPos.x(key), SectionPos.z(key))
				.getSection(level.getSectionIndexFromSectionY(sectionY)));
	}

	/** The lowest section of the world. */
	private static int minSectionY(ClientLevel level) {
		//? >=1.21.11 {
		return level.getMinSectionY();
		//?} else {
		/*return level.getMinSection();
		*///?}
	}

	/** The highest section of the world, itself included. */
	private static int maxSectionY(ClientLevel level) {
		//? >=1.21.11 {
		return level.getMaxSectionY();
		//?} else {
		/*// Before 1.21.11 the world's top section is given as the one past it.
		return level.getMaxSection() - 1;
		*///?}
	}

	private static long regionKeyOf(long sectionKey) {
		return SectionPos.asLong(
				SectionPos.x(sectionKey) >> REGION_WIDTH_SHIFT,
				SectionPos.y(sectionKey) >> REGION_HEIGHT_SHIFT,
				SectionPos.z(sectionKey) >> REGION_WIDTH_SHIFT);
	}

	private static BlockPos regionOrigin(long regionKey) {
		return new BlockPos(
				SectionPos.sectionToBlockCoord(SectionPos.x(regionKey) << REGION_WIDTH_SHIFT),
				SectionPos.sectionToBlockCoord(SectionPos.y(regionKey) << REGION_HEIGHT_SHIFT),
				SectionPos.sectionToBlockCoord(SectionPos.z(regionKey) << REGION_WIDTH_SHIFT));
	}

	private static int slotOf(long sectionKey) {
		int x = SectionPos.x(sectionKey) & (REGION_WIDTH - 1);
		int y = SectionPos.y(sectionKey) & (REGION_HEIGHT - 1);
		int z = SectionPos.z(sectionKey) & (REGION_WIDTH - 1);
		return (y * REGION_WIDTH + z) * REGION_WIDTH + x;
	}

	private static void putSection(long key, Section section) {
		Region region = REGIONS.computeIfAbsent(regionKeyOf(key), Region::new);
		region.put(slotOf(key), section);
		//? <1.21.11 {
		/*if (DIRTY_REGIONS.add(region)) {
			region.pendingSince = System.currentTimeMillis();
		}
		*///?} else {
		DIRTY_REGIONS.add(region);
		//?}
	}

	private static void removeSection(long key) {
		long regionKey = regionKeyOf(key);
		Region region = REGIONS.get(regionKey);
		if (region == null || !region.remove(slotOf(key))) {
			return;
		}
		if (region.sectionCount == 0) {
			region.free();
			REGIONS.remove(regionKey);
			DIRTY_REGIONS.remove(region);
		} else {
			//? <1.21.11 {
			/*if (DIRTY_REGIONS.add(region)) {
				region.pendingSince = System.currentTimeMillis();
			}
			*///?} else {
			DIRTY_REGIONS.add(region);
			//?}
		}
	}

	//? >=1.21.11 {
	/** Called by each loader's hooks once the opaque terrain is drawn. */
	public static void draw(LevelRenderState levelState) {
		// The render state carries the camera and the cull frustum vanilla already prepared this
		// frame, which is available whether or not Sodium owns the terrain renderer.
		var cameraState = levelState.cameraRenderState;
		boolean hasCamera = cameraState != null && cameraState.pos != null;
		//? >=26.1.2 {
		Frustum frustum = hasCamera ? cameraState.cullFrustum : null;
		//?} else {
		/*// Before 26.1.2 the camera state does not carry it, so it is caught as vanilla prepares it.
		Frustum frustum = cullFrustum;
		*///?}
		drawFrame(hasCamera ? cameraState.pos : null, frustum);
	}
	//?} else {
	/*/^*
	 * Called by each loader's hooks once the opaque terrain is drawn. Before 1.21.11 there is no render state
	 * to read the camera, the cull frustum and the terrain's matrices from, so the hook hands them over.
	 ^/
	public static void draw(Vec3 camera, Frustum frustum, Matrix4f modelView, Matrix4f projection) {
		legacyModelView = modelView;
		legacyProjection = projection;
		drawFrame(camera, frustum);
	}
	*///?}

	private static void drawFrame(Vec3 camera, Frustum frustum) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		// Decisions keep being applied while switched off, so the record is accurate when switched back on.
		GpuFoliageSplit.applyMeshDecisions();
		// Where the chunk mesher cannot be told to leave foliage out, the renderer stays out of the way too, or every
		// plant it drew would be drawn twice.
		if (!FoliageSettings.gpuRenderer() || !SodiumBridge.canSplitChunkMeshes()) {
			if (active) {
				deactivate(minecraft);
			}
			return;
		}
		if (!active) {
			// Chunks that loaded while switched off were never queued.
			active = true;
			reseedPending = true;
		}

		if (camera == null) {
			return;
		}
		updateNearArea(minecraft);
		// Near sections whose build is on screen still holding their foliage are asked for again; see
		// GpuFoliageSplit.NEEDS_REBUILD for why only once the build is in place.
		GpuFoliageSplit.forEachRebuildDue(key ->
				rebuildChunkMesh(minecraft, SectionPos.x(key), SectionPos.y(key), SectionPos.z(key)));
		if (reseedPending && minecraft.player != null) {
			reseedPending = false;
			reseedLoadedChunks(minecraft, minecraft.level);
		}
		buildNearest(minecraft, camera);
		// Every change is folded into its region before anything is drawn, so a region's buffers and
		// the layout used to draw from them always agree.
		//? <1.21.11 {
		/*uploadDueRegion();
		*///?} else {
		DIRTY_REGIONS.forEach(Region::upload);
		DIRTY_REGIONS.clear();
		//?}

		//? <26.1.2 {
		/*if (frustum == null) {
			return;
		}
		*///?}
		Object sodium = SodiumBridge.renderer();
		List<Region> drawn = DRAWN;
		drawn.clear();
		drawsSize = 0;
		var regions = REGIONS.entrySet().iterator();
		while (regions.hasNext()) {
			var entry = regions.next();
			Region region = entry.getValue();
			if (!regionWithinRange(minecraft, entry.getKey())) {
				region.free();
				regions.remove();
				//? <1.21.11 {
				/*DIRTY_REGIONS.remove(region);
				*///?}
				continue;
			}
			if (frustum != null && !frustum.isVisible(region.bounds)) {
				continue;
			}
			// A region wholly inside the frustum needs no per-section frustum test. Its unpadded box is
			// used, so a section whose padding pokes outside is simply drawn -- never wrongly dropped.
			//? >=1.21.11 {
			Frustum sectionFrustum = frustum != null
					&& frustum.cubeInFrustum(region.blockBounds) == FrustumIntersection.INSIDE ? null : frustum;
			//?} else {
			/*// Vanilla's frustum can only be asked about boxes before 1.21.11, so every section is tested.
			Frustum sectionFrustum = frustum;
			*///?}

			// Walk the sections in buffer order, extending a run while they stay visible, so a region
			// that is fully in view costs one draw call however many sections it holds.
			int regionIndex = drawn.size();
			int drawsBefore = drawsSize;
			int runStart = -1;
			int runEnd = -1;
			for (int i = 0; i < region.occupiedCount; i++) {
				int slot = region.occupied[i];
				//? <1.21.11 {
				/*// Drawn as uploaded: a section rebuilt since is not in the buffer yet.
				Section section = region.uploadedSections[slot];
				*///?} else {
				Section section = region.sections[slot];
				//?}
				if (!isVisible(section, sectionFrustum, sodium)) {
					if (runStart >= 0) {
						addDraw(regionIndex, runStart, runEnd - runStart);
						runStart = -1;
					}
					continue;
				}
				if (runStart < 0) {
					runStart = region.firstVertex[slot];
				}
				runEnd = region.firstVertex[slot] + section.vertexCount;
			}
			if (runStart >= 0) {
				addDraw(regionIndex, runStart, runEnd - runStart);
			}
			if (drawsSize > drawsBefore) {
				drawn.add(region);
			}
		}

		if (drawn.isEmpty()) {
			return;
		}

		//? >=1.21.11 {
		// Every region's offset is written in one mapping of the uniform ring buffer. The singular
		// writeTransform maps and unmaps it per call, which costs a GPU round trip for each one.
		//? >=26.2 {
		Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
		//?} else {
		/*Matrix4f modelView = RenderSystem.getModelViewMatrix();
		*///?}
		Vector4f noModulation = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
		Matrix4f noTextureTransform = new Matrix4f();
		DynamicUniforms.Transform[] transforms = new DynamicUniforms.Transform[drawn.size()];
		for (int i = 0; i < transforms.length; i++) {
			BlockPos origin = drawn.get(i).origin;
			transforms[i] = new DynamicUniforms.Transform(
					modelView,
					noModulation,
					new Vector3f(
							(float) (origin.getX() - camera.x),
							(float) (origin.getY() - camera.y),
							(float) (origin.getZ() - camera.z)),
					noTextureTransform);
		}
		GpuBufferSlice[] offsets = RenderSystem.getDynamicUniforms().writeTransforms(transforms);

		int longestDraw = 0;
		for (int i = 2; i < drawsSize; i += 3) {
			longestDraw = Math.max(longestDraw, draws[i]);
		}
		AbstractTexture atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
		//? >=26.2 {
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		//?} else {
		/*RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		*///?}
		GpuBuffer indexBuffer = indices.getBuffer(indexCountFor(longestDraw));
		//? >=26.2 {
		RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
		//?} else {
		/*RenderTarget target = minecraft.getMainRenderTarget();
		*///?}
		// Written before the pass opens: a buffer cannot be written to while a render pass is open.
		GpuBuffer settings = swaySettings();
		GpuBuffer interaction = GpuFoliageInteraction.upload(camera);

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
						() -> "MC2 foliage",
						target.getColorTextureView(),
						//? >=26.2 {
						Optional.empty(),
						//?} else {
						/*OptionalInt.empty(),
						*///?}
						target.getDepthTextureView(),
						OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
			pass.bindTexture("Sampler2",
					//? >=26.1.2 {
					minecraft.gameRenderer.lightmap(),
					//?} else {
					/*minecraft.gameRenderer.lightTexture().getTextureView(),
					*///?}
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.setUniform(SWAY_SETTINGS_UNIFORM, settings);
			pass.setUniform(GpuFoliageInteraction.UNIFORM, interaction);

			int boundRegion = -1;
			for (int i = 0; i < drawsSize; i += 3) {
				int regionIndex = draws[i];
				if (regionIndex != boundRegion) {
					Region region = drawn.get(regionIndex);
					pass.setUniform("DynamicTransforms", offsets[regionIndex]);
					//? >=26.2 {
					pass.setVertexBuffer(0, region.vertices.slice());
					//?} else {
					/*pass.setVertexBuffer(0, region.vertices);
					*///?}
					boundRegion = regionIndex;
				}
				// The sequential index buffer counts from zero, and the base vertex moves it to where
				// this run of sections starts in the region's buffers.
				//? >=26.2 {
				pass.drawIndexed(indexCountFor(draws[i + 2]), 1, 0, draws[i + 1], 0);
				//?} else {
				/*pass.drawIndexed(draws[i + 1], 0, indexCountFor(draws[i + 2]), 1);
				*///?}
			}
		}
		//?} else {
		/*drawLegacy(minecraft, camera, drawn);
		*///?}
	}

	//? <1.21.11 {
	/*/^* Where the foliage shader reads the plant pushes from. Nothing in vanilla binds uniform buffers here. ^/
	private static final int INTERACTION_BINDING = 12;

	/^* The foliage shader, as the game last loaded it, or null before it has been. ^/
	private static ShaderInstance legacyShader;
	private static Matrix4f legacyModelView;
	private static Matrix4f legacyProjection;

	/^* The vertex format the foliage shader is loaded for: the block's, with the mod's own two values. ^/
	public static VertexFormat legacyVertexFormat() {
		return FOLIAGE_FORMAT;
	}

	/^*
	 * Called whenever the game has loaded the foliage shader, which it does again on every resource reload. The
	 * push data's uniform block is tied to its binding here, once per program, as the shader's JSON has no way
	 * to say it.
	 ^/
	public static void onLegacyShaderLoaded(ShaderInstance shader) {
		legacyShader = shader;
		int index = GL31.glGetUniformBlockIndex(shader.getId(), GpuFoliageInteraction.UNIFORM);
		if (index != GL31.GL_INVALID_INDEX) {
			GL31.glUniformBlockBinding(shader.getId(), index, INTERACTION_BINDING);
		}
	}

	/^*
	 * Draws the queued runs the way vanilla draws a chunk layer before 1.21.11: the cutout layer's render state,
	 * the shader's default uniforms, then one offset and one vertex buffer per region. Only the draw call differs:
	 * a vertex buffer can only draw the whole of itself, and a region is drawn in runs of visible sections, so each
	 * run is drawn from where it starts in the buffer.
	 ^/
	private static void drawLegacy(Minecraft minecraft, Vec3 camera, List<Region> drawn) {
		ShaderInstance shader = legacyShader;
		if (shader == null || legacyModelView == null || legacyProjection == null) {
			return;
		}
		GpuFoliageInteraction.uploadLegacy(camera, INTERACTION_BINDING);

		RenderType renderType = RenderType.cutout();
		renderType.setupRenderState();
		RenderSystem.setShader(() -> shader);
		//? >=1.21.1 {
		shader.setDefaultUniforms(VertexFormat.Mode.QUADS, legacyModelView, legacyProjection, minecraft.getWindow());
		//?} else {
		/^// Before 1.21.1 nothing sets the uniforms every shader shares in one call: they are set the way the terrain's
		// own layers set them.
		for (int sampler = 0; sampler < 12; sampler++) {
			shader.setSampler("Sampler" + sampler, RenderSystem.getShaderTexture(sampler));
		}
		if (shader.MODEL_VIEW_MATRIX != null) {
			shader.MODEL_VIEW_MATRIX.set(legacyModelView);
		}
		if (shader.PROJECTION_MATRIX != null) {
			shader.PROJECTION_MATRIX.set(legacyProjection);
		}
		if (shader.COLOR_MODULATOR != null) {
			shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
		}
		if (shader.FOG_START != null) {
			shader.FOG_START.set(RenderSystem.getShaderFogStart());
		}
		if (shader.FOG_END != null) {
			shader.FOG_END.set(RenderSystem.getShaderFogEnd());
		}
		if (shader.FOG_COLOR != null) {
			shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
		}
		if (shader.FOG_SHAPE != null) {
			shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
		}
		if (shader.GAME_TIME != null) {
			shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
		}
		RenderSystem.setupShaderLights(shader);
		^///?}
		shader.safeGetUniform("SwayIntensity").set(
				FoliageSettings.wavingFoliage() ? FoliageSettings.wavingIntensity() : 0.0F);
		BlockPos cameraBlock = BlockPos.containing(camera);
		shader.safeGetUniform("CameraBlockPos").set(cameraBlock.getX(), cameraBlock.getY(), cameraBlock.getZ());
		shader.safeGetUniform("CameraOffset").set(
				(float) (cameraBlock.getX() - camera.x),
				(float) (cameraBlock.getY() - camera.y),
				(float) (cameraBlock.getZ() - camera.z));
		shader.apply();

		Uniform regionOffset = shader.CHUNK_OFFSET;
		int boundRegion = -1;
		int indexType = 0;
		for (int i = 0; i < drawsSize; i += 3) {
			int regionIndex = draws[i];
			if (regionIndex != boundRegion) {
				Region region = drawn.get(regionIndex);
				if (regionOffset != null) {
					regionOffset.set(
							(float) (region.origin.getX() - camera.x),
							(float) (region.origin.getY() - camera.y),
							(float) (region.origin.getZ() - camera.z));
					regionOffset.upload();
				}
				region.vertices.bind();
				// Read after binding: binding is what grows vanilla's shared quad indices to fit the region, and
				// growing them can widen the index type.
				indexType = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).type().asGLType;
				boundRegion = regionIndex;
			}
			GL32.glDrawElementsBaseVertex(GL11.GL_TRIANGLES, indexCountFor(draws[i + 2]), indexType, 0L, draws[i + 1]);
		}

		if (regionOffset != null) {
			regionOffset.set(0.0F, 0.0F, 0.0F);
		}
		shader.clear();
		VertexBuffer.unbind();
		renderType.clearRenderState();
	}
	*///?}

	//? >=1.21.11 {
	/**
	 * The sway settings buffer, with the current intensity in it, or zero while the wind is switched off. It is
	 * written only when the value changed, so moving the slider is seen live and costs nothing the rest of the
	 * time.
	 */
	private static GpuBuffer swaySettings() {
		if (swaySettings == null) {
			swaySettings = RenderSystem.getDevice().createBuffer(
					() -> "MC2 foliage sway settings",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					SWAY_SETTINGS_SIZE);
			uploadedIntensity = Float.NaN;
		}
		float intensity = FoliageSettings.wavingFoliage() ? FoliageSettings.wavingIntensity() : 0.0F;
		if (intensity != uploadedIntensity) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				ByteBuffer data = Std140Builder.onStack(stack, SWAY_SETTINGS_SIZE).putFloat(intensity).get();
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(swaySettings.slice(), data);
			}
			uploadedIntensity = intensity;
		}
		return swaySettings;
	}
	//?}

	//? <1.21.11 {
	/*private static final LongOpenHashSet REGIONS_WITH_QUEUED_SECTIONS = new LongOpenHashSet();

	/^*
	 * Uploads at most one region this frame: the one waiting longest among those with nothing left in the queue, or
	 * that have waited too long. A region upload can be tens of megabytes with a detailed resource pack, and uploading
	 * a region for every section built into it, every frame, was most of what the renderer cost.
	 ^/
	private static void uploadDueRegion() {
		if (DIRTY_REGIONS.isEmpty()) {
			return;
		}
		REGIONS_WITH_QUEUED_SECTIONS.clear();
		for (long key : DIRTY) {
			REGIONS_WITH_QUEUED_SECTIONS.add(regionKeyOf(key));
		}
		long now = System.currentTimeMillis();
		// Oldest first: the set keeps the order regions started waiting in.
		var waiting = DIRTY_REGIONS.iterator();
		while (waiting.hasNext()) {
			Region region = waiting.next();
			if (now - region.pendingSince < REGION_UPLOAD_MAX_WAIT_MILLIS
					&& REGIONS_WITH_QUEUED_SECTIONS.contains(region.key)) {
				continue;
			}
			waiting.remove();
			region.upload();
			return;
		}
	}
	*///?}

	private static boolean isVisible(Section section, Frustum frustum, Object sodium) {
		// Only the sections whose chunk mesh on screen went without their foliage are drawn here; every
		// other one still has it in the chunk mesh.
		if (!GpuFoliageSplit.chunkMeshLeftFoliage(section.key)) {
			return false;
		}
		if (frustum != null && !frustum.isVisible(section.bounds)) {
			return false;
		}
		if (sodium == null) {
			return true;
		}
		// The box is kept inside the section so Sodium answers for this section and not its neighbours.
		BlockPos origin = section.origin;
		return SodiumBridge.isVisible(sodium,
				origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1,
				origin.getX() + SECTION_SIZE - 1, origin.getY() + SECTION_SIZE - 1, origin.getZ() + SECTION_SIZE - 1);
	}

	private static void addDraw(int regionIndex, int firstVertex, int vertexCount) {
		if (drawsSize + 3 > draws.length) {
			draws = Arrays.copyOf(draws, draws.length * 2);
		}
		draws[drawsSize++] = regionIndex;
		draws[drawsSize++] = firstVertex;
		draws[drawsSize++] = vertexCount;
	}

	private static int indexCountFor(int vertexCount) {
		return vertexCount / 4 * 6;
	}

	/**
	 * Keeps the near area on the player. When it moves, every section with foliage that crossed its
	 * edge is meshed again, which is what hands that foliage over between the chunk mesh and this
	 * renderer; sections that came inside are also queued here, so their geometry is built in time.
	 * <p>
	 * Only the old and the new area are walked, so even a teleport costs two squares of chunks.
	 */
	/**
	 * How far out, in chunks, the renderer draws the foliage for a render distance. Half of it up to 8 chunks, as
	 * it always was; past that a share shrinking steadily to a quarter, which keeps the near area close to 5
	 * chunks all the way from 10 to 20 and lets it reach no further than 8 at 32.
	 */
	static int nearRadius(int renderDistance) {
		if (renderDistance <= FULL_NEAR_SHARE_UP_TO) {
			return Math.max(MIN_NEAR_RADIUS, renderDistance / 2);
		}
		float share = Math.max(MIN_NEAR_SHARE,
				FULL_NEAR_SHARE - (renderDistance - FULL_NEAR_SHARE_UP_TO) * NEAR_SHARE_DROP_PER_CHUNK);
		return Math.max(MIN_NEAR_RADIUS, Math.round(renderDistance * share));
	}

	private static void updateNearArea(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		int centreX = SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
		int centreZ = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
		int radius = nearRadius(minecraft.options.renderDistance().get());
		boolean hadArea = GpuFoliageSplit.hasArea();
		int oldX = GpuFoliageSplit.centreX();
		int oldZ = GpuFoliageSplit.centreZ();
		int oldRadius = GpuFoliageSplit.radius();
		if (hadArea && centreX == oldX && centreZ == oldZ && radius == oldRadius) {
			return;
		}
		GpuFoliageSplit.setArea(centreX, centreZ, radius);
		if (hadArea) {
			for (int chunkX = oldX - oldRadius; chunkX <= oldX + oldRadius; chunkX++) {
				for (int chunkZ = oldZ - oldRadius; chunkZ <= oldZ + oldRadius; chunkZ++) {
					if (!GpuFoliageSplit.isNear(chunkX, chunkZ)) {
						remeshColumn(minecraft, chunkX, chunkZ, false);
					}
				}
			}
		}
		for (int chunkX = centreX - radius; chunkX <= centreX + radius; chunkX++) {
			for (int chunkZ = centreZ - radius; chunkZ <= centreZ + radius; chunkZ++) {
				boolean wasNear = hadArea && Math.max(Math.abs(chunkX - oldX), Math.abs(chunkZ - oldZ)) <= oldRadius;
				if (!wasNear) {
					remeshColumn(minecraft, chunkX, chunkZ, true);
				}
			}
		}
	}

	/**
	 * The GPU renderer was switched off: the chunk mesh takes back the near foliage, and everything the renderer
	 * held is let go. Switching it on again goes through {@link #updateNearArea} as on first use.
	 */
	private static void deactivate(Minecraft minecraft) {
		active = false;
		if (GpuFoliageSplit.hasArea()) {
			int centreX = GpuFoliageSplit.centreX();
			int centreZ = GpuFoliageSplit.centreZ();
			int radius = GpuFoliageSplit.radius();
			// Cleared before any column is queued, so none of their builds can still find itself near.
			GpuFoliageSplit.clearArea();
			for (int chunkX = centreX - radius; chunkX <= centreX + radius; chunkX++) {
				for (int chunkZ = centreZ - radius; chunkZ <= centreZ + radius; chunkZ++) {
					remeshColumn(minecraft, chunkX, chunkZ, false);
				}
			}
		}
		discardAll();
	}

	/** Has the chunk mesher mesh again every section of a column that could hold foliage. */
	/**
	 * Asks for a section's chunk mesh to be built again, by whichever route is in charge: vanilla's own, or
	 * Sodium's, which does not follow vanilla's. Asking both costs nothing when only one is listening.
	 */
	private static void rebuildChunkMesh(Minecraft minecraft, int sectionX, int sectionY, int sectionZ) {

		//? >=26.2 {
		minecraft.levelExtractor.setSectionDirty(sectionX, sectionY, sectionZ);
		//?} else {
		/*minecraft.levelRenderer.setSectionDirty(sectionX, sectionY, sectionZ);
		*///?}
		SodiumBridge.scheduleRebuild(sectionX, sectionY, sectionZ);
	}

	private static void remeshColumn(Minecraft minecraft, int chunkX, int chunkZ, boolean nowNear) {
		ClientLevel level = minecraft.level;
		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			return;
		}
		LevelChunkSection[] sections = chunk.getSections();
		for (int index = 0; index < sections.length; index++) {
			if (!mayHoldFoliage(sections[index])) {
				continue;
			}
			int sectionY = level.getSectionYFromSectionIndex(index);
			long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
			if (!nowNear || GpuFoliageSplit.isGpuReady(key)) {
				// Leaving: the chunk mesh takes the foliage back. Entering, already on the GPU: it lets it go.
				rebuildChunkMesh(minecraft, chunkX, sectionY, chunkZ);
			} else {
				// Entering, not built yet: the chunk mesh keeps the foliage until it is, and is asked to let go then.
				DIRTY.add(key);
			}
		}
	}

	/** How far from the player sections are built and kept, in chunks. */
	private static int buildRadius() {
		return GpuFoliageSplit.hasArea() ? GpuFoliageSplit.radius() + BUILD_MARGIN : 0;
	}

	/**
	 * Rebuilds the queued sections nearest the camera. Nearest first matters because a chunk load
	 * storm can leave thousands of entries queued, and foliage appearing late at your feet is far
	 * more noticeable than foliage appearing late further out.
	 */
	private static void buildNearest(Minecraft minecraft, Vec3 camera) {
		long[] nearest = new long[REBUILD_BUDGET];
		double[] distances = new double[REBUILD_BUDGET];
		Arrays.fill(distances, Double.MAX_VALUE);
		int found = 0;

		var pending = DIRTY.iterator();
		while (pending.hasNext()) {
			long key = pending.next();
			if (!withinRange(minecraft, key)) {
				pending.remove();
				continue;
			}
			double distance = distanceSqTo(key, camera);
			for (int slot = 0; slot < REBUILD_BUDGET; slot++) {
				if (distance < distances[slot]) {
					int tail = REBUILD_BUDGET - slot - 1;
					System.arraycopy(distances, slot, distances, slot + 1, tail);
					System.arraycopy(nearest, slot, nearest, slot + 1, tail);
					distances[slot] = distance;
					nearest[slot] = key;
					found = Math.min(found + 1, REBUILD_BUDGET);
					break;
				}
			}
		}

		//? <1.21.11 {
		/*long budgetEnd = System.nanoTime() + REBUILD_BUDGET_NANOS;
		*///?}
		for (int i = 0; i < found; i++) {
			//? <1.21.11 {
			/*// However few sections that is: a heavy resource pack makes a single one cost several milliseconds.
			if (i > 0 && System.nanoTime() >= budgetEnd) {
				break;
			}
			*///?}
			DIRTY.remove(nearest[i]);
			rebuild(minecraft, minecraft.level, nearest[i]);
		}
	}

	private static double distanceSqTo(long key, Vec3 camera) {
		double x = SectionPos.sectionToBlockCoord(SectionPos.x(key)) + 8.0D - camera.x;
		double y = SectionPos.sectionToBlockCoord(SectionPos.y(key)) + 8.0D - camera.y;
		double z = SectionPos.sectionToBlockCoord(SectionPos.z(key)) + 8.0D - camera.z;
		return x * x + y * y + z * z;
	}

	/** Sections beyond the build radius are neither built nor kept. */
	private static boolean withinRange(Minecraft minecraft, long key) {
		if (minecraft.player == null) {
			return false;
		}
		int limit = buildRadius();
		int dx = SectionPos.x(key) - SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
		int dz = SectionPos.z(key) - SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
		int dy = SectionPos.y(key) - SectionPos.blockToSectionCoord(minecraft.player.getBlockY());
		return Math.abs(dx) <= limit && Math.abs(dz) <= limit && Math.abs(dy) <= limit;
	}

	/** A region is kept while its section nearest the player is still within range. */
	private static boolean regionWithinRange(Minecraft minecraft, long regionKey) {
		if (minecraft.player == null) {
			return false;
		}
		int minX = SectionPos.x(regionKey) << REGION_WIDTH_SHIFT;
		int minY = SectionPos.y(regionKey) << REGION_HEIGHT_SHIFT;
		int minZ = SectionPos.z(regionKey) << REGION_WIDTH_SHIFT;
		return withinRange(minecraft, SectionPos.asLong(
				Mth.clamp(SectionPos.blockToSectionCoord(minecraft.player.getBlockX()), minX, minX + REGION_WIDTH - 1),
				Mth.clamp(SectionPos.blockToSectionCoord(minecraft.player.getBlockY()), minY, minY + REGION_HEIGHT - 1),
				Mth.clamp(SectionPos.blockToSectionCoord(minecraft.player.getBlockZ()), minZ, minZ + REGION_WIDTH - 1)));
	}

	//? >=1.21.1 && <26.1.2 {
	/*/^*
	 * Appends the mod's per-vertex data as vanilla writes each vertex, for the versions where a block is
	 * tesselated into a vertex consumer rather than handed over one quad at a time.
	 * <p>
	 * Everything is passed straight through to the buffer, untouched. Only {@code addVertex} is of
	 * interest: it is where a vertex's final position is known, and one set of weights is written for it.
	 * The position arrives relative to the region, so the block's own corner is taken off again to get the
	 * height within the plant that a sway weight is measured against.
	 ^/
	private static final class FoliageVertexWriter implements VertexConsumer {

		private final VertexConsumer delegate;
		private final SwayAnchor anchor;
		private final BlockPos regionOrigin;
		private float blockY;

		FoliageVertexWriter(VertexConsumer delegate, SwayAnchor anchor, BlockPos regionOrigin) {
			this.delegate = delegate;
			this.anchor = anchor;
			this.regionOrigin = regionOrigin;
		}

		/^* The height of the block about to be tesselated, relative to the region. ^/
		void beginBlock(float blockY) {
			this.blockY = blockY;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			float localY = y - blockY;
			reserveWeights(WEIGHT_BYTES);
			weightScratch.putFloat(packCell(anchor.cellX - regionOrigin.getX(),
					anchor.cellY - regionOrigin.getY(),
					anchor.cellZ - regionOrigin.getZ()));
			weightScratch.putFloat(packWeights(anchor.weightAt(regionOrigin.getY() + y),
					anchor.pushWeightAt(localY)));
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			delegate.setColor(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer setColor(int packed) {
			delegate.setColor(packed);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			delegate.setNormal(x, y, z);
			return this;
		}

		//? >=1.21.11 {
		/^@Override
		public VertexConsumer setLineWidth(float width) {
			delegate.setLineWidth(width);
			return this;
		}
		^///?}
	}
	*///?} elif <1.21.1 {
	/*/^*
	 * Appends the mod's per-vertex data as vanilla writes each vertex, as the newer writer does, against the vertex
	 * consumer of before 1.21.1: a vertex is either written whole in one call, which is how a block's quads arrive,
	 * or element by element and then ended, and its weights are written as it is finished either way.
	 ^/
	private static final class FoliageVertexWriter implements VertexConsumer {

		private final VertexConsumer delegate;
		private final SwayAnchor anchor;
		private final BlockPos regionOrigin;
		private float blockY;
		/^* The height of a vertex being written element by element, kept until it is ended. ^/
		private float pendingY;

		FoliageVertexWriter(VertexConsumer delegate, SwayAnchor anchor, BlockPos regionOrigin) {
			this.delegate = delegate;
			this.anchor = anchor;
			this.regionOrigin = regionOrigin;
		}

		/^* The height of the block about to be tesselated, relative to the region. ^/
		void beginBlock(float blockY) {
			this.blockY = blockY;
		}

		private void writeWeights(float y) {
			float localY = y - blockY;
			reserveWeights(WEIGHT_BYTES);
			weightScratch.putFloat(packCell(anchor.cellX - regionOrigin.getX(),
					anchor.cellY - regionOrigin.getY(),
					anchor.cellZ - regionOrigin.getZ()));
			weightScratch.putFloat(packWeights(anchor.weightAt(regionOrigin.getY() + y),
					anchor.pushWeightAt(localY)));
		}

		@Override
		public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
				float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
			delegate.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
			writeWeights(y);
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			delegate.vertex(x, y, z);
			pendingY = (float) y;
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			delegate.color(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			delegate.uv(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			delegate.overlayCoords(u, v);
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			delegate.uv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			delegate.normal(x, y, z);
			return this;
		}

		@Override
		public void endVertex() {
			delegate.endVertex();
			writeWeights(pendingY);
		}

		@Override
		public void defaultColor(int red, int green, int blue, int alpha) {
			delegate.defaultColor(red, green, blue, alpha);
		}

		@Override
		public void unsetDefaultColor() {
			delegate.unsetDefaultColor();
		}
	}
	*///?}

	private static boolean warnedForeignVertexFormat;

	/**
	 * Staging memory for a region upload, reused between them and grown when a region needs more. Render
	 * thread only, and only live for the length of one upload: nothing holds on to it afterwards.
	 */
	private static ByteBuffer uploadStaging;

	private static ByteBuffer uploadScratch(int bytes) {
		if (uploadStaging == null || uploadStaging.capacity() < bytes) {
			if (uploadStaging != null) {
				MemoryUtil.memFree(uploadStaging);
			}
			uploadStaging = MemoryUtil.memAlloc(Math.max(bytes, 64 * 1024));
		}
		return uploadStaging.clear().limit(bytes);
	}

	//? >=1.21.1 && <1.21.11 {
	/*/^* The same, for the versions that upload a region as a mesh. It is left empty by every upload. ^/
	private static ByteBufferBuilder uploadBuilder;

	private static ByteBufferBuilder uploadBuilder() {
		if (uploadBuilder == null) {
			uploadBuilder = new ByteBufferBuilder(64 * 1024);
		}
		return uploadBuilder;
	}
	*///?} elif <1.21.1 {
	/*/^* The same, as a builder: before 1.21.1 a mesh only comes out of one. It is left empty by every upload. ^/
	private static BufferBuilder uploadBuilder;

	private static BufferBuilder uploadBuilder() {
		if (uploadBuilder == null) {
			uploadBuilder = new BufferBuilder(64 * 1024);
		}
		return uploadBuilder;
	}
	*///?}

	private static boolean warnedWeightCountMismatch;

	private static void warnWeightCountMismatch(int vertices, int weights) {
		if (warnedWeightCountMismatch) {
			return;
		}
		warnedWeightCountMismatch = true;
		ModTemplate.LOGGER.warn("Foliage was meshed with {} vertices but {} sway weights, so the GPU renderer "
				+ "left it to the chunk mesh. Another mod is writing vertices the mod cannot see.",
				vertices, weights);
	}

	private static void warnForeignVertexFormat(Object format) {
		if (warnedForeignVertexFormat) {
			return;
		}
		warnedForeignVertexFormat = true;
		ModTemplate.LOGGER.warn("Foliage was meshed in {} rather than the block format, so the GPU renderer "
				+ "left it to the chunk mesh. Another mod is widening the mod's vertex buffer.", format);
	}

	private static void reserveWeights(int bytes) {
		if (weightScratch.remaining() >= bytes) {
			return;
		}
		ByteBuffer grown = MemoryUtil.memAlloc(Math.max(weightScratch.capacity() * 2, weightScratch.position() + bytes));
		weightScratch.flip();
		grown.put(weightScratch);
		MemoryUtil.memFree(weightScratch);
		weightScratch = grown;
	}

	/**
	 * Meshes a section, with Iris told to leave the buffer alone: it widens any buffer asked for the block
	 * format while a shader pack is loaded, and these vertices are written for a pipeline of the mod's own.
	 */
	private static void rebuild(Minecraft minecraft, ClientLevel level, long key) {
		boolean wasSkipping = IrisVertexExtension.begin();
		try {
			meshSection(minecraft, level, key);
		} finally {
			IrisVertexExtension.end(wasSkipping);
		}
	}

	private static void meshSection(Minecraft minecraft, ClientLevel level, long key) {
		if (!mayHoldFoliage(level, key)) {
			removeSection(key);
			return;
		}
		if (modelRenderer == null) {
			// Same arguments the section compiler passes, so the geometry matches it exactly.
			//? >=26.1.2 {
			modelRenderer = new ModelBlockRenderer(
					minecraft.options.ambientOcclusion().get(),
					minecraft.options.cutoutLeaves().get(),
					minecraft.getBlockColors());
			//?} else {
			/*modelRenderer = new ModelBlockRenderer(minecraft.getBlockColors());
			*///?}
		}
		if (vertexScratch == null) {
			//? >=1.21.1 {
			vertexScratch = new ByteBufferBuilder(VERTEX_BYTES * 4 * INITIAL_SCRATCH_QUADS);
			//?} else {
			/*vertexScratch = new BufferBuilder(VERTEX_BYTES * 4 * INITIAL_SCRATCH_QUADS);
			*///?}
			weightScratch = MemoryUtil.memAlloc(WEIGHT_BYTES * 4 * INITIAL_SCRATCH_QUADS);
		}
		vertexScratch.clear();
		weightScratch.clear();

		BlockPos origin = new BlockPos(
				SectionPos.sectionToBlockCoord(SectionPos.x(key)),
				SectionPos.sectionToBlockCoord(SectionPos.y(key)),
				SectionPos.sectionToBlockCoord(SectionPos.z(key)));
		BlockPos regionOrigin = regionOrigin(regionKeyOf(key));
		// Vertices are written relative to the region, so every section in it can share its buffers.
		int offsetX = origin.getX() - regionOrigin.getX();
		int offsetY = origin.getY() - regionOrigin.getY();
		int offsetZ = origin.getZ() - regionOrigin.getZ();

		//? >=26.2 {
		BufferBuilder builder = new BufferBuilder(vertexScratch, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
		//?} elif >=1.21.1 {
		/*BufferBuilder builder = new BufferBuilder(vertexScratch, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
		*///?} else {
		/*BufferBuilder builder = vertexScratch;
		builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
		*///?}
		// Vanilla writes the standard attributes; alongside each quad we append the sway weight of its
		// four vertices, so both bindings stay in step without touching its output.
		SwayAnchor anchor = new SwayAnchor();
		//? >=26.1.2 {
		BlockQuadOutput output = (x, y, z, quad, instance) -> {
			builder.putBlockBakedQuad(x, y, z, quad, instance);
			reserveWeights(WEIGHT_BYTES * 4);
			float cell = packCell(anchor.cellX - regionOrigin.getX(),
					anchor.cellY - regionOrigin.getY(),
					anchor.cellZ - regionOrigin.getZ());
			for (int vertex = 0; vertex < 4; vertex++) {
				float localY = quad.position(vertex).y();
				float worldY = regionOrigin.getY() + y + localY;
				weightScratch.putFloat(cell);
				weightScratch.putFloat(packWeights(anchor.weightAt(worldY), anchor.pushWeightAt(localY)));
			}
		};

		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
		//?} else {
		/*// Before 26.1.2 a block is tesselated into a vertex consumer rather than handed over quad by quad,
		// so the weights are appended as each vertex is written instead of once per quad.
		FoliageVertexWriter output = new FoliageVertexWriter(builder, anchor, regionOrigin);
		PoseStack poseStack = new PoseStack();
		RandomSource random = RandomSource.create();
		BlockModelShaper models = minecraft.getModelManager().getBlockModelShaper();
		*///?}
		// Blocks are read from the section itself: going through the level would look the chunk up again
		// for every one of its 4096 blocks.
		LevelChunkSection blocks = level.getChunk(SectionPos.x(key), SectionPos.z(key))
				.getSection(level.getSectionIndexFromSectionY(SectionPos.y(key)));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < SECTION_SIZE; dx++) {
			for (int dy = 0; dy < SECTION_SIZE; dy++) {
				for (int dz = 0; dz < SECTION_SIZE; dz++) {
					BlockState state = blocks.getBlockState(dx, dy, dz);
					if (!GpuFoliageSplit.isFoliage(state)) {
						continue;
					}
					pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					anchor.prepare(state, pos, level);
					//? >=26.1.2 {
					modelRenderer.tesselateBlock(output, offsetX + dx, offsetY + dy, offsetZ + dz,
							level, pos, state, GpuFoliageSplit.modelFor(state, models.get(state)),
							state.getSeed(pos));
					//?} else {
					/*//? >=1.21.11 {
					random.setSeed(state.getSeed(pos));
					List<BlockModelPart> parts = GpuFoliageSplit
							.modelFor(state, models.getBlockModel(state))
							.collectParts(random);
					// The pose carries what the newer call took as arguments: where the block sits in
					// its region. The writer subtracts it again to recover each vertex's height in
					// its own block, which is what a sway weight is measured against.
					poseStack.pushPose();
					poseStack.translate(offsetX + dx, offsetY + dy, offsetZ + dz);
					output.beginBlock(offsetY + dy);
					modelRenderer.tesselateBlock(level, parts, state, pos, poseStack, output, true,
							OverlayTexture.NO_OVERLAY);
					poseStack.popPose();
					//?} else {
					/^poseStack.pushPose();
					poseStack.translate(offsetX + dx, offsetY + dy, offsetZ + dz);
					output.beginBlock(offsetY + dy);
					tesselate(level, GpuFoliageSplit.modelFor(state, models.getBlockModel(state)),
							state, pos, poseStack, output, random);
					poseStack.popPose();
					^///?}
					*///?}
				}
			}
		}

		//? >=1.21.1 {
		MeshData mesh = builder.build();
		//?} else {
		/*BufferBuilder.RenderedBuffer mesh = builder.endOrDiscardIfEmpty();
		*///?}
		if (mesh == null) {
			removeSection(key);
			return;
		}
		//? >=1.21.1 {
		try (mesh) {
		//?} else {
		/*// Before 1.21.1 the mesh is not closeable: it hands its memory back to the builder when released.
		try {
		*///?}
			// The mesh says how many vertices it holds and in what format, rather than dividing its size by
			// the block format's stride: another mod may widen the buffer behind our back -- Iris does, for
			// shader packs -- and the vertices would then be read at a stride they were not written at. A
			// section that comes back in another format is left to the chunk mesh instead of drawn as noise.
			if (!DefaultVertexFormat.BLOCK.equals(mesh.drawState().format())) {
				warnForeignVertexFormat(mesh.drawState().format());
				removeSection(key);
				return;
			}
			ByteBuffer meshVertices = mesh.vertexBuffer();
			int vertexCount = mesh.drawState().vertexCount();
			weightScratch.flip();
			// One set of weights was written for each vertex as it was written. If the two ever disagree,
			// every vertex past the first difference would be read from the wrong place, so the section is
			// left to the chunk mesh rather than drawn wrongly.
			if (weightScratch.remaining() != vertexCount * WEIGHT_BYTES) {
				warnWeightCountMismatch(vertexCount, weightScratch.remaining() / WEIGHT_BYTES);
				removeSection(key);
				return;
			}
			// Interleaved here, once, rather than each time the section's region is uploaded.
			int stride = VERTEX_BYTES + WEIGHT_BYTES;
			int meshBase = meshVertices.position();
			ByteBuffer data = MemoryUtil.memAlloc(vertexCount * stride);
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				data.put(vertex * stride, meshVertices, meshBase + vertex * VERTEX_BYTES, VERTEX_BYTES);
				data.put(vertex * stride + VERTEX_BYTES, weightScratch, vertex * WEIGHT_BYTES, WEIGHT_BYTES);
			}
			putSection(key, new Section(key, origin, data, vertexCount));
		//? >=1.21.1 {
		}
		//?} else {
		/*} finally {
			mesh.release();
		}
		*///?}
	}

	//? <1.21.11 {
	/*/^*
	 * Vanilla's {@code tesselateBlock}, step for step: the same choice of smooth or flat lighting, and the same
	 * random offset for plants that have one.
	 * <p>
	 * It is not called itself because, before 1.21.11, both Fabric's renderer and Sodium take it over for any
	 * model that is not a plain vanilla one, and send it through the rendering API instead -- where Sway would
	 * bake its own sway into geometry the shader is about to move again. On NeoForge, Sway hooks it too. The two methods it picks between are
	 * left alone by both, and read the model's plain quads, which Sway's wrapper hands over untouched.
	 ^/
	private static void tesselate(ClientLevel level, BakedModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, VertexConsumer output, RandomSource random) {
		//? neoforge {
		/^// NeoForge lets a model decide, and only falls back to the block's light where it does not.
		boolean smoothLighting = Minecraft.useAmbientOcclusion()
				&& switch (model.useAmbientOcclusion(state, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) {
					case TRUE -> true;
					case DEFAULT -> state.getLightEmission(level, pos) == 0;
					case FALSE -> false;
				};
		^///?} elif forge {
		/^// Forge's rule, as its own tesselateBlock applies it: the block's light where it is placed, and the model asked
		// with no layer in particular, as a block outside a chunk is.
		boolean smoothLighting = Minecraft.useAmbientOcclusion() && state.getLightEmission(level, pos) == 0
				&& model.useAmbientOcclusion(state, null);
		^///?} else {
		boolean smoothLighting = Minecraft.useAmbientOcclusion() && state.getLightEmission() == 0
				&& model.useAmbientOcclusion();
		//?}
		Vec3 offset = state.getOffset(level, pos);
		poseStack.translate(offset.x, offset.y, offset.z);
		long seed = state.getSeed(pos);
		if (smoothLighting) {
			modelRenderer.tesselateWithAO(level, model, state, pos, poseStack, output, true, random, seed,
					OverlayTexture.NO_OVERLAY);
		} else {
			modelRenderer.tesselateWithoutAO(level, model, state, pos, poseStack, output, true, random, seed,
					OverlayTexture.NO_OVERLAY);
		}
	}
	*///?}
}
//?}
