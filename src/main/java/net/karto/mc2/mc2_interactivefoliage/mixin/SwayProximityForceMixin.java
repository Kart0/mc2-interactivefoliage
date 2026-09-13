package net.karto.mc2.mc2_interactivefoliage.mixin;

//? >=1.21.1 || fabric {

import com.github.razorplay01.sway.client.behavior.force.ProximityForceBehavior;
import com.github.razorplay01.sway.config.SwayConfig;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageInteraction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;

/**
 * Makes entities push plants from a little further away, and a little harder, than Sway does by itself.
 * <p>
 * Only what Sway reads while working out a plant's push is changed. The box is a new one, so the entity keeps
 * its own hitbox and collisions, damage and movement are untouched; and the intensity is raised only as that
 * one read sees it, so the setting stays as the player chose it. Nothing leaves this client.
 */
@Mixin(ProximityForceBehavior.class)
public abstract class SwayProximityForceMixin {

	@Redirect(method = "contributeForce", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
	private AABB mc2$plantHitbox(Entity entity) {
		return GpuFoliageInteraction.plantHitbox(entity.getBoundingBox());
	}

	/** The intensity Sway pushes with. Raising it here moves plants alike in the chunk mesh and on the GPU. */
	@Redirect(method = "contributeForce", at = @At(value = "FIELD",
			target = "Lcom/github/razorplay01/sway/config/SwayConfig;intensity:F", opcode = Opcodes.GETFIELD))
	private float mc2$plantPushIntensity(SwayConfig config) {
		return GpuFoliageInteraction.plantPushIntensity(config.intensity);
	}
}
//?}
