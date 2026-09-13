package net.karto.mc2.mc2_interactivefoliage.platform.neoforge;

//? neoforge && >=1.21.1 {

/*import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
//? <1.21.11 {
/^import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
^///?}
//? >=26.1.2 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
//?} elif >=1.21.11 {
/^import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.BlockAndTintGetter;
^///?}
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

//? >=1.21.11 {
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
	//?} elif >=1.21.11 {
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
//?} else {
/^// Wraps the model of every foliage block so a chunk mesher leaves out the foliage the GPU renderer draws.
//
// Before 1.21.11 a model is not handed the block's position while its quads are asked for. It is handed it
// just before, in getModelData, which vanilla's chunk compiler and Sodium both call for every block. So the
// decision is made there and carried in the model data to the calls that follow, which then hand over
// nothing for a block the GPU renderer draws.
//
// It sits outside Sway's own wrapper, unlike on newer versions: Sway's getModelData does not ask the model
// inside it, so a wrapper in there would never see the position. Everything it does not withhold goes to
// Sway as before. The renderer meshes from the model as it was before Sway wrapped it, which the hooks keep.
public final class NeoforgeFoliageModel extends BakedModelWrapper<BakedModel> {

	// Set in the model data of a block whose foliage the GPU renderer draws.
	private static final ModelProperty<Boolean> LEFT_TO_GPU = new ModelProperty<>();

	public NeoforgeFoliageModel(BakedModel swayModel) {
		super(swayModel);
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		if (GpuFoliageSplit.leaveToGpu(level, pos)) {
			return modelData.derive().with(LEFT_TO_GPU, Boolean.TRUE).build();
		}
		return super.getModelData(level, pos, state, modelData);
	}

	// No layer at all for a block left to the GPU renderer, so a mesher skips it outright.
	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
		if (data.has(LEFT_TO_GPU)) {
			return ChunkRenderTypeSet.none();
		}
		return super.getRenderTypes(state, rand, data);
	}

	// And no quads, for a mesher that asks regardless.
	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
			RenderType renderType) {
		if (extraData.has(LEFT_TO_GPU)) {
			return List.of();
		}
		return super.getQuads(state, side, rand, extraData, renderType);
	}
}
^///?}
*///?}
