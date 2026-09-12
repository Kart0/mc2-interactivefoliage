package net.karto.mc2.mc2_interactivefoliage.mixin;

//? >=26.1.2 {

import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer;
//? >=26.2 {
import net.minecraft.client.renderer.extract.LevelExtractor;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the mod's foliage geometry in step with the light and models it was built from.
 * <p>
 * The class that marks sections dirty moved in 26.2: it was {@code LevelRenderer} and became
 * {@code LevelExtractor}. Both carry the same two methods, so only the target changes.
 * <p>
 * Sodium overwrites the vanilla routes that mark a section dirty for a block change, so those are
 * followed elsewhere ({@code ClientLevelMixin}, and chunk loads through Fabric's chunk events). What
 * still reaches the public {@code setSectionDirty} is light arriving or changing, which foliage bakes
 * into its vertices. {@code allChanged} covers the wholesale cases: a resource pack reload, a world
 * swap, or a video option such as smooth lighting.
 */
//? >=26.2 {
@Mixin(LevelExtractor.class)
//?} else {
/*@Mixin(LevelRenderer.class)
*///?}
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
