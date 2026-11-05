package de.botarmee;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Baut Schlachtfelder direkt in die Welt – verteilt auf mehrere Ticks, damit das Spiel nicht einfriert.
 */
public final class ArenaBuilder {
	private ArenaBuilder() {}

	public static final String[] NAMES = {"Grasland", "Zwei Burgen", "Wüstenschlucht", "Schneefeld", "Kolosseum", "Nether-Ebene", "Dorf"};
	public static final String[] DESCRIPTIONS = {
			"Weite Wiese mit Bäumen und Felsen.",
			"Zwei Steinburgen mit Türmen.",
			"Sandtal zwischen hohen Schluchtwänden.",
			"Schnee, Tannen und gefrorene Teiche.",
			"Runde Arena – ideal für 3–4 Teams.",
			"Netherrack, Basalt und Lavaseen.",
			"Fachwerkhäuser, Marktplatz, Kirche."
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
		ArenaProtection.add(level, j.cx - j.hx, j.baseY - 5, j.cz - j.hz, j.cx + j.hx, j.baseY + CLEAR + 6, j.cz + j.hz);

		Vec3 c = new Vec3(j.cx + 0.5, j.baseY + 1, j.cz + 0.5);
		// wie weit die Armeen höchstens von der Mitte stehen dürfen (bis kurz vor Burgmauer / Tribüne)
		double half = switch (type) {
			case 1 -> j.hx - 22; // Burgmauern
			case 2 -> j.hx - 14; // Oasen
			case 3 -> j.hx - 11; // Iglus
			case 4 -> j.hz - 11; // Tribüne
			case 5 -> j.hx - 13;
			case 6 -> j.hx - 12; // Häuser am Ortsrand
			default -> j.hx - 9; // Zelte
		};
		List<Vec3> perches = new ArrayList<>();
		if (type == 4) {
			for (int[] pc : platformCenters(j)) perches.add(new Vec3(j.cx + pc[0] + 0.5, j.baseY + 4, j.cz + pc[1] + 0.5));
		}
		return new Battle.ArenaInfo(c, new Vec3(1, 0, 0), half, type == 4, perches);
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
					int step = Math.min(7, (int) (d - radius));
					int h = 3 + step;
					boolean gate = Math.abs(x) <= 1 || Math.abs(z) <= 1; // 4 Eingänge
					fillBase(j, x, z, Blocks.STONE.defaultBlockState(), gate ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
					Direction out = Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.EAST : Direction.WEST) : (z > 0 ? Direction.SOUTH : Direction.NORTH);
					for (int y = 1; y <= h; y++) {
						BlockState s;
						if (gate && y <= 3) {
							// Tunnel durch die Tribüne – innen mit einem Gitter verschlossen
							s = step == 0 ? bars(out) : Blocks.AIR.defaultBlockState();
						} else if (gate && y == 4 && step == 7) {
							s = Blocks.CHISELED_STONE_BRICKS.defaultBlockState(); // Torbogen außen
						} else if (y == h) {
							s = step >= 1 && step <= 6 && step % 2 == 1
									? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, out) // Sitzreihe
									: Blocks.POLISHED_ANDESITE.defaultBlockState();
						} else {
							s = r.nextInt(8) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
									: r.nextInt(10) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
						}
						set(j, x, y0 + y, z, s);
					}
					int top = h + 1;
					if (step == 7) {
						// Außenmauer mit Zinnen und Laternen
						set(j, x, y0 + h + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
						boolean crenel = Math.floorMod(x + z, 2) == 0;
						if (crenel) set(j, x, y0 + h + 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
						if (crenel && Math.floorMod(x * 7 + z * 13, 11) == 0) set(j, x, y0 + h + 3, z, Blocks.LANTERN.defaultBlockState());
						top = h + (crenel ? 4 : 2);
					}
					clearAbove(j, x, z, top);
				}
			}
			case 5 -> { // Nether
				BlockState top = r.nextInt(9) == 0 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
				fillBase(j, x, z, Blocks.NETHERRACK.defaultBlockState(), top);
				clearAbove(j, x, z, 1);
				if (r.nextInt(300) == 0) set(j, x, y0 + 1, z, Blocks.FIRE.defaultBlockState());
			}
			case 6 -> { // Dorf: Straßen und Marktplatz
				fillBase(j, x, z, Blocks.DIRT.defaultBlockState(), villageGround(j, x, z));
				clearAbove(j, x, z, 1);
				boolean road = Math.abs(z) <= 3 || Math.abs(x) <= 2 || x * x + z * z <= 110;
				if (!road) {
					int k = r.nextInt(100);
					if (k < 8) set(j, x, y0 + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
					else if (k == 8) set(j, x, y0 + 1, z, Blocks.POPPY.defaultBlockState());
					else if (k == 9) set(j, x, y0 + 1, z, Blocks.OXEYE_DAISY.defaultBlockState());
				}
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

	static int[][] platformCenters(Job j) {
		int radius = j.hz - 8;
		int d = (int) Math.round((radius - 7) / Math.sqrt(2));
		return new int[][] {{d, d}, {-d, d}, {d, -d}, {-d, -d}};
	}

	/** 5x5-Podest, 3 Blöcke hoch, Geländer, Laternen, Treppe zur Mitte hin. */
	private static void platform(Job j, int px, int pz) {
		int sx = px > 0 ? 1 : -1;
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				put(j, px + dx, 1, pz + dz, edge ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : brick);
				put(j, px + dx, 2, pz + dz, edge && (dx + dz) % 2 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : brick);
				put(j, px + dx, 3, pz + dz, Blocks.POLISHED_ANDESITE.defaultBlockState());
				// Geländer außer an der Treppenseite
				boolean stairSide = dx == -2 * sx && Math.abs(dz) <= 1;
				if (edge && !stairSide) put(j, px + dx, 4, pz + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
			}
		}
		for (int[] c : new int[][] {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}}) {
			if (c[0] == -2 * sx) continue;
			put(j, px + c[0], 5, pz + c[1], Blocks.LANTERN.defaultBlockState());
		}
		// Treppe zur Arena-Mitte
		Direction up = sx > 0 ? Direction.EAST : Direction.WEST;
		BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up);
		for (int dz = -1; dz <= 1; dz++) {
			put(j, px - 3 * sx, 1, pz + dz, brick);
			put(j, px - 3 * sx, 2, pz + dz, stair);
			put(j, px - 4 * sx, 1, pz + dz, stair);
		}
		// Fahne in der Mitte
		put(j, px + sx, 4, pz, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(j, px + sx, 5, pz, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(j, px + sx, 6, pz, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(j, px + sx, 7, pz, Blocks.CAMPFIRE.defaultBlockState());
	}

	private static BlockState bars(Direction out) {
		boolean alongX = out == Direction.NORTH || out == Direction.SOUTH; // Gitter quer zum Tunnel
		return Blocks.IRON_BARS.defaultBlockState()
				.setValue(CrossCollisionBlock.EAST, alongX).setValue(CrossCollisionBlock.WEST, alongX)
				.setValue(CrossCollisionBlock.NORTH, !alongX).setValue(CrossCollisionBlock.SOUTH, !alongX);
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
				// Feldlager an beiden Enden (Zelte in Teamfarbe, Lagerfeuer, Heu)
				camp(j, -(j.hx - 5), Blocks.WOOL.red().defaultBlockState());
				camp(j, j.hx - 5, Blocks.WOOL.blue().defaultBlockState());
				// Trampelpfad und Blumenwiesen
				for (int x = -j.hx + 2; x <= j.hx - 2; x++) {
					for (int z = -1; z <= 1; z++) if (r.nextInt(3) != 0) put(j, x, 0, z, Blocks.DIRT_PATH.defaultBlockState());
				}
				for (int i = 0; i < 6; i++) flowers(j, r.nextInt(j.hx * 2 - 20) - j.hx + 10, (j.hz - 9 - r.nextInt(5)) * (r.nextBoolean() ? 1 : -1));
			}
			case 1 -> {
				castle(j, -(j.hx - 11), 1, Blocks.WOOL.red().defaultBlockState());
				castle(j, j.hx - 11, -1, Blocks.WOOL.blue().defaultBlockState());
				// gepflasterte Straße zwischen den Toren
				for (int x = -(j.hx - 19); x <= j.hx - 19; x++) {
					for (int z = -2; z <= 2; z++) {
						BlockState road = Math.abs(z) == 2 ? Blocks.COBBLESTONE.defaultBlockState()
								: r.nextInt(4) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
						if (Math.abs(z) < 2 || r.nextInt(3) != 0) put(j, x, 0, z, road);
					}
				}
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
				// Oasen mit Palmen hinter beiden Armeen
				oasis(j, -(j.hx - 8), 0);
				oasis(j, j.hx - 8, 0);
				// Fossilien und ein alter Brunnen
				for (int i = 0; i < 4; i++) fossil(j, r.nextInt(j.hx * 2 - 20) - j.hx + 10, (j.hz - 12) * (i % 2 == 0 ? 1 : -1));
				well(j, 0, -(j.hz - 13), Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
			}
			case 3 -> {
				for (int i = 0; i < j.hx / 2; i++) {
					int x = r.nextInt(j.hx * 2 + 1) - j.hx;
					int z = (j.hz - 3 - r.nextInt(8)) * (r.nextBoolean() ? 1 : -1);
					spruceTree(j, x, z);
				}
				pond(j, j.hx / 3, j.hz / 2 + 2, 6);
				pond(j, -j.hx / 2, -(j.hz / 2 + 1), 5);
				// Iglus als Lager, Schneemänner, Laternen-Pfosten
				igloo(j, -(j.hx - 6), 0);
				igloo(j, j.hx - 6, 0);
				for (int i = 0; i < 5; i++) snowman(j, r.nextInt(j.hx * 2 - 16) - j.hx + 8, (j.hz - 8 - r.nextInt(4)) * (r.nextBoolean() ? 1 : -1));
				for (int x = -j.hx + 12; x <= j.hx - 12; x += 12) {
					for (int sgn = -1; sgn <= 1; sgn += 2) {
						put(j, x, 1, sgn * (j.hz - 6), Blocks.SPRUCE_FENCE.defaultBlockState());
						put(j, x, 2, sgn * (j.hz - 6), Blocks.SPRUCE_FENCE.defaultBlockState());
						put(j, x, 3, sgn * (j.hz - 6), Blocks.LANTERN.defaultBlockState());
					}
				}
			}
			case 4 -> {
				int radius = j.hz - 8;
				// vier erhöhte Podeste mit Treppe – dort stehen die Bogenschützen
				for (int[] pc : platformCenters(j)) platform(j, pc[0], pc[1]);
				// Kaiserloge an der Nordseite
				int lz = -(radius + 2);
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = 0; dz >= -3; dz--) {
						for (int y = 1; y <= 8; y++) put(j, dx, y, lz + dz, y == 8 ? Blocks.POLISHED_GRANITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
						put(j, dx, 9, lz + dz, Math.abs(dx) <= 1 ? Blocks.CARPET.red().defaultBlockState() : Blocks.AIR.defaultBlockState());
						put(j, dx, 10, lz + dz, Blocks.AIR.defaultBlockState());
						put(j, dx, 11, lz + dz, Blocks.AIR.defaultBlockState());
						put(j, dx, 12, lz + dz, Blocks.WOOL.red().defaultBlockState());
					}
				}
				for (int dx = -3; dx <= 3; dx += 6) {
					for (int y = 9; y <= 11; y++) put(j, dx, y, lz, Blocks.GOLD_BLOCK.defaultBlockState());
				}
				put(j, 0, 9, lz - 3, Blocks.QUARTZ_BLOCK.defaultBlockState()); // Thron
				put(j, 0, 13, lz - 1, Blocks.GOLD_BLOCK.defaultBlockState());
				// Laternen an den Toren
				for (int i = 0; i < 4; i++) {
					Direction d = Direction.from2DDataValue(i);
					int gx = d.getStepX() * (radius + 1), gz = d.getStepZ() * (radius + 1);
					int sx = d.getStepZ(), sz = d.getStepX(); // seitlich vom Tor
					put(j, gx + 2 * sx, 5, gz + 2 * sz, Blocks.LANTERN.defaultBlockState());
					put(j, gx - 2 * sx, 5, gz - 2 * sz, Blocks.LANTERN.defaultBlockState());
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
					if (r.nextInt(3) == 0) put(j, x, h + 1, z, Blocks.GLOWSTONE.defaultBlockState());
				}
				for (int i = 0; i < 3; i++) lava(j, r.nextInt(j.hx) - j.hx / 2, (j.hz - 6) * (i % 2 == 0 ? 1 : -1), 4);
				// Karmesin- und Wirrwald-Flecken, Seelenfeuer, Ruinen einer Netherfestung
				netherPatch(j, -(j.hx - 10), 0, Blocks.CRIMSON_NYLIUM.defaultBlockState(), Blocks.CRIMSON_FUNGUS.defaultBlockState(), Blocks.NETHER_WART_BLOCK.defaultBlockState());
				netherPatch(j, j.hx - 10, 0, Blocks.WARPED_NYLIUM.defaultBlockState(), Blocks.WARPED_FUNGUS.defaultBlockState(), Blocks.WARPED_WART_BLOCK.defaultBlockState());
				for (int i = 0; i < 12; i++) {
					int x = r.nextInt(j.hx * 2 - 10) - j.hx + 5, z = (j.hz - 4 - r.nextInt(8)) * (r.nextBoolean() ? 1 : -1);
					put(j, x, 0, z, Blocks.SOUL_SOIL.defaultBlockState());
					put(j, x, 1, z, Blocks.SOUL_FIRE.defaultBlockState());
				}
				fortressRuin(j, -j.hx / 3, -(j.hz - 5));
				fortressRuin(j, j.hx / 3, j.hz - 5);
			}
			case 6 -> village(j);
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
	private static void castle(Job j, int cx, int dir, BlockState flag) {
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
		// Fahnen-Mast mit Fahne in Teamfarbe
		for (int y = 1; y <= 12; y++) put(j, cx, y, -6, Blocks.SPRUCE_FENCE.defaultBlockState());
		for (int y = 9; y <= 11; y++) {
			for (int dz = 1; dz <= 4; dz++) put(j, cx, y, -6 + dz, flag);
		}
		// Fahnen auf den Türmen
		for (int[] c : corners) {
			put(j, cx + c[0], towerH + 2, c[1], Blocks.SPRUCE_FENCE.defaultBlockState());
			put(j, cx + c[0], towerH + 3, c[1], flag);
		}
		// Laternen auf den Zinnen
		for (int dz = -hd + 4; dz <= hd - 4; dz += 4) {
			put(j, cx + hw * dir, wallH + 2, dz, Blocks.LANTERN.defaultBlockState());
			put(j, cx - hw * dir, wallH + 2, dz, Blocks.LANTERN.defaultBlockState());
		}
		// Brunnen, Fässer und Heu im Hof
		well(j, cx - 3 * dir, 4, Blocks.COBBLESTONE.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
		for (int i = 0; i < 4; i++) {
			put(j, cx - (hw - 1) * dir, 1, -hd + 2 + i, i % 2 == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState());
			if (i < 2) put(j, cx - (hw - 1) * dir, 2, -hd + 2 + i, Blocks.HAY_BLOCK.defaultBlockState());
		}
		// Torbogen-Laternen
		put(j, cx + hw * dir, 5, 3, Blocks.LANTERN.defaultBlockState());
		put(j, cx + hw * dir, 5, -3, Blocks.LANTERN.defaultBlockState());
	}

	/** Feldlager: zwei Zelte in Teamfarbe, Lagerfeuer, Heuballen. */
	private static void camp(Job j, int x, BlockState wool) {
		BlockState white = Blocks.WOOL.white().defaultBlockState();
		for (int t = -1; t <= 1; t += 2) {
			int tz = t * 7;
			for (int dx = -2; dx <= 2; dx++) {
				boolean end = Math.abs(dx) == 2;
				for (int w = -2; w <= 2; w++) {
					int roof = 3 - Math.abs(w);
					put(j, x + dx, roof, tz + w, end && Math.abs(w) < 2 ? white : wool);
					for (int yy = 1; yy < roof; yy++) {
						// Giebelwände weiß, mit Eingang in der Mitte
						put(j, x + dx, yy, tz + w, end && w != 0 ? white : Blocks.AIR.defaultBlockState());
					}
				}
			}
			put(j, x, 4, tz, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		put(j, x, 1, 0, Blocks.CAMPFIRE.defaultBlockState());
		for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) put(j, x + o[0], 0, o[1], Blocks.COBBLESTONE.defaultBlockState());
		put(j, x + 2, 1, 2, Blocks.HAY_BLOCK.defaultBlockState());
		put(j, x + 2, 1, 3, Blocks.HAY_BLOCK.defaultBlockState());
		put(j, x + 2, 2, 2, Blocks.HAY_BLOCK.defaultBlockState());
		put(j, x - 2, 1, -2, Blocks.BARREL.defaultBlockState());
	}

	private static void flowers(Job j, int x, int z) {
		Block[] f = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM};
		for (int i = 0; i < 14; i++) {
			int dx = j.rnd.nextInt(7) - 3, dz = j.rnd.nextInt(5) - 2;
			put(j, x + dx, 1, z + dz, f[j.rnd.nextInt(f.length)].defaultBlockState());
		}
	}

	private static void well(Job j, int x, int z, BlockState rim, BlockState roof) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					put(j, x, 0, z, Blocks.WATER.defaultBlockState());
					put(j, x, -1, z, Blocks.WATER.defaultBlockState());
				} else {
					put(j, x + dx, 1, z + dz, rim);
				}
				put(j, x + dx, 4, z + dz, roof);
			}
		}
		for (int[] c : new int[][] {{-1, -1}, {1, 1}}) {
			put(j, x + c[0], 2, z + c[1], Blocks.OAK_FENCE.defaultBlockState());
			put(j, x + c[0], 3, z + c[1], Blocks.OAK_FENCE.defaultBlockState());
		}
	}

	private static void oasis(Job j, int x, int z) {
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 <= 9) {
					put(j, x + dx, -1, z + dz, Blocks.SAND.defaultBlockState());
					put(j, x + dx, 0, z + dz, Blocks.WATER.defaultBlockState());
				} else if (d2 <= 25) {
					put(j, x + dx, 0, z + dz, Blocks.GRASS_BLOCK.defaultBlockState());
					if (j.rnd.nextInt(3) == 0) put(j, x + dx, 1, z + dz, Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}
		palm(j, x + 4, z + 3);
		palm(j, x - 4, z - 3);
		palm(j, x + 1, z - 5);
	}

	private static void palm(Job j, int x, int z) {
		BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
		for (int y = 1; y <= 3; y++) put(j, x, y, z, log);
		for (int y = 4; y <= 6; y++) put(j, x + 1, y, z, log);
		int tx = x + 1, ty = 7;
		BlockState leaf = leaves(Blocks.JUNGLE_LEAVES);
		put(j, tx, ty, z, leaf);
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			put(j, tx + d[0], ty, z + d[1], leaf);
			put(j, tx + 2 * d[0], ty, z + 2 * d[1], leaf);
			put(j, tx + 3 * d[0], ty - 1, z + 3 * d[1], leaf);
		}
	}

	private static void fossil(Job j, int x, int z) {
		BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
		for (int dx = 0; dx < 5; dx++) put(j, x + dx, 1, z, bone);
		for (int dx = 0; dx < 5; dx += 2) {
			put(j, x + dx, 1, z + 1, bone);
			put(j, x + dx, 1, z - 1, bone);
			put(j, x + dx, 2, z + 1, bone);
			put(j, x + dx, 2, z - 1, bone);
		}
	}

	private static void igloo(Job j, int x, int z) {
		BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int y = 1; y <= 4; y++) {
					double d = Math.sqrt(dx * dx + dz * dz + (y - 0.5) * (y - 0.5) * 1.6);
					if (d <= 4.2 && d > 3.1) put(j, x + dx, y, z + dz, snow);
					else if (d <= 3.1) put(j, x + dx, y, z + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		int dir = x > 0 ? -1 : 1; // Eingang zur Mitte
		for (int k = 3; k <= 5; k++) {
			put(j, x + dir * k, 1, z, Blocks.AIR.defaultBlockState());
			put(j, x + dir * k, 2, z, Blocks.AIR.defaultBlockState());
			put(j, x + dir * k, 1, z + 1, snow);
			put(j, x + dir * k, 1, z - 1, snow);
			put(j, x + dir * k, 2, z + 1, snow);
			put(j, x + dir * k, 2, z - 1, snow);
			put(j, x + dir * k, 3, z, snow);
		}
		put(j, x, 1, z, Blocks.LANTERN.defaultBlockState());
	}

	private static void snowman(Job j, int x, int z) {
		put(j, x, 1, z, Blocks.SNOW_BLOCK.defaultBlockState());
		put(j, x, 2, z, Blocks.SNOW_BLOCK.defaultBlockState());
		put(j, x, 3, z, Blocks.CARVED_PUMPKIN.defaultBlockState());
	}

	private static void netherPatch(Job j, int x, int z, BlockState nylium, BlockState fungus, BlockState wart) {
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				if (dx * dx + dz * dz * 0.6 > 49 || j.rnd.nextInt(5) == 0) continue;
				put(j, x + dx, 0, z + dz, nylium);
				if (j.rnd.nextInt(7) == 0) put(j, x + dx, 1, z + dz, fungus);
			}
		}
		// ein großer Pilz
		int fx = x + (x > 0 ? 2 : -2);
		for (int y = 1; y <= 6; y++) put(j, fx, y, z + 5, Blocks.CRIMSON_STEM.defaultBlockState());
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				put(j, fx + dx, 7, z + 5 + dz, Math.floorMod(dx * 7 + dz * 3, 5) == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : wart);
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) put(j, fx + dx, 6, z + 5 + dz, wart);
			}
		}
	}

	private static void fortressRuin(Job j, int x, int z) {
		BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
		for (int dx = -6; dx <= 6; dx++) {
			boolean pillar = Math.abs(dx) == 6 || dx == 0;
			int h = pillar ? 6 + j.rnd.nextInt(2) : (j.rnd.nextInt(4) == 0 ? 0 : 5);
			for (int y = 1; y <= h; y++) {
				if (!pillar && y < 5) continue;
				put(j, x + dx, y, z, j.rnd.nextInt(7) == 0 ? Blocks.CRACKED_NETHER_BRICKS.defaultBlockState() : brick);
			}
			if (!pillar && h > 0 && j.rnd.nextBoolean()) put(j, x + dx, 6, z, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
		}
	}

	// ================================================================== Dorf

	/** Bodenbelag im Dorf: Pflaster auf Straßen, Muster auf dem Marktplatz, sonst Gras. */
	private static BlockState villageGround(Job j, int x, int z) {
		RandomSource r = j.rnd;
		int d2 = x * x + z * z;
		if (d2 <= 110) {
			// Marktplatz: Ringe aus Steinziegeln und polierter Andesit
			int ring = (int) Math.sqrt(d2);
			if (ring % 3 == 0) return Blocks.STONE_BRICKS.defaultBlockState();
			return r.nextInt(6) == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
		}
		boolean main = Math.abs(z) <= 3, cross = Math.abs(x) <= 2;
		if (main || cross) {
			int edge = main ? Math.abs(z) : Math.abs(x);
			int lim = main ? 3 : 2;
			if (edge == lim) return r.nextInt(4) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			int k = r.nextInt(10);
			return k < 6 ? Blocks.DIRT_PATH.defaultBlockState() : k < 8 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
		}
		return Blocks.GRASS_BLOCK.defaultBlockState();
	}

	private static void village(Job j) {
		RandomSource r = j.rnd;
		int rowZ = 12; // Abstand der Häuserreihen zur Hauptstraße
		// Brunnen in der Mitte
		fountain(j, 0, 0);
		// Marktstände rund um den Platz
		BlockState[] awnings = {Blocks.WOOL.red().defaultBlockState(), Blocks.WOOL.yellow().defaultBlockState(),
				Blocks.WOOL.blue().defaultBlockState(), Blocks.WOOL.lime().defaultBlockState()};
		int[][] stalls = {{7, -7}, {-7, -7}, {7, 7}, {-7, 7}};
		for (int i = 0; i < stalls.length; i++) stall(j, stalls[i][0], stalls[i][1], awnings[i]);
		// Kirche am Nordende der Querstraße, Dorflinde im Süden
		church(j, 0, -(rowZ - 2), -1);
		bigTree(j, 0, rowZ + 2);
		// Häuserreihen
		int idx = 0;
		for (int x = -j.hx + 9; x <= j.hx - 9; x += 14) {
			if (Math.abs(x) < 13) continue;
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				int hz = sgn * rowZ;
				house(j, x, hz, -sgn, idx++);
				garden(j, x, sgn * (rowZ + 7), sgn);
				// Weg von der Haustür zur Straße
				for (int z = 4; z < rowZ - 3; z++) put(j, x, 0, sgn * z, Blocks.DIRT_PATH.defaultBlockState());
				lamp(j, x + 3, sgn * 5);
			}
		}
		// Laternen an der Hauptstraße
		for (int x = -j.hx + 4; x <= j.hx - 4; x += 10) {
			if (Math.abs(x) <= 12) continue;
			lamp(j, x, 4);
			lamp(j, x, -4);
		}
		// Bäume, Büsche und Heuwagen am Rand
		for (int i = 0; i < j.hx / 4; i++) {
			int x = r.nextInt(j.hx * 2 - 6) - j.hx + 3;
			int z = (j.hz - 2 - r.nextInt(3)) * (r.nextBoolean() ? 1 : -1);
			if (r.nextInt(3) == 0) bush(j, x, z);
			else oakTree(j, x, z);
		}
		haycart(j, -j.hx + 6, 6);
		haycart(j, j.hx - 6, -6);
		well(j, j.hx / 2 + 3, 6, Blocks.COBBLESTONE.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
		well(j, -j.hx / 2 - 3, -6, Blocks.COBBLESTONE.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
	}

	private static BlockState stair(Block b, Direction facing) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	private static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axis);
	}

	private static BlockState pane(boolean alongX) {
		return Blocks.GLASS_PANE.defaultBlockState()
				.setValue(CrossCollisionBlock.EAST, alongX).setValue(CrossCollisionBlock.WEST, alongX)
				.setValue(CrossCollisionBlock.NORTH, !alongX).setValue(CrossCollisionBlock.SOUTH, !alongX);
	}

	/**
	 * Fachwerkhaus 9x7: Steinsockel, Holzbalken, weiße Füllung, Fenster, Satteldach mit Überstand,
	 * Schornstein mit Rauch. {@code door} = +1/-1: Tür zeigt in +z / -z Richtung.
	 */
	private static void house(Job j, int cx, int cz, int door, int variant) {
		Block roofStair = switch (variant % 3) {
			case 0 -> Blocks.DARK_OAK_STAIRS;
			case 1 -> Blocks.BRICK_STAIRS;
			default -> Blocks.SPRUCE_STAIRS;
		};
		Block roofSlab = switch (variant % 3) {
			case 0 -> Blocks.DARK_OAK_SLAB;
			case 1 -> Blocks.BRICK_SLAB;
			default -> Blocks.SPRUCE_SLAB;
		};
		BlockState fill = variant % 4 == 3 ? Blocks.BRICKS.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
		BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				boolean wallX = Math.abs(dx) == 4, wallZ = Math.abs(dz) == 3;
				int x = cx + dx, z = cz + dz;
				put(j, x, 0, z, Blocks.COBBLESTONE.defaultBlockState());
				if (!wallX && !wallZ) {
					put(j, x, 1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
					for (int y = 2; y <= 8; y++) put(j, x, y, z, air);
					continue;
				}
				put(j, x, 1, z, j.rnd.nextInt(5) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
				boolean isPost = (wallX && wallZ) || (wallZ && Math.abs(dx) == 2) || (wallX && dz == 0 && false);
				for (int y = 2; y <= 4; y++) {
					BlockState s = isPost ? post : fill;
					// Fenster
					if (y == 3 && wallZ && Math.abs(dx) == 3) s = pane(true);
					if (y == 3 && wallX && Math.abs(dz) == 1) s = pane(false);
					// Tür
					if (wallZ && dz == door * 3 && dx == 0 && y <= 3) s = air;
					put(j, x, y, z, s);
				}
				// Rähm (waagerechter Balken oben)
				put(j, x, 5, z, wallZ ? log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X) : log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
			}
		}
		// Satteldach entlang x, mit Überstand
		for (int k = 0; k <= 3; k++) {
			for (int dx = -5; dx <= 5; dx++) {
				put(j, cx + dx, 5 + k, cz - (4 - k), stair(roofStair, Direction.SOUTH));
				put(j, cx + dx, 5 + k, cz + (4 - k), stair(roofStair, Direction.NORTH));
			}
		}
		for (int dx = -5; dx <= 5; dx++) put(j, cx + dx, 9, cz, roofSlab.defaultBlockState());
		// Giebel
		for (int k = 1; k <= 3; k++) {
			for (int dz = -(3 - k); dz <= 3 - k; dz++) {
				for (int sx = -4; sx <= 4; sx += 8) put(j, cx + sx, 5 + k, cz + dz, dz == 0 && k == 1 ? pane(false) : fill);
			}
		}
		// Schornstein mit Rauch
		int chx = cx + 2, chz = cz - door * 2;
		for (int y = 2; y <= 10; y++) put(j, chx, y, chz, Blocks.BRICKS.defaultBlockState());
		put(j, chx, 11, chz, Blocks.CAMPFIRE.defaultBlockState());
		// Lampe über der Tür, Blumen vor dem Haus, etwas Einrichtung
		put(j, cx + 1, 4, cz + door * 4, Blocks.LANTERN.defaultBlockState());
		put(j, cx - 2, 1, cz + door * 4, Blocks.POPPY.defaultBlockState());
		put(j, cx + 2, 1, cz + door * 4, Blocks.RED_TULIP.defaultBlockState());
		put(j, cx - 3, 1, cz + door * 4, Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
		put(j, cx + 3, 1, cz + door * 4, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
		put(j, cx - 3, 2, cz - door * 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		put(j, cx - 2, 2, cz - door * 2, Blocks.BARREL.defaultBlockState());
		put(j, cx + 3, 2, cz + door * 2, Blocks.BOOKSHELF.defaultBlockState());
		put(j, cx - 3, 2, cz + door * 2, Blocks.LANTERN.defaultBlockState());
	}

	/** Steinkirche mit Satteldach, hohen Fenstern und Glockenturm. dir = -1: Kirche liegt Richtung -z. */
	private static void church(Job j, int cx, int front, int dir) {
		int len = Math.min(11, j.hz - Math.abs(front) - 7);
		BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int k = 0; k <= len; k++) {
			int z = front + dir * k;
			for (int dx = -4; dx <= 4; dx++) {
				boolean side = Math.abs(dx) == 4, end = k == 0 || k == len;
				put(j, cx + dx, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
				if (!side && !end) {
					put(j, cx + dx, 1, z, Math.abs(dx) <= 1 ? Blocks.CARPET.red().defaultBlockState() : Blocks.SPRUCE_STAIRS.defaultBlockState()
							.setValue(StairBlock.FACING, dir < 0 ? Direction.NORTH : Direction.SOUTH)); // rote Mitte, Bänke links/rechts
					for (int y = 2; y <= 8; y++) put(j, cx + dx, y, z, air);
					continue;
				}
				for (int y = 1; y <= 7; y++) {
					BlockState s = j.rnd.nextInt(9) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : wall;
					if (side && k % 3 == 1 && y >= 3 && y <= 5) s = pane(false); // hohe Fenster
					if (k == 0 && Math.abs(dx) <= 1 && y <= 3) s = air; // Portal
					if (k == 0 && Math.abs(dx) <= 1 && y == 4) s = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
					if (k == len && dx == 0 && y >= 3 && y <= 5) s = pane(true); // Rosenfenster
					put(j, cx + dx, y, z, s);
				}
			}
		}
		// Dach entlang z
		for (int k = 0; k <= 4; k++) {
			for (int kk = -1; kk <= len + 1; kk++) {
				int z = front + dir * kk;
				put(j, cx - (5 - k), 7 + k, z, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST));
				put(j, cx + (5 - k), 7 + k, z, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST));
			}
		}
		for (int kk = -1; kk <= len + 1; kk++) put(j, cx, 12, front + dir * kk, Blocks.DARK_OAK_SLAB.defaultBlockState());
		for (int k = 1; k <= 4; k++) {
			for (int dx = -(4 - k); dx <= 4 - k; dx++) {
				put(j, cx + dx, 7 + k, front, wall);
				put(j, cx + dx, 7 + k, front + dir * len, wall);
			}
		}
		// Glockenturm vorne über dem Portal
		int tz = front - dir * 2;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				for (int y = 1; y <= 16; y++) {
					if (!rim) {
						if (y == 12) put(j, cx + dx, y, tz + dz, Blocks.STONE_BRICKS.defaultBlockState());
						else if (y < 12 || y > 12) put(j, cx + dx, y, tz + dz, air);
						continue;
					}
					BlockState s = wall;
					boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					if (!corner && y >= 13 && y <= 15) s = air; // Schallöffnungen
					if (!corner && y <= 3 && dz == dir * 2 && Math.abs(dx) <= 1) s = air; // Eingang zur Kirche
					if (!corner && y <= 3 && dz == -dir * 2 && dx == 0) s = air; // Eingang von der Straße
					put(j, cx + dx, y, tz + dz, s);
				}
			}
		}
		put(j, cx, 13, tz, Blocks.BELL.defaultBlockState());
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) put(j, cx + dx, 17, tz + dz, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		// spitzer Turmhelm
		for (int lvl = 0; lvl <= 2; lvl++) {
			int rr = 2 - lvl;
			for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) put(j, cx + dx, 18 + lvl * 2, tz + dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
			for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) put(j, cx + dx, 19 + lvl * 2, tz + dz, Blocks.DARK_OAK_SLAB.defaultBlockState());
		}
		put(j, cx, 23, tz, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(j, cx, 24, tz, Blocks.GOLD_BLOCK.defaultBlockState());
		put(j, cx - 2, 4, tz - dir * 3, Blocks.LANTERN.defaultBlockState());
		put(j, cx + 2, 4, tz - dir * 3, Blocks.LANTERN.defaultBlockState());
	}

	private static void fountain(Job j, int x, int z) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > 10) continue;
				if (d2 > 4) {
					put(j, x + dx, 1, z + dz, Blocks.STONE_BRICK_SLAB.defaultBlockState());
				} else {
					put(j, x + dx, -1, z + dz, Blocks.STONE_BRICKS.defaultBlockState());
					put(j, x + dx, 0, z + dz, Blocks.WATER.defaultBlockState());
				}
			}
		}
		for (int y = 0; y <= 2; y++) put(j, x, y, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		put(j, x, 3, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
		put(j, x, 4, z, Blocks.LANTERN.defaultBlockState());
	}

	private static void stall(Job j, int x, int z, BlockState awning) {
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			put(j, x + c[0], 1, z + c[1], Blocks.SPRUCE_FENCE.defaultBlockState());
			put(j, x + c[0], 2, z + c[1], Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(j, x + dx, 3, z + dz, awning);
		put(j, x, 4, z, awning);
		put(j, x, 1, z, Blocks.BARREL.defaultBlockState());
		put(j, x + (x > 0 ? 1 : -1), 1, z, Blocks.HAY_BLOCK.defaultBlockState());
		put(j, x, 2, z, Blocks.CARVED_PUMPKIN.defaultBlockState());
	}

	private static void lamp(Job j, int x, int z) {
		put(j, x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
		put(j, x, 2, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
		put(j, x, 3, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
		put(j, x, 4, z, Blocks.LANTERN.defaultBlockState());
	}

	/** Gemüsegarten hinter dem Haus: Zaun, Weizen, Wasserrinne, Komposter. */
	private static void garden(Job j, int cx, int cz, int sgn) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int x = cx + dx, z = cz + dz;
				if (Math.abs(z) > j.hz - 1) continue;
				boolean rim = Math.abs(dx) == 4 || Math.abs(dz) == 3;
				if (rim) {
					put(j, x, 1, z, Blocks.OAK_FENCE.defaultBlockState());
					continue;
				}
				if (dz == 0) {
					put(j, x, 0, z, Blocks.WATER.defaultBlockState());
					continue;
				}
				put(j, x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
				Block crop = (dx + 4) % 3 == 0 ? Blocks.CARROTS : (dx + 4) % 3 == 1 ? Blocks.WHEAT : Blocks.POTATOES;
				put(j, x, 1, z, crop.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
			}
		}
		put(j, cx - 4, 1, cz - sgn * 3, Blocks.COMPOSTER.defaultBlockState());
	}

	private static void bush(Job j, int x, int z) {
		BlockState b = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (j.rnd.nextInt(4) != 0) put(j, x + dx, 1, z + dz, b);
		put(j, x, 2, z, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
	}

	/** Große Dorflinde mit Bank drumherum. */
	private static void bigTree(Job j, int x, int z) {
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		BlockState leaf = leaves(Blocks.OAK_LEAVES);
		for (int y = 1; y <= 7; y++) put(j, x, y, z, log);
		for (int dy = 5; dy <= 10; dy++) {
			int rad = dy <= 6 ? 3 : dy <= 8 ? 4 : 2;
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					if (dx * dx + dz * dz > rad * rad + 1 || (dx == 0 && dz == 0 && dy <= 7)) continue;
					if (j.rnd.nextInt(8) == 0) continue;
					put(j, x + dx, dy, z + dz, leaf);
				}
			}
		}
		for (int[] o : new int[][] {{2, 0, 0}, {-2, 0, 1}, {0, 2, 2}, {0, -2, 3}}) {
			Direction f = Direction.from2DDataValue(o[2]);
			put(j, x + o[0], 1, z + o[1], stair(Blocks.SPRUCE_STAIRS, f));
		}
	}

	private static void haycart(Job j, int x, int z) {
		put(j, x, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
		put(j, x + 1, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
		put(j, x, 2, z, Blocks.HAY_BLOCK.defaultBlockState());
		put(j, x - 1, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(j, x + 2, 1, z, Blocks.BARREL.defaultBlockState());
	}

	private static int cz(int dz) {
		return dz;
	}
}
