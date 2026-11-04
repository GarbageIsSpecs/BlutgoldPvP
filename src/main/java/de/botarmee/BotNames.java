package de.botarmee;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.util.RandomSource;

/** Erfundene Gamer-Namen, damit die Bots wie echte Spieler aussehen. */
public final class BotNames {
	private BotNames() {}

	private static final String[] A = {
			"Dark", "Shadow", "Pixel", "Frost", "Blaze", "Night", "Storm", "Iron", "Toxic", "Lucky", "Silent", "Crazy",
			"Mega", "Ultra", "Hyper", "Turbo", "Epic", "Mystic", "Royal", "Savage", "Golden", "Crimson", "Ender", "Nether",
			"Sky", "Lava", "Creeper", "Diamond", "Ghost", "Ninja", "Wolf", "Dragon", "Cyber", "Noob", "Pro", "Sweaty",
			"Baby", "Sir", "Captain", "Mister", "Lord", "Tiny", "Big", "Spicy", "Salty", "Sleepy", "Hungry", "Angry"
	};
	private static final String[] B = {
			"Miner", "Gamer", "Wolf", "Fox", "Slayer", "Hunter", "Knight", "Warrior", "Builder", "Steve", "Blade", "Archer",
			"Panda", "Potato", "Taco", "Cookie", "Noodle", "Pickle", "Shark", "Tiger", "Bear", "Falcon", "Viper", "Ghost",
			"Crafter", "Digger", "Sniper", "Legend", "King", "Queen", "Boy", "Girl", "Dude", "Bro", "Bot", "Player",
			"Gaming", "YT", "TV", "HD", "MC", "PvP", "Clutch", "Bridger", "Sweat", "Tryhard", "Enjoyer", "Main"
	};
	private static final String[] SOLO = {
			"xXSlayerXx", "notabot", "imsostuck", "lagmaster", "MLGwater", "creeperhugs", "dirtblock", "pigrider",
			"bedwarsgod", "skywarsbest", "tntlover", "diamondsplz", "chickenjockey", "afkfarmer", "redstonenerd",
			"spawnkiller", "iamsteve", "nobodyknows", "justvibing", "soupmaster", "elytraboi", "wardenfan"
	};

	private static final Set<String> used = new HashSet<>();

	public static void reset() {
		used.clear();
	}

	public static String next(RandomSource r) {
		for (int tries = 0; tries < 20; tries++) {
			String name = make(r);
			if (name.length() <= 16 && used.add(name)) return name;
		}
		return "Bot" + (1000 + r.nextInt(9000));
	}

	private static String make(RandomSource r) {
		int style = r.nextInt(10);
		String a = A[r.nextInt(A.length)];
		String b = B[r.nextInt(B.length)];
		return switch (style) {
			case 0 -> SOLO[r.nextInt(SOLO.length)] + (r.nextBoolean() ? "" : String.valueOf(r.nextInt(100)));
			case 1 -> a + "_" + b;
			case 2 -> (a + b).toLowerCase();
			case 3 -> "xX" + a + b + "Xx";
			case 4 -> a + b + r.nextInt(1000);
			case 5 -> b + "_" + (r.nextInt(90) + 10);
			case 6 -> "The" + a + b;
			case 7 -> a.toLowerCase() + "_" + b.toLowerCase() + "_";
			default -> a + b;
		};
	}
}
