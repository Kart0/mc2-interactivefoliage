package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

import java.util.List;

/^*
 * Wraps the model of every foliage block so a chunk mesher leaves out the foliage the GPU renderer draws.
 * <p>
 * A model is not handed the block's position while its quads are asked for. It is handed it just before, in
 * {@code getModelData}, which vanilla's chunk compiler and Embeddium both call for every block. So the decision is
 * made there and carried in the model data to the calls that follow, which then hand over nothing for a block the
 * GPU renderer draws. The same approach NeoForge takes on 1.21.1, which Forge's model API is the ancestor of.
 * <p>
 * It sits outside Sway's own wrapper: Sway's {@code getModelData} does not ask the model inside it, so a wrapper in
 * there would never see the position. Everything it does not withhold goes to Sway as before. The renderer meshes
 * from the model as it was before Sway wrapped it, which the hooks keep.
 ^/
public final class ForgeFoliageModel extends BakedModelWrapper<BakedModel> {

	/^* Set in the model data of a block whose foliage the GPU renderer draws. ^/
	private static final ModelProperty<Boolean> LEFT_TO_GPU = new ModelProperty<>();

	public ForgeFoliageModel(BakedModel swayModel) {
		super(swayModel);
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		if (GpuFoliageSplit.leaveToGpu(level, pos)) {
			return modelData.derive().with(LEFT_TO_GPU, Boolean.TRUE).build();
		}
		return super.getModelData(level, pos, state, modelData);
	}

	/^* No layer at all for a block left to the GPU renderer, so a mesher skips it outright. ^/
	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
		if (data.has(LEFT_TO_GPU)) {
			return ChunkRenderTypeSet.none();
		}
		return super.getRenderTypes(state, rand, data);
	}

	/^* And no quads, for a mesher that asks regardless. ^/
	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
			RenderType renderType) {
		if (extraData.has(LEFT_TO_GPU)) {
			return List.of();
		}
		return super.getQuads(state, side, rand, extraData, renderType);
	}
}
*///?}
