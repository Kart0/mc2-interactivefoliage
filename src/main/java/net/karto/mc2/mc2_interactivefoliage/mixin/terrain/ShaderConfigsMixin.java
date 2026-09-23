package net.karto.mc2.mc2_interactivefoliage.mixin.terrain;

//? >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.karto.mc2.mc2_interactivefoliage.gpu.TerrainFoliageShader;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Builds the foliage vertex shader out of the terrain's, where the game reads the shaders it compiles from.
 * <p>
 * From 26.3 the game loads every shader once and compiles its pipelines from what it loaded, rather than each pipeline
 * asking for its shaders as it is drawn with. So the mod's terrain shader is spliced here, once per resource reload: what
 * is asked for is the mod's own file, which is only the additions, and what is handed back is the terrain shader in use
 * -- vanilla's or a resource pack's -- with those additions in it. See {@link TerrainFoliageShader}.
 ^/
@Mixin(ShaderManager.Configs.class)
public abstract class ShaderConfigsMixin {

	// Once the sources have answered, so what they hold for the mod's own file -- the additions -- is in hand; asking
	// for it again here would come back through this very injection.
	@Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
	private void mc2$spliceFoliageShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		if (TerrainFoliageShader.isFoliageVertexShader(id, type)) {
			// Null where it cannot be spliced: the pipeline then fails to compile, which is optional and only noted, and
			// the renderer keeps to its own shaders.
			cir.setReturnValue(TerrainFoliageShader.spliced((ShaderSource) (Object) this, cir.getReturnValue()));
		}
	}
}
*///?}
