package net.karto.mc2.mc2_interactivefoliage.platform.neoforge;

//? neoforge && >=1.21.11 {

/*import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
//? >=26.1.2 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
//?} else {
/^import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.BlockAndTintGetter;
^///?}
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/^*
 * Wraps the model of every foliage block so a chunk mesher leaves out the foliage the GPU renderer draws.
 * <p>
 * NeoForge has no rendering API of its own, so it hands the block's position to the model through its
 * {@code collectParts} extension, and that is the call this answers. It sits inside Sway's wrapper rather
 * than outside it: Sway deforms here too, so the renderer meshes through this model to get plain geometry,
 * which its shader then moves itself.
 * <p>
 * Everything else is passed straight through. A model carries rather more of that from 26.1.2 on, where a
 * part knows its material and the flags that go with it; before then a model only names its particle
 * texture.
 ^/
public final class NeoforgeFoliageModel implements BlockStateModel {

	private final BlockStateModel parent;

	public NeoforgeFoliageModel(BlockStateModel parent) {
		this.parent = parent;
	}

	//? >=26.1.2 {
	@Override
	public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			List<BlockStateModelPart> parts) {
		if (GpuFoliageSplit.leaveToGpu(level, pos)) {
			return;
		}
		parent.collectParts(level, pos, state, random, parts);
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
		parent.collectParts(random, parts);
	}

	@Override
	public Material.Baked particleMaterial() {
		return parent.particleMaterial();
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return parent.particleMaterial(level, pos, state);
	}

	@Override
	public int materialFlags() {
		return parent.materialFlags();
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return parent.materialFlags(level, pos, state);
	}

	@Override
	public boolean hasMaterialFlag(int flag) {
		return parent.hasMaterialFlag(flag);
	}

	@Override
	public boolean hasMaterialFlag(BlockAndTintGetter level, BlockPos pos, BlockState state, int flag) {
		return parent.hasMaterialFlag(level, pos, state, flag);
	}
	//?} else {
	/^@Override
	public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			List<BlockModelPart> parts) {
		if (GpuFoliageSplit.leaveToGpu(level, pos)) {
			return;
		}
		parent.collectParts(level, pos, state, random, parts);
	}

	@Override
	public void collectParts(RandomSource random, List<BlockModelPart> parts) {
		parent.collectParts(random, parts);
	}

	@Override
	public TextureAtlasSprite particleIcon() {
		return parent.particleIcon();
	}

	@Override
	public TextureAtlasSprite particleIcon(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return parent.particleIcon(level, pos, state);
	}
	^///?}

	@Override
	public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return parent.createGeometryKey(level, pos, state, random);
	}
}
*///?}
