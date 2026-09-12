package net.karto.mc2.mc2_interactivefoliage.mixin;

//? >=26.2 {

import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the mod's foliage geometry in step with the light and models it was built from.
 * <p>
 * Sodium overwrites the vanilla routes that mark a section dirty for a block change, so those are
 * followed elsewhere ({@code ClientLevelMixin}, and chunk loads through Fabric's chunk events). What
 * still reaches the public {@code setSectionDirty} is light arriving or changing, which foliage bakes
 * into its vertices. {@code allChanged} covers the wholesale cases: a resource pack reload, a world
 * swap, or a video option such as smooth lighting.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

	@Inject(method = "setSectionDirty(III)V", at = @At("TAIL"))
	private void mc2$foliageLightChanged(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
		GpuFoliageRenderer.onLightChanged(sectionX, sectionY, sectionZ);
	}

	@Inject(method = "allChanged", at = @At("TAIL"))
	private void mc2$foliageAllChanged(CallbackInfo ci) {
		GpuFoliageRenderer.discardAll();
	}
}
//?}
