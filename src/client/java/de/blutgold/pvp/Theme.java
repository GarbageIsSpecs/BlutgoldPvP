package de.blutgold.pvp;

/** Farbschemata für Menü, HUD, Crosshair und Hitboxen. */
public record Theme(String name, int accent, int highlight, int bg, int panel, int panelLight, int text, int muted) {
	public static final Theme[] ALL = {
			new Theme("Blutgold",    0xFFE0202A, 0xFFFFC94A, 0xF0140708, 0xFF1E0B0C, 0xFF2E1315, 0xFFF5EAEA, 0xFFB09898),
			new Theme("Ozean",       0xFF1E9BFF, 0xFF7FE7FF, 0xF0060E18, 0xFF0B1826, 0xFF15283E, 0xFFEAF4FF, 0xFF8FA6BF),
			new Theme("Smaragd",     0xFF1FCB6B, 0xFFC8FF6A, 0xF0061209, 0xFF0C1E12, 0xFF163220, 0xFFEAFBEF, 0xFF92B39E),
			new Theme("Amethyst",    0xFFA45CFF, 0xFFFF7AD9, 0xF00E0818, 0xFF181026, 0xFF271B3D, 0xFFF2EAFF, 0xFFA898C0),
			new Theme("Sonnenglut",  0xFFFF7A1E, 0xFFFFE066, 0xF0170C04, 0xFF241407, 0xFF38200D, 0xFFFFF3E6, 0xFFC0A68E),
			new Theme("Mitternacht", 0xFFE6E6E6, 0xFF9AA0A6, 0xF00A0A0B, 0xFF141416, 0xFF222226, 0xFFF2F2F2, 0xFF8E8E94),
	};

	public static Theme current() {
		int i = Config.theme;
		return ALL[i >= 0 && i < ALL.length ? i : 0];
	}

	/** Farbe mit neuer Deckkraft (0..1). */
	public static int withAlpha(int color, float alpha) {
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		return (a << 24) | (color & 0x00FFFFFF);
	}

	/** Farbe dunkler machen (factor 0..1, 1 = unverändert). */
	public static int darker(int color, float factor) {
		int r = (int) (((color >> 16) & 0xFF) * factor);
		int g = (int) (((color >> 8) & 0xFF) * factor);
		int b = (int) ((color & 0xFF) * factor);
		return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** Zwischen zwei Farben mischen (t 0..1). */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF, aa = (a >>> 24);
		int rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF, ab = (b >>> 24);
		int r = Math.round(ra + (rb - ra) * t), g = Math.round(ga + (gb - ga) * t), bl = Math.round(ba + (bb - ba) * t);
		int al = Math.round(aa + (ab - aa) * t);
		return (al << 24) | (r << 16) | (g << 8) | bl;
	}

	/** HSV -> ARGB (h beliebig, wird auf 0..1 gewickelt; s, v 0..1). */
	public static int hsv(double h, float s, float v) {
		double hh = (h - Math.floor(h)) * 6.0;
		int sector = (int) hh;
		double f = hh - sector;
		double p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		double r, g, b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000 | ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
	}
}
