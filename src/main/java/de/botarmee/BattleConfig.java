package de.botarmee;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alle Einstellungen einer Schlacht. Wird im Menü bearbeitet, als Text an den Server geschickt
 * und in config/botarmee.properties gespeichert.
 */
public class BattleConfig {
	public static final int MAX_TEAMS = 4;
	public static final int MAX_PER_TEAM = 150;
	public static final int MAX_TOTAL = 300;

	/** Teamfarben: Anzeigename, RGB, Index in TeamColor (Scoreboard). */
	public static final String[] COLOR_NAMES = {"Rot", "Blau", "Grün", "Gelb", "Lila", "Türkis", "Orange", "Weiß"};
	public static final int[] COLOR_RGB = {0xFFE5484D, 0xFF3E7BFA, 0xFF46C25B, 0xFFF2D14B, 0xFFA45CE6, 0xFF3CC7C7, 0xFFF08A24, 0xFFEDEDED};
	public static final int[] COLOR_TEAM = {12, 9, 10, 14, 13, 11, 6, 15};
	/** Leder-Farbe passend zur Teamfarbe. */
	public static final int[] COLOR_DYE = {0xB02E26, 0x3C44AA, 0x5E7C16, 0xFED83D, 0x8932B8, 0x169C9C, 0xF9801D, 0xF9FFFE};

	public static final String[] FORMATIONS = {"Block", "Breite Linie", "Keil", "Locker"};
	public static final String[] ACTIONS = {"Halten", "Marschieren", "Angriff", "Bogensalve", "Flankieren", "Rückzug"};
	public static final int ACT_HOLD = 0, ACT_MARCH = 1, ACT_ATTACK = 2, ACT_VOLLEY = 3, ACT_FLANK = 4, ACT_RETREAT = 5;
	public static final String[] RANGED = {"Bogen", "Armbrust"};
	public static final String[] SKIN_MODES = {"Alle Standard-Skins", "Nur Steve & Alex", "Eigene Skins (Ordner)"};

	public static class Piece {
		public String item = "";
		public String pattern = "";
		public String material = "";
		public boolean glint = false;

		Piece() {}

		Piece(String item) {
			this.item = item;
		}

		Piece copy() {
			Piece p = new Piece(item);
			p.pattern = pattern;
			p.material = material;
			p.glint = glint;
			return p;
		}

		String encode() {
			return item + "|" + pattern + "|" + material + "|" + (glint ? 1 : 0);
		}

		static Piece decode(String s) {
			Piece p = new Piece();
			if (s == null) return p;
			String[] a = s.split("\\|", -1);
			if (a.length > 0) p.item = a[0];
			if (a.length > 1) p.pattern = a[1];
			if (a.length > 2) p.material = a[2];
			if (a.length > 3) p.glint = a[3].equals("1");
			return p;
		}
	}

	/** Reihenfolge: Kopf, Brust, Beine, Füße, Haupthand, Nebenhand. */
	public static final String[] SLOT_NAMES = {"Helm", "Brustpanzer", "Hose", "Schuhe", "Waffe", "Nebenhand"};

	public static class Phase {
		public int action;
		public int seconds;

		public Phase(int action, int seconds) {
			this.action = action;
			this.seconds = seconds;
		}
	}

	public static class Team {
		public String name;
		public int color;
		public int count = 30;
		public int archers = 20;
		public int ranged = 0;
		public int edge = 50;
		public int formation = 0;
		public int variation = 15;
		public Piece[] kit = new Piece[6];
		public List<Phase> sequence = new ArrayList<>();

		public Team(String name, int color) {
			this.name = name;
			this.color = color;
			for (int i = 0; i < 6; i++) kit[i] = new Piece();
		}

		public int rgb() {
			return COLOR_RGB[Math.floorMod(color, COLOR_RGB.length)];
		}
	}

	public final List<Team> teams = new ArrayList<>();
	/** -1 = fair (Zufall), sonst Index des Teams, das gewinnen soll. */
	public int winner = -1;
	/** 0 = klarer Sieg, 100 = extrem knapp. */
	public int closeness = 60;
	public int distance = 40;
	public int speed = 100;
	public int aggression = 100;
	public boolean names = true;
	public boolean glow = false;
	public boolean hud = true;
	public boolean countdown = true;
	public boolean winnerTitle = true;
	public boolean dyeLeather = true;
	public boolean useArena = true;
	public int skinMode = 0;
	public int arena = 0;
	public int arenaSize = 1;

	public static BattleConfig defaults() {
		BattleConfig c = new BattleConfig();
		Team a = new Team("Römer", 0);
		a.kit[0] = new Piece("minecraft:iron_helmet");
		a.kit[1] = new Piece("minecraft:iron_chestplate");
		a.kit[1].pattern = "sentry";
		a.kit[1].material = "gold";
		a.kit[2] = new Piece("minecraft:leather_leggings");
		a.kit[3] = new Piece("minecraft:iron_boots");
		a.kit[4] = new Piece("minecraft:iron_sword");
		a.kit[5] = new Piece("minecraft:shield");
		a.sequence.add(new Phase(ACT_HOLD, 3));
		a.sequence.add(new Phase(ACT_MARCH, 6));
		a.sequence.add(new Phase(ACT_ATTACK, 0));
		Team b = new Team("Wikinger", 1);
		b.kit[0] = new Piece("minecraft:chainmail_helmet");
		b.kit[1] = new Piece("minecraft:leather_chestplate");
		b.kit[2] = new Piece("minecraft:chainmail_leggings");
		b.kit[3] = new Piece("minecraft:leather_boots");
		b.kit[4] = new Piece("minecraft:iron_axe");
		b.archers = 10;
		b.formation = 3;
		b.sequence.add(new Phase(ACT_VOLLEY, 4));
		b.sequence.add(new Phase(ACT_ATTACK, 0));
		c.teams.add(a);
		c.teams.add(b);
		return c;
	}

	// ------------------------------------------------------------ Text-Format

	public String encode() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("teams", String.valueOf(teams.size()));
		m.put("winner", String.valueOf(winner));
		m.put("closeness", String.valueOf(closeness));
		m.put("distance", String.valueOf(distance));
		m.put("speed", String.valueOf(speed));
		m.put("aggression", String.valueOf(aggression));
		m.put("names", b(names));
		m.put("glow", b(glow));
		m.put("hud", b(hud));
		m.put("countdown", b(countdown));
		m.put("winnerTitle", b(winnerTitle));
		m.put("dyeLeather", b(dyeLeather));
		m.put("useArena", b(useArena));
		m.put("skinMode", String.valueOf(skinMode));
		m.put("arena", String.valueOf(arena));
		m.put("arenaSize", String.valueOf(arenaSize));
		for (int i = 0; i < teams.size(); i++) {
			Team t = teams.get(i);
			String p = "t" + i + ".";
			m.put(p + "name", t.name.replace("\n", " "));
			m.put(p + "color", String.valueOf(t.color));
			m.put(p + "count", String.valueOf(t.count));
			m.put(p + "archers", String.valueOf(t.archers));
			m.put(p + "ranged", String.valueOf(t.ranged));
			m.put(p + "edge", String.valueOf(t.edge));
			m.put(p + "formation", String.valueOf(t.formation));
			m.put(p + "variation", String.valueOf(t.variation));
			for (int s = 0; s < 6; s++) m.put(p + "kit" + s, t.kit[s].encode());
			StringBuilder seq = new StringBuilder();
			for (Phase ph : t.sequence) {
				if (seq.length() > 0) seq.append(',');
				seq.append(ph.action).append(':').append(ph.seconds);
			}
			m.put(p + "seq", seq.toString());
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : m.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
		return sb.toString();
	}

	public static BattleConfig decode(String text) {
		Map<String, String> m = new LinkedHashMap<>();
		for (String line : text.split("\n")) {
			int eq = line.indexOf('=');
			if (eq > 0) m.put(line.substring(0, eq).trim(), line.substring(eq + 1));
		}
		if (!m.containsKey("teams")) return defaults();
		BattleConfig c = new BattleConfig();
		int n = clamp(i(m, "teams", 2), 2, MAX_TEAMS);
		c.winner = clamp(i(m, "winner", -1), -1, n - 1);
		c.closeness = clamp(i(m, "closeness", 60), 0, 100);
		c.distance = clamp(i(m, "distance", 40), 10, 120);
		c.speed = clamp(i(m, "speed", 100), 40, 200);
		c.aggression = clamp(i(m, "aggression", 100), 30, 200);
		c.names = bool(m, "names", true);
		c.glow = bool(m, "glow", false);
		c.hud = bool(m, "hud", true);
		c.countdown = bool(m, "countdown", true);
		c.winnerTitle = bool(m, "winnerTitle", true);
		c.dyeLeather = bool(m, "dyeLeather", true);
		c.useArena = bool(m, "useArena", true);
		c.skinMode = clamp(i(m, "skinMode", 0), 0, SKIN_MODES.length - 1);
		c.arena = Math.max(0, i(m, "arena", 0));
		c.arenaSize = clamp(i(m, "arenaSize", 1), 0, 2);
		for (int t = 0; t < n; t++) {
			String p = "t" + t + ".";
			Team team = new Team(m.getOrDefault(p + "name", "Team " + (t + 1)), clamp(i(m, p + "color", t), 0, COLOR_RGB.length - 1));
			team.count = clamp(i(m, p + "count", 30), 1, MAX_PER_TEAM);
			team.archers = clamp(i(m, p + "archers", 20), 0, 100);
			team.ranged = clamp(i(m, p + "ranged", 0), 0, 1);
			team.edge = clamp(i(m, p + "edge", 50), 0, 100);
			team.formation = clamp(i(m, p + "formation", 0), 0, FORMATIONS.length - 1);
			team.variation = clamp(i(m, p + "variation", 15), 0, 100);
			for (int s = 0; s < 6; s++) team.kit[s] = Piece.decode(m.get(p + "kit" + s));
			String seq = m.getOrDefault(p + "seq", "2:0");
			for (String part : seq.split(",")) {
				String[] a = part.split(":");
				if (a.length != 2) continue;
				try {
					team.sequence.add(new Phase(clamp(Integer.parseInt(a[0].trim()), 0, ACTIONS.length - 1), clamp(Integer.parseInt(a[1].trim()), 0, 120)));
				} catch (NumberFormatException ignored) {
				}
				if (team.sequence.size() >= 6) break;
			}
			if (team.sequence.isEmpty()) team.sequence.add(new Phase(ACT_ATTACK, 0));
			c.teams.add(team);
		}
		return c;
	}

	public BattleConfig copy() {
		return decode(encode());
	}

	private static String b(boolean v) {
		return v ? "1" : "0";
	}

	private static boolean bool(Map<String, String> m, String k, boolean def) {
		String v = m.get(k);
		return v == null ? def : v.trim().equals("1");
	}

	private static int i(Map<String, String> m, String k, int def) {
		try {
			return Integer.parseInt(m.getOrDefault(k, String.valueOf(def)).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
