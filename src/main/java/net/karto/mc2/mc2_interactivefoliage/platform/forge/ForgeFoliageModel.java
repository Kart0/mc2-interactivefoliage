package net.karto.mc2.mc2_interactivefoliage.platform.forge;

//? forge {

/*import com.github.razorplay01.sway.platform.forge.util.SwayModel;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageSplit;
import net.karto.mc2.mc2_interactivefoliage.gpu.SnowRealMagicCompat;
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
	/^* Set in the model data of the top half of a tall plant standing in Snow! Real Magic's snow. ^/
	private static final ModelProperty<Boolean> ON_SNOW = new ModelProperty<>();

	/^* The snowy variant last drawn, and Sway's wrapper around it, which bends it as it bends the plant. ^/
	private BakedModel snowyVariant;
	private BakedModel swayedSnowyVariant;

	public ForgeFoliageModel(BakedModel swayModel) {
		super(swayModel);
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		// A block whose whitelist group the player has switched off, or snow off the leaves, stays with the chunk mesh.
		if (GpuFoliageSplit.isFoliageAt(level, pos, state) && GpuFoliageSplit.leaveToGpu(level, pos)) {
			return modelData.derive().with(LEFT_TO_GPU, Boolean.TRUE).build();
		}
		ModelData data = super.getModelData(level, pos, state, modelData);
		// Snow! Real Magic draws the top half of a tall plant in its snow snowy too; only here is the position known.
		if (SnowRealMagicCompat.standsOnSnow(level, pos, state)) {
			data = data.derive().with(ON_SNOW, Boolean.TRUE).build();
		}
		return data;
	}

	/^*
	 * Whether the GPU renderer draws this plant: decided in getModelData for a plant of its own, and as Indigo started
	 * on the snow for a plant held in Snow! Real Magic's snow, which is handed the snow's data instead.
	 ^/
	private static boolean leftToGpu(ModelData data) {
		return data.has(LEFT_TO_GPU) || SnowRealMagicCompat.drawnInSnow(data) && GpuFoliageSplit.snowPlantLeftToGpu();
	}

	/^* No layer at all for a block left to the GPU renderer, so a mesher skips it outright. ^/
	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
		if (leftToGpu(data)) {
			return ChunkRenderTypeSet.none();
		}
		return super.getRenderTypes(state, rand, data);
	}

	/^* And no quads, for a mesher that asks regardless. ^/
	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
			RenderType renderType) {
		if (leftToGpu(extraData)) {
			return List.of();
		}
		// Where Snow! Real Magic draws the plant snowy. It picks its snowy variant only when asked through Fabric's
		// rendering API, which Sway's wrapper and this one do not pass on, so the variant is drawn from here -- through
		// Sway's own wrapper, so it bends as the plant would.
		if (state != null && (extraData.has(ON_SNOW) || SnowRealMagicCompat.drawnInSnow(extraData))) {
			BakedModel variant = SnowRealMagicCompat.snowyVariantOf(this);
			if (variant != null) {
				if (variant != snowyVariant) {
					swayedSnowyVariant = new SwayModel(variant);
					snowyVariant = variant;
				}
				return swayedSnowyVariant.getQuads(state, side, rand, extraData, renderType);
			}
		}
		return super.getQuads(state, side, rand, extraData, renderType);
	}
}
*///?}
