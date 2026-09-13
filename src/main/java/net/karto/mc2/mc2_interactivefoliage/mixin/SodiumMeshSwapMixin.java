package net.karto.mc2.mc2_interactivefoliage.mixin;

//? >=1.21.1 {

import net.karto.mc2.mc2_interactivefoliage.gpu.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * Tells the GPU foliage renderer the moment Sodium's chunk mesh of a section is replaced by a newly built one.
 * <p>
 * {@code uploadResults} is where Sodium puts a finished build's geometry in place of the old, on the render thread.
 * Sodium never builds one section twice at a time and drops a result older than what is already uploaded, so the
 * results it uploads for a section always come in the order they were built.
 * <p>
 * The method is named with its parameters: a private overload of the same name uploads each region's share, and
 * would report every section twice. Its parameters differ between Sodium's release lines.
 * <p>
 * Sodium is optional, and not on the compile classpath: the target is named, and does nothing when it is absent.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager", remap = false)
public abstract class SodiumMeshSwapMixin {

	//? >=26.2 {
	@Inject(method = "uploadResults(Ljava/util/Collection;Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V",
			at = @At("RETURN"), require = 0, remap = false)
	private void mc2$foliageChunkMeshesSwapped(Collection<?> results, @Coerce Object uniforms, CallbackInfo ci) {
		SodiumBridge.onResultsUploaded(results);
	}
	//?} elif >=26.1.2 {
	/*@Inject(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V",
			at = @At("RETURN"), require = 0, remap = false)
	private void mc2$foliageChunkMeshesSwapped(@Coerce Object commandList, Collection<?> results, @Coerce Object uniforms,
			CallbackInfo ci) {
		SodiumBridge.onResultsUploaded(results);
	}
	*///?} else {
	/*@Inject(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;)V",
			at = @At("RETURN"), require = 0, remap = false)
	private void mc2$foliageChunkMeshesSwapped(@Coerce Object commandList, Collection<?> results, CallbackInfo ci) {
		SodiumBridge.onResultsUploaded(results);
	}
	*///?}
}
//?}
