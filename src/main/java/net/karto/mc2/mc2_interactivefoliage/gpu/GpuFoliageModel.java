package net.karto.mc2.mc2_interactivefoliage.gpu;

//? fabric && >=1.21.11 {

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
//? >=26.1.2 {
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
//?} else {
/*import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
*///?}
//? >=26.1.2 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter;
*///?}
//? >=26.1.2 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
//?} else {
/*import net.minecraft.client.renderer.block.model.BlockStateModel;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Wraps the model of every foliage block so a chunk mesher leaves out the foliage the GPU renderer draws.
 * <p>
 * It sits outside Sway's own model wrapper, so everything it does not withhold still goes through Sway.
 * Only {@code emitQuads} is involved: it is how meshers ask for a block's geometry through Fabric's
 * rendering API, and it is the call that carries the block's position.
 */
public final class GpuFoliageModel extends WrapperBlockStateModel {

	public GpuFoliageModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
			RandomSource random, Predicate<Direction> cullTest) {
		if (GpuFoliageSplit.leaveToGpu(level, pos)) {
			return;
		}
		super.emitQuads(emitter, level, pos, state, random, cullTest);
	}
}
//?}
