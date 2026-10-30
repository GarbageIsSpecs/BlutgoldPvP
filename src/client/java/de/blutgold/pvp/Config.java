package de.blutgold.pvp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Alle Einstellungen, gespeichert in .minecraft/config/blutgoldpvp.properties.
 * Jedes "public static" Feld hier wird automatisch gespeichert und geladen.
 */
public final class Config {
	// ---------------- HUD
	public static boolean hud = true;
	public static boolean showFpsCps = true;
	public static boolean showArmor = true;
	public static boolean showEffects = true;
	public static float hudOpacity = 0.56f;
	public static boolean hudRight = false;
	public static boolean hudShadow = true;

	// ---------------- Info-Anzeigen
	public static boolean showCoords = false;
	public static boolean showDirection = false;
	public static boolean showSpeed = false;
	public static boolean showClock = false;
	public static boolean showCombo = true;
	public static boolean showReach = true;

	// ---------------- Hitboxen
	public static boolean hitboxes = true;
	public static boolean hitboxesPlayersOnly = true;
	public static boolean hitboxEyeLine = true;
	public static boolean hitboxFlash = true;

	// ---------------- Crosshair
	public static boolean targetCrosshair = true;
	public static boolean crosshairCooldown = true;
	public static boolean crosshairBrackets = true;
	public static float crosshairGap = 2f;

	// ---------------- Komfort
	public static boolean toggleSprint = false;
	public static boolean noHurtCam = true;
	/** Ursprüngliche Stärke des Kamera-Wackelns, damit wir sie wiederherstellen können. */
	public static double savedTilt = 1.0;

	// ---------------- Block-Rahmen
	public static boolean rainbowOutline = true;
	/** 0 = Regenbogen, 1 = Einfarbig, 2 = Theme-Verlauf */
	public static int outlineMode = 0;
	public static float outlineHue = 0.0f;
	public static float outlineSaturation = 1.0f;
	public static float outlineOpacity = 1.0f;
	public static float outlineThickness = 4f;
	public static float outlineSpeed = 0.35f;
	public static float outlineSpread = 0.6f;
	public static float outlineFill = 0.0f;

	// ---------------- Flügel
	public static boolean wings = true;
	/** 0 = Blutgold, 1 = Theme, 2 = Regenbogen, 3 = Schatten */
	public static int wingColor = 0;
	public static float wingScale = 1.0f;
	public static float wingFlapSpeed = 1.0f;
	public static float wingSpread = 0.5f;
	public static float wingOpacity = 0.85f;
	public static boolean wingVeins = true;
	public static boolean wingFlapOnlyMoving = false;

	// ---------------- Heiligenschein
	public static boolean halo = true;
	/** 0 = Gold, 1 = Theme, 2 = Regenbogen, 3 = Blutrot */
	public static int haloColor = 0;
	public static float haloSize = 1.0f;
	public static float haloHeight = 0.35f;
	public static float haloSpin = 1.0f;
	public static float haloGlow = 1.0f;
	public static boolean haloBob = true;

	// ---------------- Partikel
	public static boolean particles = true;
	/** siehe Cosmetics.PARTICLE_TYPES */
	public static int particleType = 0;
	public static float particleAmount = 0.4f;
	public static boolean particlesOnlyMoving = true;
	public static boolean particlesFirstPerson = true;

	// ---------------- Design
	public static int theme = 0;
	public static float menuDim = 0.4f;

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blutgoldpvp.properties");

	private Config() {}

	public static void load() {
		if (!Files.exists(FILE)) {
			save();
			return;
		}
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(FILE)) {
			p.load(r);
		} catch (IOException e) {
			return;
		}
		for (Field f : fields()) {
			String raw = p.getProperty(f.getName());
			if (raw == null) continue;
			try {
				Class<?> t = f.getType();
				if (t == boolean.class) f.setBoolean(null, Boolean.parseBoolean(raw.trim()));
				else if (t == int.class) f.setInt(null, (int) Float.parseFloat(raw.trim()));
				else if (t == float.class) f.setFloat(null, Float.parseFloat(raw.trim()));
				else if (t == double.class) f.setDouble(null, Double.parseDouble(raw.trim()));
			} catch (Exception ignored) {
				// kaputter Wert -> Standard behalten
			}
		}
	}

	public static void save() {
		Properties p = new Properties();
		for (Field f : fields()) {
			try {
				p.setProperty(f.getName(), String.valueOf(f.get(null)));
			} catch (IllegalAccessException ignored) {
			}
		}
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE)) {
				p.store(w, "Blutgold PvP - im Spiel mit dem Menue (Standard: M) einstellbar");
			}
		} catch (IOException ignored) {
		}
	}

	private static java.util.List<Field> fields() {
		java.util.List<Field> out = new java.util.ArrayList<>();
		for (Field f : Config.class.getDeclaredFields()) {
			int m = f.getModifiers();
			if (Modifier.isPublic(m) && Modifier.isStatic(m) && !Modifier.isFinal(m)) out.add(f);
		}
		return out;
	}
}
