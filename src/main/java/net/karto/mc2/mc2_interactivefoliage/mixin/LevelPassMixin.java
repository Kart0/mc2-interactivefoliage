package net.karto.mc2.mc2_interactivefoliage.mixin;

//? >=26.3 {

import com.mojang.renderpearl.api.commands.RenderPass;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the GPU foliage the render pass vanilla draws the opaque world in.
 * <p>
 * From 26.3 the opaque terrain and the solid features are drawn inside one pass vanilla opens itself, and nothing
 * outside it is handed that pass. The foliage goes in once the opaque terrain is drawn and before the solid features,
 * the moment Fabric's opaque-terrain event marks. Everything it needs was written while the frame was prepared; here
 * it is only bound and drawn.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelPassMixin {

	@Inject(method = "executeSolid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
	private void mc2$drawFoliage(ChunkSectionsToRender chunkSectionsToRender, FeatureRenderDispatcher.PreparedFrame featureFrame,
			RenderPass renderPass, CallbackInfo ci) {
		GpuFoliageRenderer.drawIntoLevelPass(renderPass);
	}
}
//?}
