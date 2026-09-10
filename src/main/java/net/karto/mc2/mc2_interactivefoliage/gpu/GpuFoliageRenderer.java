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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Prototype step 2a: draws the blocks that {@link GpuFoliagePrototype} removed from the chunk mesh.
 * <p>
 * Geometry comes from {@link ModelBlockRenderer#tesselateBlock}, the very method the section
 * compiler uses, so ambient occlusion, light coordinates and biome tint are produced by vanilla and
 * not reimplemented here. The pipeline is vanilla's {@code CUTOUT_BLOCK}, whose vertex shader is
 * line-for-line the terrain one except for how the model offset is applied, so this pass should be
 * indistinguishable from the chunk mesh it replaces.
 * <p>
 * Everything here is deliberately crude: one buffer rebuilt whenever the player crosses a block
 * boundary, covering a small box around them, with no frustum culling and no reaction to block or
 * light updates. The only question it answers is whether the result looks identical -- scope comes
 * later.
 */
public final class GpuFoliageRenderer {

	private static final int RADIUS_XZ = 24;
	private static final int RADIUS_Y = 12;

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

	private static ModelBlockRenderer modelRenderer;
	private static GpuBuffer vertexBuffer;
	private static GpuBuffer waveBuffer;
	private static int indexCount;

	/** World position the buffered vertices are relative to, keeping their values small. */
	private static BlockPos origin = BlockPos.ZERO;
	private static BlockPos builtAround;

	private static final int WAVE_BUFFER_BYTES = Float.BYTES * 4 * 65536;

	private GpuFoliageRenderer() {
	}

	private static void close() {
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
		if (waveBuffer != null) {
			waveBuffer.close();
			waveBuffer = null;
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
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(context -> draw());
	}

	private static void draw() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!GpuFoliagePrototype.enabled || minecraft.level == null || minecraft.player == null) {
			return;
		}

		BlockPos around = minecraft.player.blockPosition();
		if (!around.equals(builtAround)) {
			rebuild(minecraft, minecraft.level, around);
			builtAround = around;
		}
		if (vertexBuffer == null || waveBuffer == null || indexCount == 0) {
			return;
		}

		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(
						RenderSystem.getModelViewMatrixCopy(),
						new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
						new Vector3f(
								(float) (origin.getX() - camera.x),
								(float) (origin.getY() - camera.y),
								(float) (origin.getZ() - camera.z)),
						new Matrix4f());

		AbstractTexture atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(indexCount);
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
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
			pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.setVertexBuffer(0, vertexBuffer.slice());
			pass.setVertexBuffer(1, waveBuffer.slice());
			pass.drawIndexed(indexCount, 1, 0, 0, 0);
		}
	}

	private static void rebuild(Minecraft minecraft, ClientLevel level, BlockPos centre) {
		if (modelRenderer == null) {
			// Same arguments the section compiler passes, so the geometry matches it exactly.
			modelRenderer = new ModelBlockRenderer(
					minecraft.options.ambientOcclusion().get(),
					minecraft.options.cutoutLeaves().get(),
					minecraft.getBlockColors());
		}
		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
		origin = centre;
		indexCount = 0;

		try (ByteBufferBuilder scratch = ByteBufferBuilder.exactlySized(
				DefaultVertexFormat.BLOCK.getVertexSize() * 4 * 4096)) {
			BufferBuilder builder = new BufferBuilder(
					scratch, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
			ByteBuffer weights = ByteBuffer.allocateDirect(WAVE_BUFFER_BYTES).order(ByteOrder.nativeOrder());

			// Vanilla writes the standard attributes; alongside each quad we append the sway weight
			// of its four vertices, so both bindings stay in step without touching its output.
			SwayAnchor anchor = new SwayAnchor();
			anchor.reset();
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
			for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
				for (int dy = -RADIUS_Y; dy <= RADIUS_Y; dy++) {
					for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
						pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
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
				indexCount = mesh.drawState().indexCount();
				weights.flip();
				close();
				vertexBuffer = RenderSystem.getDevice().createBuffer(
						() -> "MC2 foliage vertices",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
						mesh.vertexBuffer());
				waveBuffer = RenderSystem.getDevice().createBuffer(
						() -> "MC2 foliage sway weights",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
						weights);
			}
		}
	}
}
//?}
