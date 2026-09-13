package net.karto.mc2.mc2_interactivefoliage.mixin;

//? neoforge && <1.21.11 {

/*import net.karto.mc2.mc2_interactivefoliage.gpu.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/^*
 * Tells the GPU foliage renderer the moment Sodium's chunk mesh of a section is replaced by a newly built one.
 * <p>
 * {@code uploadResults} is where Sodium puts a finished build's geometry in place of the old, on the render thread.
 * Sodium never builds one section twice at a time and drops a result older than what is already uploaded, so the
 * results it uploads for a section always come in the order they were built.
 * <p>
 * Sodium is optional, and not on the compile classpath: the target is named, and does nothing when it is absent.
 ^/
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager", remap = false)
public abstract class SodiumMeshSwapMixin {

	@Inject(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;)V",
			at = @At("RETURN"), require = 0, remap = false)
	private void mc2$foliageChunkMeshesSwapped(@Coerce Object commandList, Collection<?> results, CallbackInfo ci) {
		SodiumBridge.onResultsUploaded(results);
	}
}
*///?}
