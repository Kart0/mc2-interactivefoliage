package net.karto.mc2.mc2_interactivefoliage.mixin.terrain;

//? >=26.3 {
/*import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Hands the GPU foliage the render pass the terrain is drawn in, right after the opaque terrain and before anything
 * else joins it, which is where the foliage belongs: over the solid blocks and behind everything see-through.
 * <p>
 * From 26.3 one render pass is opened for the whole main pass and handed down, and no mod may open its own inside it
 * or write to a buffer while it is open. So the renderer prepares its frame where vanilla prepares the chunks it is
 * about to draw, and only draws here. Nothing else in the game hands the pass out: the loaders' rendering events carry
 * the render states but not the pass.
 ^/
@Mixin(LevelRenderer.class)
public abstract class TerrainPassMixin {

	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	// Before the world is drawn at all: nothing is open and nothing is under way, so this is where the renderer may
	// write to its buffers, have a pipeline compiled, and ask the chunk mesher to build a section again -- which is what
	// it does when foliage is handed over or taken back, and which reaches into Sodium while Sodium owns the chunks.
	// None of that can be done once the drawing has started. The terrain is drawn with the camera's rotation, which the
	// game takes from this very state.
	@Inject(method = "render", at = @At("HEAD"))
	private void mc2$prepareFoliage(GraphicsResourceAllocator allocator, boolean renderBlockOutline,
			CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky,
			boolean consistentDepthRequired, CallbackInfo ci) {
		GpuFoliageRenderer.prepareTerrainFrame(camera.viewRotationMatrix, levelRenderState);
	}

	@Inject(method = "executeSolid", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
	private void mc2$drawFoliage(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame,
			RenderPass pass, CallbackInfo ci) {
		GpuFoliageRenderer.drawIntoTerrainPass(pass);
	}
}
*///?}
