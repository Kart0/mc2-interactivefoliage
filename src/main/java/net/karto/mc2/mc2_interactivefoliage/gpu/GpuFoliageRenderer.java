package net.karto.mc2.mc2_interactivefoliage.gpu;

//? fabric && >=26.2 {

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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

	private static ModelBlockRenderer modelRenderer;
	private static GpuBuffer vertexBuffer;
	private static int indexCount;

	/** World position the buffered vertices are relative to, keeping their values small. */
	private static BlockPos origin = BlockPos.ZERO;
	private static BlockPos builtAround;

	private GpuFoliageRenderer() {
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
		if (vertexBuffer == null || indexCount == 0) {
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
			pass.setPipeline(RenderPipelines.CUTOUT_BLOCK);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
			pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.setVertexBuffer(0, vertexBuffer.slice());
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
			// Same signature as BlockQuadOutput.put, so vanilla writes the vertices for us.
			BlockQuadOutput output = builder::putBlockBakedQuad;

			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
				for (int dy = -RADIUS_Y; dy <= RADIUS_Y; dy++) {
					for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
						pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
						BlockState state = level.getBlockState(pos);
						if (!GpuFoliagePrototype.rendersItself(state)) {
							continue;
						}
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
				if (vertexBuffer != null) {
					vertexBuffer.close();
				}
				vertexBuffer = RenderSystem.getDevice().createBuffer(
						() -> "MC2 foliage vertices",
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
						mesh.vertexBuffer());
			}
		}
	}
}
//?}
