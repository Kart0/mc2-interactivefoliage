package net.karto.mc2.mc2_interactivefoliage.mixin;

//? neoforge && <1.21.11 {

/*import com.mojang.blaze3d.systems.RenderSystem;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Tells the GPU foliage renderer the moment vanilla's chunk mesh of a section is replaced by a newly built one.
 * <p>
 * {@code setCompiled} is called once every layer of the new mesh is uploaded, and from then on vanilla draws the new
 * mesh. A build that was superseded is cancelled before it gets there, so an older mesh never reports after a newer
 * build has begun.
 ^/
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SectionMeshSwapMixin {

	@Inject(method = "setCompiled", at = @At("RETURN"))
	private void mc2$foliageChunkMeshSwapped(SectionRenderDispatcher.CompiledSection compiled, CallbackInfo ci) {
		BlockPos origin = ((SectionRenderDispatcher.RenderSection) (Object) this).getOrigin();
		int sectionX = SectionPos.blockToSectionCoord(origin.getX());
		int sectionY = SectionPos.blockToSectionCoord(origin.getY());
		int sectionZ = SectionPos.blockToSectionCoord(origin.getZ());
		// A build with no layers at all to upload finishes on its meshing thread.
		if (RenderSystem.isOnRenderThread()) {
			GpuFoliageSplit.onChunkMeshSwapped(sectionX, sectionY, sectionZ);
		} else {
			GpuFoliageSplit.onChunkMeshSwappedElsewhere(sectionX, sectionY, sectionZ);
		}
	}
}
*///?}
