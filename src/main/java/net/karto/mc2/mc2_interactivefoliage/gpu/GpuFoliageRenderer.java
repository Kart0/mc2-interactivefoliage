package net.karto.mc2.mc2_interactivefoliage.gpu;

//? fabric && >=26.2 {

import com.github.razorplay01.sway.api.SwayAPI;
import com.github.razorplay01.sway.api.behavior.BehaviorPipeline;
import com.github.razorplay01.sway.api.behavior.contributors.MultiBlockContributor;
import com.github.razorplay01.sway.client.behavior.multiblock.HangingVineMultiblockBehavior;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the blocks that {@link GpuFoliagePrototype} removed from the chunk mesh.
 * <p>
 * Geometry comes from {@link ModelBlockRenderer#tesselateBlock}, the very method the section
 * compiler uses, so ambient occlusion, light coordinates and biome tint are produced by vanilla and
 * not reimplemented here.
 * <p>
 * It is held one buffer per chunk section, mirroring how the game stores terrain. That is what lets
 * a single section be rebuilt when something in it changes, and it means the sections to draw can
 * be taken straight from {@code LevelRenderer.visibleSections()} -- so render distance, frustum and
 * occlusion culling are inherited from vanilla, and the foliage appears and disappears in step with
 * the terrain around it.
 */
public final class GpuFoliageRenderer {

	/** Rebuilds run on the render thread, so only a few are allowed per frame. */
	private static final int REBUILD_BUDGET = 6;
	/** Padding on a section's cull box so foliage leaning into view is not culled away. */
	private static final double SWAY_MARGIN = 1.0D;
	private static final int SECTION_SIZE = 16;

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
	 * A second vertex binding carrying one float per vertex: how freely it may sway, from 0 at the
	 * anchor to 1 at the far end. Keeping it out of the main format leaves vanilla free to write
	 * the standard attributes through {@code putBlockBakedQuad}.
	 */
	private static final VertexFormat WAVE_FORMAT = VertexFormat.builder(0)
			.addAttribute("WaveWeight", GpuFormat.R32_FLOAT)
			.build();

	/**
	 * Our own pipeline, so the vertex shader can displace foliage on the GPU.
	 * <p>
	 * It inherits everything from vanilla's block snippet -- vertex format, bind groups, depth and
	 * blend state -- and only swaps in our shaders, which for now are a literal copy of vanilla's.
	 * The shader files are discovered by resource pack scanning, so no registration call is needed.
	 */
	private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "pipeline/foliage"))
			.withVertexShader(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "core/foliage"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(ModTemplate.MOD_ID, "core/foliage"))
			.withVertexBinding(1, WAVE_FORMAT)
			.withShaderDefine("ALPHA_CUTOUT", 0.5F)
			.build();

	private static final int WAVE_BUFFER_BYTES = Float.BYTES * 4 * 16384;

	private static final Map<Long, Section> SECTIONS = new HashMap<>();
	private static final Set<Long> DIRTY = new LinkedHashSet<>();

	private static final Predicate<BlockState> FOLIAGE = GpuFoliagePrototype::rendersItself;

	private static ModelBlockRenderer modelRenderer;
	/** Set when every buffer was thrown away, so the chunks already loaded get queued again. */
	private static boolean reseedPending;

	// Temporary instrumentation while the section pipeline is being brought up.
	private static long lastReport;

	private GpuFoliageRenderer() {
	}

	/** Geometry for one chunk section, its vertices relative to that section's own corner. */
	private record Section(GpuBuffer vertices, GpuBuffer weights, int indexCount, BlockPos origin, AABB bounds) {
		void close() {
			vertices.close();
			weights.close();
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
		private boolean hanging;
		private float damping = 1.0F;

		void reset() {
			dampingByAnchor.clear();
		}

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
			// Hanging plants are held at the top of their anchor block, everything else at the base.
			anchorY = hanging ? anchor.getY() + 1 : anchor.getY();
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

		float weightAt(float worldY) {
			float distance = hanging ? anchorY - worldY : worldY - anchorY;
			return Math.clamp(distance / SWAY_SPAN, 0.0F, 1.0F) * damping;
		}
	}

	public static void register() {
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(GpuFoliageRenderer::draw);
		// Sodium overwrites every vanilla route that marks a section dirty when a chunk arrives, so
		// chunk loads are followed through Fabric's own event, which fires whichever renderer is used.
		ClientChunkEvents.CHUNK_LOAD.register(GpuFoliageRenderer::discoverChunk);
		ClientChunkEvents.CHUNK_UNLOAD.register(GpuFoliageRenderer::forgetChunk);
	}

	/** Light arrived or changed in a section. Only sections already holding foliage care. */
	public static void onLightChanged(int sectionX, int sectionY, int sectionZ) {
		long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
		if (SECTIONS.containsKey(key)) {
			DIRTY.add(key);
		}
	}

	/**
	 * A block changed. The sections above and below are rebuilt with its own because a column's sway
	 * depends on its length and anchor, and a column can cross a section boundary; sideways
	 * neighbours are only touched when the block sits on that edge.
	 */
	public static void onBlockChanged(BlockPos pos) {
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
		SECTIONS.values().forEach(Section::close);
		SECTIONS.clear();
		DIRTY.clear();
		// The renderer snapshots options such as smooth lighting, which are among the things that
		// land here, so it is rebuilt from the current options on next use.
		modelRenderer = null;
		// Nothing queues the chunks that are already loaded again: with Sodium in charge a resource
		// reload rebuilds its own meshes without passing any hook. They are rediscovered on next draw.
		reseedPending = true;
	}

	/** Queues the sections of a chunk whose palette could hold foliage. */
	private static void discoverChunk(ClientLevel level, LevelChunk chunk) {
		if (!GpuFoliagePrototype.enabled) {
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

	private static void forgetChunk(ClientLevel level, LevelChunk chunk) {
		int chunkX = SectionPos.blockToSectionCoord(chunk.getPos().getMinBlockX());
		int chunkZ = SectionPos.blockToSectionCoord(chunk.getPos().getMinBlockZ());
		for (int sectionY = level.getMinSectionY(); sectionY <= level.getMaxSectionY(); sectionY++) {
			long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
			DIRTY.remove(key);
			Section stored = SECTIONS.remove(key);
			if (stored != null) {
				stored.close();
			}
		}
	}

	private static void reseedLoadedChunks(Minecraft minecraft, ClientLevel level) {
		int radius = minecraft.options.renderDistance().get() + 1;
		int centerX = SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
		int centerZ = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
		for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
			for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
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

	private static void draw(LevelTerrainRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!GpuFoliagePrototype.enabled || minecraft.level == null) {
			return;
		}

		// The render state carries the camera and the cull frustum vanilla already prepared this
		// frame, which is available whether or not Sodium owns the terrain renderer.
		CameraRenderState cameraState = context.levelState().cameraRenderState;
		if (cameraState == null || cameraState.pos == null) {
			return;
		}
		Vec3 camera = cameraState.pos;
		if (reseedPending && minecraft.player != null) {
			reseedPending = false;
			reseedLoadedChunks(minecraft, minecraft.level);
		}
		buildNearest(minecraft, camera);

		// Sodium replaces the terrain renderer and never fills vanilla's visible section list, so
		// the sections to draw are chosen here instead. Walking our own map is cheap because only
		// sections that actually contain foliage are ever stored, and dropping the ones that have
		// fallen out of range doubles as eviction so their buffers do not pile up.
		Frustum frustum = cameraState.cullFrustum;
		List<Section> visible = new ArrayList<>();
		int longestSection = 0;
		var stored = SECTIONS.entrySet().iterator();
		while (stored.hasNext()) {
			var entry = stored.next();
			Section section = entry.getValue();
			if (!withinRange(minecraft, entry.getKey())) {
				section.close();
				stored.remove();
				continue;
			}
			if (frustum != null && !frustum.isVisible(section.bounds())) {
				continue;
			}
			visible.add(section);
			longestSection = Math.max(longestSection, section.indexCount());
		}

		long now = System.currentTimeMillis();
		if (now - lastReport > 5000L) {
			lastReport = now;
			ModTemplate.LOGGER.info("Foliage GPU: stored={} drawn={} dirtyQueue={}",
					SECTIONS.size(), visible.size(), DIRTY.size());
		}

		if (visible.isEmpty()) {
			return;
		}

		// Every section's offset is written in one mapping of the uniform ring buffer. The singular
		// writeTransform maps and unmaps it per call, so calling it once per section was costing a
		// GPU round trip for each one and is what made thousands of sections crawl.
		Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
		Vector4f noModulation = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
		Matrix4f noTextureTransform = new Matrix4f();
		DynamicUniforms.Transform[] transforms = new DynamicUniforms.Transform[visible.size()];
		for (int i = 0; i < transforms.length; i++) {
			// Vertices are relative to their own section, so the offset brings it into view and
			// their values stay small however far from the world origin the section sits.
			BlockPos sectionOrigin = visible.get(i).origin();
			transforms[i] = new DynamicUniforms.Transform(
					modelView,
					noModulation,
					new Vector3f(
							(float) (sectionOrigin.getX() - camera.x),
							(float) (sectionOrigin.getY() - camera.y),
							(float) (sectionOrigin.getZ() - camera.z)),
					noTextureTransform);
		}
		GpuBufferSlice[] offsets = RenderSystem.getDynamicUniforms().writeTransforms(transforms);

		AbstractTexture atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(longestSection);
		RenderTarget target = minecraft.gameRenderer.mainRenderTarget();

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
						() -> "MC2 foliage",
						target.getColorTextureView(),
						Optional.empty(),
						target.getDepthTextureView(),
						OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
			pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setIndexBuffer(indexBuffer, indices.type());

			for (int i = 0; i < transforms.length; i++) {
				Section section = visible.get(i);
				pass.setUniform("DynamicTransforms", offsets[i]);
				pass.setVertexBuffer(0, section.vertices().slice());
				pass.setVertexBuffer(1, section.weights().slice());
				pass.drawIndexed(section.indexCount(), 1, 0, 0, 0);
			}
		}
	}

	/**
	 * Rebuilds the queued sections nearest the camera. Nearest first matters because a chunk load
	 * storm can leave thousands of entries queued, and foliage appearing late at your feet is far
	 * more noticeable than foliage appearing late on the horizon.
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

	/** Sections beyond the render distance are neither built nor kept. */
	private static boolean withinRange(Minecraft minecraft, long key) {
		if (minecraft.player == null) {
			return false;
		}
		int limit = minecraft.options.renderDistance().get() + 1;
		int dx = SectionPos.x(key) - SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
		int dz = SectionPos.z(key) - SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
		int dy = SectionPos.y(key) - SectionPos.blockToSectionCoord(minecraft.player.getBlockY());
		return Math.abs(dx) <= limit && Math.abs(dz) <= limit && Math.abs(dy) <= limit;
	}

	private static void rebuild(Minecraft minecraft, ClientLevel level, long key) {
		if (modelRenderer == null) {
			// Same arguments the section compiler passes, so the geometry matches it exactly.
			modelRenderer = new ModelBlockRenderer(
					minecraft.options.ambientOcclusion().get(),
					minecraft.options.cutoutLeaves().get(),
					minecraft.getBlockColors());
		}
		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
		BlockPos origin = new BlockPos(
				SectionPos.sectionToBlockCoord(SectionPos.x(key)),
				SectionPos.sectionToBlockCoord(SectionPos.y(key)),
				SectionPos.sectionToBlockCoord(SectionPos.z(key)));

		Section previous = SECTIONS.remove(key);
		if (previous != null) {
			previous.close();
		}
		if (!mayHoldFoliage(level, key)) {
			return;
		}

		try (ByteBufferBuilder scratch = ByteBufferBuilder.exactlySized(
				DefaultVertexFormat.BLOCK.getVertexSize() * 4 * 4096)) {
			BufferBuilder builder = new BufferBuilder(
					scratch, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
			ByteBuffer weights = ByteBuffer.allocateDirect(WAVE_BUFFER_BYTES).order(ByteOrder.nativeOrder());

			// Vanilla writes the standard attributes; alongside each quad we append the sway weight
			// of its four vertices, so both bindings stay in step without touching its output.
			SwayAnchor anchor = new SwayAnchor();
			BlockQuadOutput output = (x, y, z, quad, instance) -> {
				builder.putBlockBakedQuad(x, y, z, quad, instance);
				if (weights.remaining() >= Float.BYTES * 4) {
					for (int vertex = 0; vertex < 4; vertex++) {
						float worldY = origin.getY() + y + quad.position(vertex).y();
						weights.putFloat(anchor.weightAt(worldY));
					}
				}
			};

			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = 0; dx < SECTION_SIZE; dx++) {
				for (int dy = 0; dy < SECTION_SIZE; dy++) {
					for (int dz = 0; dz < SECTION_SIZE; dz++) {
						pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						BlockState state = level.getBlockState(pos);
						if (!GpuFoliagePrototype.rendersItself(state)) {
							continue;
						}
						anchor.prepare(state, pos, level);
						modelRenderer.tesselateBlock(
								output, dx, dy, dz, level, pos, state, models.get(state), state.getSeed(pos));
					}
				}
			}

			MeshData mesh = builder.build();
			if (mesh == null) {
				return;
			}
			try (mesh) {
				weights.flip();
				AABB bounds = new AABB(
						origin.getX() - SWAY_MARGIN,
						origin.getY() - SWAY_MARGIN,
						origin.getZ() - SWAY_MARGIN,
						origin.getX() + SECTION_SIZE + SWAY_MARGIN,
						origin.getY() + SECTION_SIZE + SWAY_MARGIN,
						origin.getZ() + SECTION_SIZE + SWAY_MARGIN);
				SECTIONS.put(key, new Section(
						RenderSystem.getDevice().createBuffer(
								() -> "MC2 foliage vertices",
								GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
								mesh.vertexBuffer()),
						RenderSystem.getDevice().createBuffer(
								() -> "MC2 foliage sway weights",
								GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
								weights),
						mesh.drawState().indexCount(),
						origin,
						bounds));
			}
		}
	}
}
//?}
