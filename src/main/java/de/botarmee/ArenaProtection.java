package de.botarmee;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/**
 * Gebaute Arenen sind geschützt: keine Explosionslöcher, kein Abbauen im Überlebensmodus,
 * keine Monster. Die Bereiche werden pro Welt in botarmee_arenen.txt gespeichert.
 */
public final class ArenaProtection {
	private ArenaProtection() {}

	public record Box(String dim, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		boolean contains(String d, int x, int y, int z) {
			return d.equals(dim) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}

		AABB aabb() {
			return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
		}
	}

	private static final List<Box> boxes = new ArrayList<>();
	private static Path file;
	private static int sweepIn = 0;

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	public static void load(MinecraftServer server) {
		boxes.clear();
		file = server.getWorldPath(LevelResource.ROOT).resolve("botarmee_arenen.txt");
		try {
			if (Files.exists(file)) {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					String[] a = line.trim().split(" ");
					if (a.length != 7) continue;
					boxes.add(new Box(a[0], Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]),
							Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6])));
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static void save() {
		if (file == null) return;
		StringBuilder sb = new StringBuilder();
		for (Box b : boxes) {
			sb.append(b.dim()).append(' ').append(b.minX()).append(' ').append(b.minY()).append(' ').append(b.minZ()).append(' ')
					.append(b.maxX()).append(' ').append(b.maxY()).append(' ').append(b.maxZ()).append('\n');
		}
		try {
			Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	public static void add(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		String d = dim(level);
		// alte Arena an genau derselben Stelle ersetzen
		boxes.removeIf(b -> b.dim().equals(d) && b.minX() < maxX && b.maxX() > minX && b.minZ() < maxZ && b.maxZ() > minZ);
		boxes.add(new Box(d, minX, minY, minZ, maxX, maxY, maxZ));
		save();
	}

	public static int clearAll() {
		int n = boxes.size();
		boxes.clear();
		save();
		return n;
	}

	public static boolean isProtected(Level level, BlockPos pos) {
		if (boxes.isEmpty()) return false;
		String d = dim(level);
		for (Box b : boxes) if (b.contains(d, pos.getX(), pos.getY(), pos.getZ())) return true;
		return false;
	}

	static boolean isMonster(Entity e) {
		return e instanceof Mob && e instanceof Enemy && !(e instanceof BotEntity) && !e.hasCustomName();
	}

	/** Alle 2 Sekunden: Monster aus den Arenen entfernen. */
	public static void tick(MinecraftServer server) {
		if (boxes.isEmpty() || --sweepIn > 0) return;
		sweepIn = 40;
		for (ServerLevel level : server.getAllLevels()) {
			String d = dim(level);
			for (Box b : boxes) {
				if (!b.dim().equals(d)) continue;
				for (Mob m : level.getEntitiesOfClass(Mob.class, b.aabb(), ArenaProtection::isMonster)) m.discard();
			}
		}
	}
}
