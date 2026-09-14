package net.karto.mc2.mc2_interactivefoliage.gpu;

//? >=1.20.1 {

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;

/**
 * How much of rain's wind reaches the plants of one section, worked out while the section is meshed and baked into
 * its vertices, so the shader only multiplies by it.
 * <p>
 * Shelter adds up, from 0 for none to 1 for all of it, and the plant keeps what is left of the wind:
 * <ul>
 * <li>a roof: the sky light at the plant, which a torch never raises and night never lowers. Under a roof it takes
 * half the wind, and deep inside, far from any opening, all of it;</li>
 * <li>a wall upwind: the wind blows west, as in sway.glsl, so each row of blocks is swept from east to west. A wall
 * takes half the wind from the plants behind it that are no taller than it is, as many blocks of open sky back as it
 * stands tall: the wind comes down over the top of it, so a taller plant still catches it. A wall that reaches the
 * ceiling on its downwind side seals the room behind it: the wind has no way down, so its shadow does not wear off
 * along a covered stretch, and it shelters a covered plant of any height. A wall with a gap between its top and the
 * ceiling -- a window above a sill -- lets the wind in over it, as a wall in the open does.</li>
 * </ul>
 * A row is swept once, the first time a plant on it asks, and shared by every plant along it.
 */
final class WindShelter {

	/** Sky light at which a plant counts as under a roof, and at or below which it is shut in with no wind at all. */
	private static final int SKY_ROOF = 12;
	/**
	 * Sky light at or below which a block counts as covered: the wind cannot come down onto it over a wall. A little
	 * above SKY_ROOF, so the blocks just under a roof's edge count too.
	 */
	private static final int SKY_COVERED = 13;
	private static final int SKY_ENCLOSED = 6;
	/** How much of the wind a roof takes, and how much a wall does. */
	private static final float ROOF_SHELTER = 0.5F;
	private static final float WALL_SHELTER = 0.5F;

	/** How many blocks behind it a wall shelters for every block it stands tall. */
	static final int SHADOW_PER_BLOCK = 1;
	/** Walls taller than this count as this tall, and plants taller than this are never sheltered by one. */
	static final int MAX_WALL_HEIGHT = 4;
	/**
	 * How far upwind of a section walls are looked for: past the longest shadow in the open, since under a roof a
	 * shadow carries on as far as the roof does -- across a house, say.
	 */
	static final int REACH = SectionPos.SECTION_SIZE;
	/** How far above a row a ceiling is looked for, to tell a wall that seals a room from one the wind blows over. */
	private static final int MAX_CEILING_HEIGHT = 8;
	/** In a row's entries: the tallest wall sheltering the block in its low bits, and whether a sealing wall does. */
	private static final int TALLEST_MASK = 0x7;
	private static final int SEALED_FLAG = 0x8;
	/** A collision shape at least this tall stops the wind: fences, walls, glass and leaves do; carpets and snow do not. */
	private static final double WALL_MIN_HEIGHT = 0.5D;

	private final ClientLevel level;
	private final int originX;
	private final int originZ;
	/**
	 * Along each row the section's plants stand on, by row: for each block of the section, west to east, the tallest wall
	 * sheltering it (0 for none) and whether a wall sealing it under a roof does.
	 */
	private final Int2ObjectOpenHashMap<byte[]> rows = new Int2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	WindShelter(ClientLevel level, BlockPos sectionOrigin) {
		this.level = level;
		this.originX = sectionOrigin.getX();
		this.originZ = sectionOrigin.getZ();
	}

	/**
	 * How much of the wind reaches a plant anchored at this block and this many blocks tall, from 0 for none to 1 for
	 * all of it. The anchor's column must lie inside the section; its height need not.
	 */
	float exposureAt(int x, int y, int z, int plantHeight) {
		cursor.set(x, y, z);
		int sky = level.getBrightness(LightLayer.SKY, cursor);
		// None in the open, half by the time the sky is behind a roof, all of it once the plant is shut in.
		float roof = sky >= SKY_ROOF
				? ROOF_SHELTER * ease((15.0F - sky) / (15 - SKY_ROOF))
				: ROOF_SHELTER + (1.0F - ROOF_SHELTER) * ease((float) (SKY_ROOF - sky) / (SKY_ROOF - SKY_ENCLOSED));
		int wall = row(y, z - originZ)[x - originX];
		// Under a roof a wall that seals the room leaves the wind no way down onto a taller plant.
		boolean sheltered = (wall & TALLEST_MASK) >= plantHeight
				|| ((wall & SEALED_FLAG) != 0 && sky <= SKY_COVERED);
		float shelter = roof + (sheltered ? WALL_SHELTER : 0.0F);
		return 1.0F - Math.min(1.0F, shelter);
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private byte[] row(int y, int localZ) {
		int key = y * SectionPos.SECTION_SIZE + localZ;
		byte[] row = rows.get(key);
		if (row == null) {
			row = sweep(y, originZ + localZ);
			rows.put(key, row);
		}
		return row;
	}

	/**
	 * Walks one row from REACH blocks east of the section to its west edge, the way the wind blows, keeping for each wall
	 * height how many blocks back the last wall that tall stood. A block is sheltered by the tallest of them still close
	 * enough, since a wall reaches as far back as it stands tall. The wind comes down behind a wall it blows over at
	 * every block; behind a wall sealing a room only where the sky is open, so covered blocks do not count towards it.
	 */
	private byte[] sweep(int y, int z) {
		byte[] row = new byte[SectionPos.SECTION_SIZE];
		int[] behind = new int[MAX_WALL_HEIGHT + 1];
		boolean[] sealed = new boolean[MAX_WALL_HEIGHT + 1];
		Arrays.fill(behind, Integer.MAX_VALUE / 2);
		for (int x = originX + SectionPos.SECTION_SIZE - 1 + REACH; x >= originX; x--) {
			int height = wallHeight(x, y, z);
			if (height > 0) {
				behind[height] = 0;
				// Against the ceiling on the downwind side, where the plants it shelters stand, and with the wall's full
				// height: a gable end stands taller than any wall counts for its shadow.
				sealed[height] = wallRun(x, y, z, MAX_CEILING_HEIGHT) >= ceilingHeight(x - 1, y, z);
				continue;
			}
			cursor.set(x, y, z);
			boolean open = level.getBrightness(LightLayer.SKY, cursor) > SKY_COVERED;
			for (int h = 1; h <= MAX_WALL_HEIGHT; h++) {
				if (open || !sealed[h]) {
					behind[h]++;
				}
			}
			if (x < originX + SectionPos.SECTION_SIZE) {
				int entry = 0;
				for (int h = MAX_WALL_HEIGHT; h >= 1; h--) {
					if (behind[h] <= h * SHADOW_PER_BLOCK) {
						entry = Math.max(entry & TALLEST_MASK, h) | (entry & SEALED_FLAG) | (sealed[h] ? SEALED_FLAG : 0);
					}
				}
				row[x - originX] = (byte) entry;
			}
		}
		return row;
	}

	/**
	 * How far above this block the ceiling is: the first block up that stops the wind, counting the block itself as
	 * 0. A wall at least this tall reaches the ceiling. With none within MAX_CEILING_HEIGHT the sky is open above, which
	 * no wall reaches.
	 */
	private int ceilingHeight(int x, int y, int z) {
		for (int up = 0; up <= MAX_CEILING_HEIGHT; up++) {
			if (stopsWind(x, y + up, z)) {
				return up;
			}
		}
		return Integer.MAX_VALUE;
	}

	/** How many blocks of wall stand at this block and straight above it, up to MAX_WALL_HEIGHT; 0 if it is not a wall. */
	private int wallHeight(int x, int y, int z) {
		return wallRun(x, y, z, MAX_WALL_HEIGHT);
	}

	private int wallRun(int x, int y, int z, int limit) {
		int height = 0;
		while (height < limit && stopsWind(x, y + height, z)) {
			height++;
		}
		return height;
	}

	private boolean stopsWind(int x, int y, int z) {
		cursor.set(x, y, z);
		BlockState state = level.getBlockState(cursor);
		if (state.isAir()) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, cursor);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= WALL_MIN_HEIGHT;
	}
}
//?}
