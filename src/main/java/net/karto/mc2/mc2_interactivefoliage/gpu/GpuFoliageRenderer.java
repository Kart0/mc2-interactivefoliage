package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=26.1.2 {

import com.github.razorplay01.sway.api.SwayAPI;
import com.github.razorplay01.sway.api.behavior.BehaviorPipeline;
import com.github.razorplay01.sway.api.behavior.contributors.DeformationContributor;
import com.github.razorplay01.sway.api.behavior.contributors.MultiBlockContributor;
import com.github.razorplay01.sway.client.behavior.multiblock.HangingVineMultiblockBehavior;
//? >=26.2 {
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
//?}
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
//? >=26.2 {
import com.mojang.blaze3d.pipeline.BindGroupLayout;
//?} else {
/*import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.VertexFormatElement;
*///?}
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.karto.mc2.mc2_interactivefoliage.FoliageSettings;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
	/**
	 * Padding on cull boxes so foliage leaning into view is not culled away: room for the sway at its strongest
	 * plus the strongest push from an entity, on a tall plant with Sway's intensity turned up.
	 */
	private static final double SWAY_MARGIN = 2.5D;
	private static final int SECTION_SIZE = 16;
	/** The near area never shrinks below this many chunks, however short the render distance. */
	private static final int MIN_NEAR_RADIUS = 2;
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
	//? >=26.2 {
	// A binding of its own, which is the tidiest a pipeline allows from 26.2 on.
	private static final VertexFormat WAVE_FORMAT = VertexFormat.builder(0)
			.addAttribute("WaveWeight", GpuFormat.R32_FLOAT)
			.addAttribute("SwayCell", GpuFormat.RGB32_FLOAT)
			.addAttribute("PushWeight", GpuFormat.R32_FLOAT)
			.build();
	//?} else {
	/*// Before 26.2 a pipeline reads one vertex buffer only, so these ride with the block's own attributes in
	// a single format, interleaved as a region is uploaded.
	private static final VertexFormatElement WAVE_WEIGHT_ELEMENT =
			VertexFormatElement.register(10, 0, VertexFormatElement.Type.FLOAT, false, 1);
	private static final VertexFormatElement SWAY_CELL_ELEMENT =
			VertexFormatElement.register(11, 0, VertexFormatElement.Type.FLOAT, false, 3);
	private static final VertexFormatElement PUSH_WEIGHT_ELEMENT =
			VertexFormatElement.register(12, 0, VertexFormatElement.Type.FLOAT, false, 1);
	private static final VertexFormat FOLIAGE_FORMAT = foliageFormat();

	private static VertexFormat foliageFormat() {
		VertexFormat block = DefaultVertexFormat.BLOCK;
		VertexFormat.Builder builder = VertexFormat.builder();
		for (VertexFormatElement element : block.getElements()) {
			builder.add(block.getElementName(element), element);
		}
		return builder
				.add("WaveWeight", WAVE_WEIGHT_ELEMENT)
				.add("SwayCell", SWAY_CELL_ELEMENT)
				.add("PushWeight", PUSH_WEIGHT_ELEMENT)
				.build();
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
	/** A whole vec4 for one float, so the buffer is never smaller than the block once a driver pads it. */
	private static final int SWAY_SETTINGS_SIZE = new Std140SizeCalculator().putVec4().get();

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
			.withVertexBinding(1, WAVE_FORMAT)
			.withBindGroupLayout(SWAY_SETTINGS)
			.withBindGroupLayout(GpuFoliageInteraction.LAYOUT)
			.withShaderDefine("ALPHA_CUTOUT", 0.5F)
			.build();
	//?} else {
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
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.withShaderDefine("ALPHA_CUTOUT", 0.5F)
			.build();
	*///?}

	private static final int VERTEX_BYTES = DefaultVertexFormat.BLOCK.getVertexSize();
	/** Our own per-vertex data: the wind weight, the anchor block and the push weight, five floats in all. */
	private static final int WEIGHT_BYTES = Float.BYTES * 5;
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
	/** Set when every buffer was thrown away, so the chunks already loaded get queued again. */
	private static boolean reseedPending;

	/** Holds the sway settings the shader reads; rewritten only when one of them changes. */
	private static GpuBuffer swaySettings;
	private static float uploadedIntensity = Float.NaN;

	/** Reused by every rebuild and grown to the largest section seen, so no rebuild has a size limit. */
	private static ByteBufferBuilder vertexScratch;
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
		final ByteBuffer vertices;
		final ByteBuffer weights;
		final int vertexCount;

		Section(long key, BlockPos origin, ByteBuffer vertices, ByteBuffer weights, int vertexCount) {
			this.key = key;
			this.origin = origin;
			this.bounds = new AABB(
					origin.getX() - SWAY_MARGIN,
					origin.getY() - SWAY_MARGIN,
					origin.getZ() - SWAY_MARGIN,
					origin.getX() + SECTION_SIZE + SWAY_MARGIN,
					origin.getY() + SECTION_SIZE + SWAY_MARGIN,
					origin.getZ() + SECTION_SIZE + SWAY_MARGIN);
			this.vertices = vertices;
			this.weights = weights;
			this.vertexCount = vertexCount;
		}

		void free() {
			MemoryUtil.memFree(vertices);
			MemoryUtil.memFree(weights);
		}
	}

	/**
	 * A block of sections drawn from one pair of buffers. Its sections' vertices are relative to the
	 * region's corner, which keeps them small enough for float precision however far out it sits.
	 */
	private static final class Region {
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
		GpuBuffer vertices;
		GpuBuffer weights;

		Region(long regionKey) {
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
			section.free();
			sections[slot] = null;
			sectionCount--;
			return true;
		}

		void upload() {
			closeBuffers();
			int vertexCount = 0;
			occupiedCount = 0;
			for (int slot = 0; slot < SECTIONS_PER_REGION; slot++) {
				if (sections[slot] != null) {
					firstVertex[slot] = vertexCount;
					vertexCount += sections[slot].vertexCount;
					occupied[occupiedCount++] = slot;
				}
			}
			//? >=26.2 {
			ByteBuffer vertexData = MemoryUtil.memAlloc(vertexCount * VERTEX_BYTES);
			ByteBuffer weightData = MemoryUtil.memAlloc(vertexCount * WEIGHT_BYTES);
			try {
				// Absolute copies, so no view of each section's buffers has to be created to read them.
				for (int i = 0; i < occupiedCount; i++) {
					int slot = occupied[i];
					Section section = sections[slot];
					vertexData.put(firstVertex[slot] * VERTEX_BYTES, section.vertices, 0, section.vertexCount * VERTEX_BYTES);
					weightData.put(firstVertex[slot] * WEIGHT_BYTES, section.weights, 0, section.vertexCount * WEIGHT_BYTES);
				}
				vertices = RenderSystem.getDevice().createBuffer(
						() -> "MC2 foliage vertices",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
						vertexData);
				weights = RenderSystem.getDevice().createBuffer(
						() -> "MC2 foliage sway weights",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
						weightData);
			} finally {
				MemoryUtil.memFree(vertexData);
				MemoryUtil.memFree(weightData);
			}
			//?} else {
			/*// One buffer holding both, vertex by vertex: the block's own attributes, then ours. A pipeline
			// reads a single vertex buffer before 26.2, so the two are interleaved here rather than bound apart.
			int stride = VERTEX_BYTES + WEIGHT_BYTES;
			ByteBuffer vertexData = MemoryUtil.memAlloc(vertexCount * stride);
			try {
				for (int i = 0; i < occupiedCount; i++) {
					int slot = occupied[i];
					Section section = sections[slot];
					int base = firstVertex[slot] * stride;
					for (int vertex = 0; vertex < section.vertexCount; vertex++) {
						vertexData.put(base + vertex * stride, section.vertices, vertex * VERTEX_BYTES, VERTEX_BYTES);
						vertexData.put(base + vertex * stride + VERTEX_BYTES, section.weights, vertex * WEIGHT_BYTES, WEIGHT_BYTES);
					}
				}
				vertices = RenderSystem.getDevice().createBuffer(
						() -> "MC2 foliage vertices",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
						vertexData);
			} finally {
				MemoryUtil.memFree(vertexData);
			}
			*///?}
		}

		void closeBuffers() {
			if (vertices != null) {
				vertices.close();
				vertices = null;
			}
			if (weights != null) {
				weights.close();
				weights = null;
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
			return Math.clamp(distance / SWAY_SPAN, 0.0F, 1.0F) * damping;
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
		for (int sectionY = level.getMinSectionY(); sectionY <= level.getMaxSectionY(); sectionY++) {
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
		if (sectionY < level.getMinSectionY() || sectionY > level.getMaxSectionY()) {
			return false;
		}
		return mayHoldFoliage(level.getChunk(SectionPos.x(key), SectionPos.z(key))
				.getSection(level.getSectionIndexFromSectionY(sectionY)));
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
		DIRTY_REGIONS.add(region);
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
			DIRTY_REGIONS.add(region);
		}
	}

	/** Called by each loader's hooks once the opaque terrain is drawn. */
	public static void draw(LevelRenderState levelState) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		// Decisions keep being applied while switched off, so the record is accurate when switched back on.
		GpuFoliageSplit.applyMeshDecisions();
		if (!FoliageSettings.gpuRenderer()) {
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

		// The render state carries the camera and the cull frustum vanilla already prepared this
		// frame, which is available whether or not Sodium owns the terrain renderer.
		CameraRenderState cameraState = levelState.cameraRenderState;
		if (cameraState == null || cameraState.pos == null) {
			return;
		}
		Vec3 camera = cameraState.pos;
		updateNearArea(minecraft);
		if (reseedPending && minecraft.player != null) {
			reseedPending = false;
			reseedLoadedChunks(minecraft, minecraft.level);
		}
		buildNearest(minecraft, camera);
		// Every change is folded into its region before anything is drawn, so a region's buffers and
		// the layout used to draw from them always agree.
		DIRTY_REGIONS.forEach(Region::upload);
		DIRTY_REGIONS.clear();

		Frustum frustum = cameraState.cullFrustum;
		Object sodium = SodiumOcclusion.renderer();
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
				continue;
			}
			if (frustum != null && !frustum.isVisible(region.bounds)) {
				continue;
			}
			// A region wholly inside the frustum needs no per-section frustum test. Its unpadded box is
			// used, so a section whose padding pokes outside is simply drawn -- never wrongly dropped.
			Frustum sectionFrustum = frustum != null
					&& frustum.cubeInFrustum(region.blockBounds) == FrustumIntersection.INSIDE ? null : frustum;

			// Walk the sections in buffer order, extending a run while they stay visible, so a region
			// that is fully in view costs one draw call however many sections it holds.
			int regionIndex = drawn.size();
			int drawsBefore = drawsSize;
			int runStart = -1;
			int runEnd = -1;
			for (int i = 0; i < region.occupiedCount; i++) {
				int slot = region.occupied[i];
				Section section = region.sections[slot];
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
			pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(),
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
					pass.setVertexBuffer(1, region.weights.slice());
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
	}

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
		return SodiumOcclusion.isVisible(sodium,
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
	private static void updateNearArea(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		int centreX = SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
		int centreZ = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
		int radius = Math.max(MIN_NEAR_RADIUS, minecraft.options.renderDistance().get() / 2);
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
			//? >=26.2 {
			minecraft.levelExtractor.setSectionDirty(chunkX, sectionY, chunkZ);
			//?} else {
			/*minecraft.levelRenderer.setSectionDirty(chunkX, sectionY, chunkZ);
			*///?}
			if (nowNear) {
				DIRTY.add(SectionPos.asLong(chunkX, sectionY, chunkZ));
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

		for (int i = 0; i < found; i++) {
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
				Math.clamp(SectionPos.blockToSectionCoord(minecraft.player.getBlockX()), minX, minX + REGION_WIDTH - 1),
				Math.clamp(SectionPos.blockToSectionCoord(minecraft.player.getBlockY()), minY, minY + REGION_HEIGHT - 1),
				Math.clamp(SectionPos.blockToSectionCoord(minecraft.player.getBlockZ()), minZ, minZ + REGION_WIDTH - 1)));
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

	private static void rebuild(Minecraft minecraft, ClientLevel level, long key) {
		if (!mayHoldFoliage(level, key)) {
			removeSection(key);
			return;
		}
		if (modelRenderer == null) {
			// Same arguments the section compiler passes, so the geometry matches it exactly.
			modelRenderer = new ModelBlockRenderer(
					minecraft.options.ambientOcclusion().get(),
					minecraft.options.cutoutLeaves().get(),
					minecraft.getBlockColors());
		}
		if (vertexScratch == null) {
			vertexScratch = new ByteBufferBuilder(VERTEX_BYTES * 4 * INITIAL_SCRATCH_QUADS);
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
		//?} else {
		/*BufferBuilder builder = new BufferBuilder(vertexScratch, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
		*///?}
		// Vanilla writes the standard attributes; alongside each quad we append the sway weight of its
		// four vertices, so both bindings stay in step without touching its output.
		SwayAnchor anchor = new SwayAnchor();
		BlockQuadOutput output = (x, y, z, quad, instance) -> {
			builder.putBlockBakedQuad(x, y, z, quad, instance);
			reserveWeights(WEIGHT_BYTES * 4);
			for (int vertex = 0; vertex < 4; vertex++) {
				float localY = quad.position(vertex).y();
				float worldY = regionOrigin.getY() + y + localY;
				weightScratch.putFloat(anchor.weightAt(worldY));
				weightScratch.putFloat(anchor.cellX - regionOrigin.getX());
				weightScratch.putFloat(anchor.cellY - regionOrigin.getY());
				weightScratch.putFloat(anchor.cellZ - regionOrigin.getZ());
				weightScratch.putFloat(anchor.pushWeightAt(localY));
			}
		};

		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
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
					modelRenderer.tesselateBlock(output, offsetX + dx, offsetY + dy, offsetZ + dz,
							level, pos, state, GpuFoliageSplit.modelFor(state, models.get(state)),
							state.getSeed(pos));
				}
			}
		}

		MeshData mesh = builder.build();
		if (mesh == null) {
			removeSection(key);
			return;
		}
		try (mesh) {
			ByteBuffer meshVertices = mesh.vertexBuffer();
			int vertexCount = meshVertices.remaining() / VERTEX_BYTES;
			ByteBuffer vertices = MemoryUtil.memAlloc(meshVertices.remaining());
			vertices.put(meshVertices).flip();
			weightScratch.flip();
			ByteBuffer weights = MemoryUtil.memAlloc(weightScratch.remaining());
			weights.put(weightScratch).flip();
			putSection(key, new Section(key, origin, vertices, weights, vertexCount));
		}
	}
}
//?}
