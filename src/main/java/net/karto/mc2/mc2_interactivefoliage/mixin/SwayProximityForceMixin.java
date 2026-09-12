package net.karto.mc2.mc2_interactivefoliage.mixin;

//? >=26.2 {

import com.github.razorplay01.sway.client.behavior.force.ProximityForceBehavior;
import net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageInteraction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets plants feel an entity from a little further away than its hitbox reaches.
 * <p>
 * Only the box Sway reads to work out a plant's push is changed, and it is a new box: the entity keeps its own
 * hitbox, so collisions, damage and movement are untouched, and nothing leaves this client.
 */
@Mixin(ProximityForceBehavior.class)
public abstract class SwayProximityForceMixin {

	@Redirect(method = "contributeForce", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
	private AABB mc2$plantHitbox(Entity entity) {
		return GpuFoliageInteraction.plantHitbox(entity.getBoundingBox());
	}
}
//?}
