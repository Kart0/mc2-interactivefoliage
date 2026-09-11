package net.karto.mc2.mc2_interactivefoliage.gpu;

//? fabric && >=26.2 {

import com.github.razorplay01.sway.client.SwayData;
import com.github.razorplay01.sway.client.SwayEngine;
import com.github.razorplay01.sway.client.behavior.multiblock.GrowingVineMultiblockBehavior;
import com.github.razorplay01.sway.client.behavior.multiblock.HangingVineMultiblockBehavior;
import com.github.razorplay01.sway.client.behavior.multiblock.SugarCaneMultiblockBehavior;
import com.github.razorplay01.sway.config.SwayConfig;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Hands Sway's plant pushes to the foliage shader, so plants near the player lean away from whoever walks
 * through them without a single section being meshed again.
 * <p>
 * Sway still decides which plants are pushed and how hard, by its own rules and settings. Its force for each
 * plant is followed here through a damped spring, so a plant leans in smoothly and rocks back when Sway lets
 * go, and is sent with the plant's anchor block: every vertex of a plant reads the same force, and the whole
 * plant leans as one piece. The shape of the bend lives in the shader.
 * <p>
 * While the GPU renderer draws the foliage, entities push plants harder and from further away than Sway's
 * settings say, so the push holds its own next to the waving. Without it, Sway's settings apply unchanged.
 */
public final class GpuFoliageInteraction {

	/** How many plant pushes are sent. The nearest win. */
	static final int MAX_CELLS = 128;

	/** The {@code FoliageInteraction} uniform block. */
	static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
			.withUniform("FoliageInteraction", UniformType.UNIFORM_BUFFER)
			.build();

	private static final int BUFFER_SIZE = bufferSize();

	/**
	 * While the GPU renderer draws the foliage, how many times wider an entity counts as when it pushes plants,
	 * along x and z. Its height is kept. Only plant pushes see it; the entity's real hitbox is never changed.
	 * <p>
	 * Sway only looks for plants within 2 blocks of an entity's centre, so beyond about 6 a player reaches no
	 * further.
	 */
	public static final double PLANT_HITBOX_SCALE = 1.5D;
	/** While the GPU renderer draws the foliage, how many times harder than Sway's own push plants are pushed. */
	private static final float PUSH_BOOST = 1.5F;
	/**
	 * How many times harder hanging and stacked plants -- vines, sugar cane and the like -- are pushed, and how
	 * much further they may lean. A long strand spreads its push over its whole length, so it needs more to
	 * look as moved as a short plant.
	 */
	private static final float TALL_PLANT_BOOST = 1.5F;
	/** The furthest Sway's intensity setting can raise how far a plant leans: the top of the config slider. */
	private static final float MAX_PUSH_LIMIT = 2.0F;

	/** How hard a plant is pulled towards Sway's push. About 1.5 swings a second. */
	private static final float CELL_STIFFNESS = 90.0F;
	/** How much of its swing a plant loses each second; lower rocks longer. */
	private static final float CELL_DAMPING = 7.0F;
	/** Below this, in both push and speed, a plant Sway has let go of counts as settled and is dropped. */
	private static final float CELL_SETTLED = 0.005F;

	/**
	 * Sway's forces by block, as it computes them each frame. Sway has no API for reading them all, so its
	 * internal map is reached by reflection. Empty if it cannot be found, and plants then simply do not react.
	 */
	private static final Map<BlockPos, SwayData> SWAY_FORCES = findSwayForces();

	/** A plant Sway is pushing, or recently was, with its spring. */
	private static final class Cell {
		final BlockPos pos;
		/** Whether this is a hanging or stacked plant, which is pushed harder. */
		final boolean tall;
		float pushX;
		float pushZ;
		float velocityX;
		float velocityZ;
		float targetX;
		float targetZ;
		boolean touched;
		double distanceSq;

		Cell(BlockPos pos, boolean tall) {
			this.pos = pos;
			this.tall = tall;
		}
	}

	private static final Map<BlockPos, Cell> CELLS = new HashMap<>();
	private static final List<Cell> NEAREST = new ArrayList<>();
	private static final Comparator<Cell> BY_DISTANCE = Comparator.comparingDouble(cell -> cell.distanceSq);

	private static final float[] CELL_POSITIONS = new float[MAX_CELLS * 4];
	private static final float[] CELL_FORCES = new float[MAX_CELLS * 4];
	/** Min then max corner of the cells sent, relative to the camera. */
	private static final float[] CELL_BOUNDS = new float[6];

	private static GpuBuffer buffer;
	private static long lastNanos;

	private GpuFoliageInteraction() {
	}

	/**
	 * The box an entity pushes plants with: its hitbox, or a wider copy of it while the GPU renderer draws the
	 * foliage. Always a separate box, so the entity's own is never changed.
	 */
	public static AABB plantHitbox(AABB hitbox) {
		if (!GpuFoliageRenderer.isActive()) {
			return hitbox;
		}
		double growX = (hitbox.maxX - hitbox.minX) * (PLANT_HITBOX_SCALE - 1.0D) * 0.5D;
		double growZ = (hitbox.maxZ - hitbox.minZ) * (PLANT_HITBOX_SCALE - 1.0D) * 0.5D;
		return hitbox.inflate(growX, 0.0D, growZ);
	}

	/** Forgets every plant, as when the world is swapped. */
	static void reset() {
		CELLS.clear();
		lastNanos = 0L;
	}

	/** Writes this frame's pushes. Render thread only, and before any render pass is opened. */
	static GpuBuffer upload(Vec3 camera) {
		long now = System.nanoTime();
		// Real time rather than game time, so a plant rocks the same at any frame rate.
		float delta = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
		lastNanos = now;

		Arrays.fill(CELL_POSITIONS, 0.0F);
		Arrays.fill(CELL_FORCES, 0.0F);
		Arrays.fill(CELL_BOUNDS, 0.0F);
		int cellCount = 0;
		// Sway's own interaction switch turns it off.
		if (SwayConfig.INSTANCE.enabled) {
			followSway(delta, Minecraft.getInstance().level);
			cellCount = collectCells(camera);
		} else {
			CELLS.clear();
		}

		if (buffer == null) {
			buffer = RenderSystem.getDevice().createBuffer(
					() -> "MC2 foliage interaction",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					BUFFER_SIZE);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Std140Builder data = Std140Builder.onStack(stack, BUFFER_SIZE)
					.putInt(cellCount)
					.putVec4(CELL_BOUNDS[0], CELL_BOUNDS[1], CELL_BOUNDS[2], 0.0F)
					.putVec4(CELL_BOUNDS[3], CELL_BOUNDS[4], CELL_BOUNDS[5], 0.0F);
			putAll(data, CELL_POSITIONS);
			putAll(data, CELL_FORCES);
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data.get());
		}
		return buffer;
	}

	/** Moves every plant's spring towards Sway's current push, or back to upright once Sway lets go. */
	private static void followSway(float delta, ClientLevel level) {
		for (Cell cell : CELLS.values()) {
			cell.touched = false;
			cell.targetX = 0.0F;
			cell.targetZ = 0.0F;
		}
		for (Map.Entry<BlockPos, SwayData> entry : SWAY_FORCES.entrySet()) {
			SwayData force = entry.getValue();
			if (force.intensity < 0.01F) {
				continue;
			}
			Cell cell = CELLS.get(entry.getKey());
			if (cell == null) {
				BlockPos pos = entry.getKey().immutable();
				// Resolved once: a plant rarely changes kind while it is being pushed.
				cell = new Cell(pos, level != null && isTallPlant(level.getBlockState(pos)));
				CELLS.put(cell.pos, cell);
			}
			float boost = PUSH_BOOST * (cell.tall ? TALL_PLANT_BOOST : 1.0F);
			cell.touched = true;
			cell.targetX = force.nx * force.intensity * boost;
			cell.targetZ = force.nz * force.intensity * boost;
		}

		Iterator<Cell> iterator = CELLS.values().iterator();
		while (iterator.hasNext()) {
			Cell cell = iterator.next();
			// Velocity first, then position: stable at every frame time this sees, which is capped at 0.1 s.
			cell.velocityX += ((cell.targetX - cell.pushX) * CELL_STIFFNESS - cell.velocityX * CELL_DAMPING) * delta;
			cell.velocityZ += ((cell.targetZ - cell.pushZ) * CELL_STIFFNESS - cell.velocityZ * CELL_DAMPING) * delta;
			cell.pushX += cell.velocityX * delta;
			cell.pushZ += cell.velocityZ * delta;
			if (!cell.touched
					&& Math.abs(cell.pushX) < CELL_SETTLED && Math.abs(cell.pushZ) < CELL_SETTLED
					&& Math.abs(cell.velocityX) < CELL_SETTLED && Math.abs(cell.velocityZ) < CELL_SETTLED) {
				iterator.remove();
			}
		}
	}

	/** Hanging and stacked plants: the ones Sway treats as strands of several blocks. */
	private static boolean isTallPlant(BlockState state) {
		return HangingVineMultiblockBehavior.isHangingVine(state)
				|| GrowingVineMultiblockBehavior.isGrowingVine(state)
				|| SugarCaneMultiblockBehavior.isStackable(state);
	}

	/**
	 * The plants nearest the camera, with the box around them. Returns how many.
	 * <p>
	 * Each is sent with the most it may lean. That limit follows Sway's intensity setting once it is above 1, so
	 * raising the setting pushes plants further; at 1 and below, plants lean no further than they always have.
	 * It stops at the top of the config screen's slider: Sway accepts more from a hand-edited file, which would
	 * push plants out past the room the renderer leaves for them when culling.
	 */
	private static int collectCells(Vec3 camera) {
		float limit = Math.clamp(SwayConfig.INSTANCE.intensity, 1.0F, MAX_PUSH_LIMIT);
		NEAREST.clear();
		for (Cell cell : CELLS.values()) {
			double dx = cell.pos.getX() + 0.5D - camera.x;
			double dy = cell.pos.getY() + 0.5D - camera.y;
			double dz = cell.pos.getZ() + 0.5D - camera.z;
			cell.distanceSq = dx * dx + dy * dy + dz * dz;
			NEAREST.add(cell);
		}
		NEAREST.sort(BY_DISTANCE);

		int count = Math.min(NEAREST.size(), MAX_CELLS);
		for (int i = 0; i < count; i++) {
			Cell cell = NEAREST.get(i);
			float x = (float) (cell.pos.getX() - camera.x);
			float y = (float) (cell.pos.getY() - camera.y);
			float z = (float) (cell.pos.getZ() - camera.z);
			CELL_POSITIONS[i * 4] = x;
			CELL_POSITIONS[i * 4 + 1] = y;
			CELL_POSITIONS[i * 4 + 2] = z;
			CELL_FORCES[i * 4] = cell.pushX;
			CELL_FORCES[i * 4 + 1] = cell.pushZ;
			CELL_FORCES[i * 4 + 2] = limit * (cell.tall ? TALL_PLANT_BOOST : 1.0F);
			if (i == 0) {
				CELL_BOUNDS[0] = CELL_BOUNDS[3] = x;
				CELL_BOUNDS[1] = CELL_BOUNDS[4] = y;
				CELL_BOUNDS[2] = CELL_BOUNDS[5] = z;
			} else {
				CELL_BOUNDS[0] = Math.min(CELL_BOUNDS[0], x);
				CELL_BOUNDS[1] = Math.min(CELL_BOUNDS[1], y);
				CELL_BOUNDS[2] = Math.min(CELL_BOUNDS[2], z);
				CELL_BOUNDS[3] = Math.max(CELL_BOUNDS[3], x);
				CELL_BOUNDS[4] = Math.max(CELL_BOUNDS[4], y);
				CELL_BOUNDS[5] = Math.max(CELL_BOUNDS[5], z);
			}
		}
		return count;
	}

	@SuppressWarnings("unchecked")
	private static Map<BlockPos, SwayData> findSwayForces() {
		try {
			Field current = SwayEngine.class.getDeclaredField("CURRENT");
			current.setAccessible(true);
			return (Map<BlockPos, SwayData>) current.get(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			ModTemplate.LOGGER.warn("Could not read Sway's forces; foliage drawn on the GPU will not react to entities", e);
			return Map.of();
		}
	}

	private static void putAll(Std140Builder data, float[] vectors) {
		for (int i = 0; i < vectors.length; i += 4) {
			data.putVec4(vectors[i], vectors[i + 1], vectors[i + 2], vectors[i + 3]);
		}
	}

	/** Matches the block in the shader: a count, two bounds, then positions and forces. */
	private static int bufferSize() {
		Std140SizeCalculator size = new Std140SizeCalculator().putInt().putVec4().putVec4();
		for (int i = 0; i < MAX_CELLS * 2; i++) {
			size.putVec4();
		}
		return size.get();
	}
}
//?}
