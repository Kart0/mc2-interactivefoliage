package net.karto.mc2.mc2_interactivefoliage.mixin;

import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliagePrototype;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports {@link RenderShape#INVISIBLE} for blocks the mod draws itself, which is how both the
 * vanilla section compiler and Sodium are told to leave them out of the chunk mesh.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

	@Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
	private void mc2$hideWhenDrawnByMod(CallbackInfoReturnable<RenderShape> cir) {
		if (GpuFoliagePrototype.rendersItself((BlockState) (Object) this)) {
			cir.setReturnValue(RenderShape.INVISIBLE);
		}
	}
}
