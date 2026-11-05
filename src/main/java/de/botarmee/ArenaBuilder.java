package de.botarmee;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Baut Schlachtfelder direkt in die Welt – verteilt auf mehrere Ticks, damit das Spiel nicht einfriert.
 */
public final class ArenaBuilder {
	private ArenaBuilder() {}

	public static final String[] NAMES = {"Grasland", "Zwei Burgen", "Wüstenschlucht", "Schneefeld", "Kolosseum", "Nether-Ebene"};
	public static final String[] DESCRIPTIONS = {
			"Weite Wiese mit Bäumen und Felsen.",
			"Zwei Steinburgen mit Türmen.",
			"Sandtal zwischen hohen Schluchtwänden.",
			"Schnee, Tannen und gefrorene Teiche.",
			"Runde Arena – ideal für 3–4 Teams.",
			"Netherrack, Basalt und Lavaseen."
	};
	public static final String[] SIZES = {"Klein", "Mittel", "Groß"};
	private static final int[][] DIM = {{45, 25}, {65, 35}, {90, 50}};
	private static final int CLEAR = 32;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private record Placement(BlockPos pos, BlockState state) {}

	private static final class Job {
		ServerLevel level;
		ServerPlayer player;
		RandomSource rnd;
		int type, cx, cz, baseY, hx, hz;
		int x, z;
		final List<Placement> extra = new ArrayList<>();
		int extraIdx;
	}

	private static Job job;

	public static Battle.ArenaInfo build(ServerLevel level, ServerPlayer player, int type, int size) {
		type = Math.floorMod(type, NAMES.length);
		size = Math.max(0, Math.min(2, size));
		Job j = new Job();
		j.level = level;
		j.player = player;
		j.rnd = RandomSource.create();
		j.type = type;
		BlockPos p = player.blockPosition();
		j.cx = p.getX();
		j.cz = p.getZ();
		j.baseY = p.getY() - 1;
		j.hx = DIM[size][0];
		j.hz = DIM[size][1];
		if (type == 4) {
			j.hx = j.hz + 8;
			j.hz = j.hz + 8;
		}
		j.x = -j.hx;
		j.z = -j.hz;
		decorate(j);
		job = j;

		Vec3 c = new Vec3(j.cx + 0.5, j.baseY + 1, j.cz + 0.5);
		double half = type == 4 ? j.hz - 10 : type == 1 ? j.hx - 24 : j.hx - 6;
		return new Battle.ArenaInfo(c, new Vec3(1, 0, 0), half, type == 4);
	}

	public static void tick() {
		Job j = job;
		if (j == null) return;
		int budget = 260;
		while (budget-- > 0 && j.x <= j.hx) {
			column(j, j.x, j.z);
			if (++j.z > j.hz) {
				j.z = -j.hz;
				j.x++;
			}
		}
		if (j.x > j.hx) {
			int n = 5000;
			while (n-- > 0 && j.extraIdx < j.extra.size()) {
				Placement pl = j.extra.get(j.extraIdx++);
				j.level.setBlock(pl.pos(), pl.state(), FLAGS);
			}
			if (j.extraIdx >= j.extra.size()) {
				job = null;
				if (j.player != null && !j.player.isRemoved()) {
					j.player.sendSystemMessage(Component.literal("§6[Bot-Armee] §aArena fertig! §fJetzt im Menü auf \"Aufstellen\" drücken."));
				}
			}
		}
	}

	public static boolean busy() {
		return job != null;
	}

	// ------------------------------------------------------------------ Boden pro Spalte

	private static void set(Job j, int x, int y, int z, BlockState s) {
		j.level.setBlock(new BlockPos(j.cx + x, y, j.cz + z), s, FLAGS);
	}

	private static void column(Job j, int x, int z) {
		int y0 = j.baseY;
		RandomSource r = j.rnd;
		switch (j.type) {
			case 2 -> { // Wüste
				int edge = Math.abs(z) - (j.hz - 9);
				fillBase(j, x, z, Blocks.SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState());
				if (edge > 0) {
					int h = 4 + edge * 2 + r.nextInt(2);
					for (int y = 1; y <= h; y++) {
						BlockState s = (y + edge) % 5 == 0 ? Blocks.RED_SANDSTONE.defaultBlockState()
								: (y % 7 == 0 ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
						set(j, x, y0 + y, z, s);
					}
					clearAbove(j, x, z, h + 1);
				} else {
					clearAbove(j, x, z, 1);
					if (r.nextInt(220) == 0) set(j, x, y0 + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
				}
			}
			case 3 -> { // Schnee
				fillBase(j, x, z, Blocks.DIRT.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState());
				clearAbove(j, x, z, 1);
				if (r.nextInt(3) != 0) set(j, x, y0 + 1, z, Blocks.SNOW.defaultBlockState());
			}
			case 4 -> { // Kolosseum
				double d = Math.sqrt(x * x + z * z);
				double radius = j.hz - 8;
				if (d <= radius) {
					fillBase(j, x, z, Blocks.STONE.defaultBlockState(), r.nextInt(6) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.SAND.defaultBlockState());
					clearAbove(j, x, z, 1);
				} else if (d <= radius + 8) {
					int step = (int) (d - radius);
					int h = 3 + step;
					fillBase(j, x, z, Blocks.STONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
					for (int y = 1; y <= h; y++) {
						BlockState s = y == h ? Blocks.POLISHED_ANDESITE.defaultBlockState()
								: r.nextInt(8) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
						set(j, x, y0 + y, z, s);
					}
					clearAbove(j, x, z, h + 1);
				}
			}
			case 5 -> { // Nether
				BlockState top = r.nextInt(9) == 0 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
				fillBase(j, x, z, Blocks.NETHERRACK.defaultBlockState(), top);
				clearAbove(j, x, z, 1);
				if (r.nextInt(300) == 0) set(j, x, y0 + 1, z, Blocks.FIRE.defaultBlockState());
			}
			default -> { // Grasland & Burgen
				fillBase(j, x, z, Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState());
				clearAbove(j, x, z, 1);
				int k = r.nextInt(100);
				if (k < 12) set(j, x, y0 + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
				else if (k == 12) set(j, x, y0 + 1, z, Blocks.POPPY.defaultBlockState());
				else if (k == 13) set(j, x, y0 + 1, z, Blocks.DANDELION.defaultBlockState());
				else if (k == 14 && r.nextBoolean()) set(j, x, y0 + 1, z, Blocks.CORNFLOWER.defaultBlockState());
			}
		}
	}

	private static void fillBase(Job j, int x, int z, BlockState under, BlockState top) {
		for (int y = j.baseY - 4; y < j.baseY - 2; y++) set(j, x, y, z, Blocks.STONE.defaultBlockState());
		set(j, x, j.baseY - 2, z, under);
		set(j, x, j.baseY - 1, z, under);
		set(j, x, j.baseY, z, top);
	}

	private static void clearAbove(Job j, int x, int z, int from) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int y = from; y <= CLEAR; y++) set(j, x, j.baseY + y, z, air);
	}

	// ------------------------------------------------------------------ Bauten & Deko

	private static void put(Job j, int x, int y, int z, BlockState s) {
		j.extra.add(new Placement(new BlockPos(j.cx + x, j.baseY + y, j.cz + z), s));
	}

	private static void decorate(Job j) {
		RandomSource r = j.rnd;
		switch (j.type) {
			case 0 -> {
				for (int i = 0; i < j.hx / 3; i++) {
					int x = r.nextInt(j.hx * 2 + 1) - j.hx;
					int z = (j.hz - 4 - r.nextInt(7)) * (r.nextBoolean() ? 1 : -1);
					oakTree(j, x, z);
				}
				for (int i = 0; i < j.hx / 6; i++) {
					rock(j, r.nextInt(j.hx * 2 - 10) - j.hx + 5, (j.hz - 2 - r.nextInt(10)) * (r.nextBoolean() ? 1 : -1));
				}
			}
			case 1 -> {
				castle(j, -(j.hx - 11), 1);
				castle(j, j.hx - 11, -1);
				for (int i = 0; i < j.hx / 5; i++) {
					oakTree(j, r.nextInt(j.hx) - j.hx / 2, (j.hz - 3 - r.nextInt(5)) * (r.nextBoolean() ? 1 : -1));
				}
			}
			case 2 -> {
				for (int i = 0; i < j.hx / 2; i++) {
					int x = r.nextInt(j.hx * 2 - 4) - j.hx + 2;
					int z = r.nextInt((j.hz - 11) * 2) - (j.hz - 11);
					if (Math.abs(z) < 8 && r.nextInt(3) != 0) continue;
					int h = 1 + r.nextInt(3);
					for (int y = 1; y <= h; y++) put(j, x, y, z, Blocks.CACTUS.defaultBlockState());
				}
				for (int i = 0; i < 3; i++) ruin(j, r.nextInt(j.hx) - j.hx / 2, (j.hz - 14) * (i % 2 == 0 ? 1 : -1));
			}
			case 3 -> {
				for (int i = 0; i < j.hx / 2; i++) {
					int x = r.nextInt(j.hx * 2 + 1) - j.hx;
					int z = (j.hz - 3 - r.nextInt(8)) * (r.nextBoolean() ? 1 : -1);
					spruceTree(j, x, z);
				}
				pond(j, j.hx / 3, j.hz / 2 + 2, 6);
				pond(j, -j.hx / 2, -(j.hz / 2 + 1), 5);
			}
			case 4 -> {
				int radius = j.hz - 8;
				for (int i = 0; i < 4; i++) {
					double a = Math.PI / 4 + i * Math.PI / 2;
					int x = (int) Math.round(Math.cos(a) * (radius - 3));
					int z = (int) Math.round(Math.sin(a) * (radius - 3));
					for (int y = 1; y <= 6; y++) put(j, x, y, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
					put(j, x, 7, z, Blocks.CAMPFIRE.defaultBlockState());
				}
			}
			case 5 -> {
				for (int i = 0; i < j.hx / 3; i++) {
					int x = r.nextInt(j.hx * 2 - 6) - j.hx + 3;
					int z = (j.hz - 3 - r.nextInt(10)) * (r.nextBoolean() ? 1 : -1);
					int h = 4 + r.nextInt(9);
					for (int y = 1; y <= h; y++) {
						put(j, x, y, z, Blocks.BASALT.defaultBlockState());
						if (y < h - 2 && r.nextBoolean()) put(j, x + 1, y, z, Blocks.BASALT.defaultBlockState());
					}
				}
				for (int i = 0; i < 3; i++) lava(j, r.nextInt(j.hx) - j.hx / 2, (j.hz - 6) * (i % 2 == 0 ? 1 : -1), 4);
			}
			default -> {}
		}
	}

	private static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	private static void oakTree(Job j, int x, int z) {
		int h = 4 + j.rnd.nextInt(3);
		BlockState leaf = leaves(Blocks.OAK_LEAVES);
		for (int dy = h - 2; dy <= h + 1; dy++) {
			int rad = dy >= h ? 1 : 2;
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2 && j.rnd.nextBoolean()) continue;
					put(j, x + dx, dy + 1, z + dz, leaf);
				}
			}
		}
		for (int y = 1; y <= h; y++) put(j, x, y, z, Blocks.OAK_LOG.defaultBlockState());
	}

	private static void spruceTree(Job j, int x, int z) {
		int h = 6 + j.rnd.nextInt(4);
		BlockState leaf = leaves(Blocks.SPRUCE_LEAVES);
		for (int y = 3; y <= h + 1; y++) {
			int rad = Math.max(0, (h + 1 - y) / 2);
			if (y % 2 == 0 && rad > 0) rad--;
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > rad + 1) continue;
					put(j, x + dx, y, z + dz, leaf);
				}
			}
		}
		put(j, x, h + 2, z, leaf);
		for (int y = 1; y <= h; y++) put(j, x, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
		put(j, x, h + 3, z, Blocks.SNOW.defaultBlockState());
	}

	private static void rock(Job j, int x, int z) {
		int rad = 1 + j.rnd.nextInt(2);
		for (int dx = -rad; dx <= rad; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				int h = rad + 1 - Math.max(Math.abs(dx), Math.abs(dz)) + j.rnd.nextInt(2);
				for (int y = 1; y <= h; y++) {
					put(j, x + dx, y, z + dz, j.rnd.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
							: j.rnd.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
				}
			}
		}
	}

	private static void ruin(Job j, int x, int z) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx) != 3 && Math.abs(dz) != 3) continue;
				int h = 1 + j.rnd.nextInt(4);
				for (int y = 1; y <= h; y++) put(j, x + dx, y, z + dz, j.rnd.nextInt(4) == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState());
			}
		}
	}

	private static void pond(Job j, int x, int z, int rad) {
		for (int dx = -rad; dx <= rad; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				if (dx * dx + dz * dz > rad * rad) continue;
				put(j, x + dx, 1, z + dz, Blocks.AIR.defaultBlockState());
				put(j, x + dx, 0, z + dz, j.rnd.nextInt(5) == 0 ? Blocks.ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
			}
		}
	}

	private static void lava(Job j, int x, int z, int rad) {
		for (int dx = -rad - 1; dx <= rad + 1; dx++) {
			for (int dz = -rad - 1; dz <= rad + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > (rad + 1) * (rad + 1)) continue;
				if (d2 > rad * rad) put(j, x + dx, 0, z + dz, Blocks.MAGMA_BLOCK.defaultBlockState());
				else {
					put(j, x + dx, -1, z + dz, Blocks.NETHERRACK.defaultBlockState());
					put(j, x + dx, 0, z + dz, Blocks.LAVA.defaultBlockState());
				}
			}
		}
	}

	/** Burg: Mauerring mit Zinnen, vier Ecktürmen und einem Tor zur Mitte hin. dir = Richtung zur Mitte (+1/-1). */
	private static void castle(Job j, int cx, int dir) {
		int hw = 8, hd = 12, wallH = 6, towerH = 10;
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -hw; dx <= hw; dx++) {
			for (int dz = -hd; dz <= hd; dz++) {
				boolean wall = Math.abs(dx) == hw || Math.abs(dz) == hd;
				if (!wall) {
					put(j, cx + dx, 0, cz(dz), Blocks.STONE_BRICKS.defaultBlockState());
					continue;
				}
				boolean gate = dx == hw * dir && Math.abs(dz) <= 2;
				for (int y = 1; y <= wallH; y++) {
					if (gate && y <= 4) {
						put(j, cx + dx, y, dz, air);
						continue;
					}
					put(j, cx + dx, y, dz, j.rnd.nextInt(6) == 0 ? mossy : brick);
				}
				if ((dx + dz) % 2 == 0) put(j, cx + dx, wallH + 1, dz, brick);
			}
		}
		// Wehrgang innen
		for (int dx = -hw + 1; dx <= hw - 1; dx++) {
			for (int dz = -hd + 1; dz <= hd - 1; dz++) {
				if (Math.abs(dx) == hw - 1 || Math.abs(dz) == hd - 1) put(j, cx + dx, wallH - 1, dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
		}
		int[][] corners = {{-hw, -hd}, {-hw, hd}, {hw, -hd}, {hw, hd}};
		for (int[] c : corners) {
			for (int tx = -1; tx <= 1; tx++) {
				for (int tz = -1; tz <= 1; tz++) {
					for (int y = 1; y <= towerH; y++) put(j, cx + c[0] + tx, y, c[1] + tz, j.rnd.nextInt(7) == 0 ? mossy : brick);
					if ((tx + tz) % 2 == 0) put(j, cx + c[0] + tx, towerH + 1, c[1] + tz, brick);
				}
			}
			put(j, cx + c[0], towerH + 1, c[1], Blocks.TORCH.defaultBlockState());
		}
		// Fahnen-Mast
		for (int y = 1; y <= 9; y++) put(j, cx, y, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(j, cx, 10, 0, Blocks.GLOWSTONE.defaultBlockState());
	}

	private static int cz(int dz) {
		return dz;
	}
}
