package de.blutgold.pvp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Einfache Einstellungen, gespeichert in .minecraft/config/blutgoldpvp.properties */
public final class Config {
	// HUD
	public static boolean hud = true;
	public static boolean showFpsCps = true;
	public static boolean showArmor = true;
	public static boolean showEffects = true;
	public static float hudOpacity = 0.56f;

	// Hitboxen & Crosshair
	public static boolean hitboxes = true;
	public static boolean hitboxesPlayersOnly = true;
	public static boolean targetCrosshair = true;

	// Komfort
	public static boolean toggleSprint = false;
	public static boolean noHurtCam = true;
	/** Ursprüngliche Stärke des Kamera-Wackelns, damit wir sie wiederherstellen können. */
	public static double savedTilt = 1.0;

	// Block-Rahmen
	public static boolean rainbowOutline = true;
	/** 0 = Regenbogen, 1 = Einfarbig, 2 = Theme-Verlauf */
	public static int outlineMode = 0;
	public static float outlineHue = 0.0f;
	public static float outlineSaturation = 1.0f;
	public static float outlineOpacity = 1.0f;
	/** Dicke in Stufen 1..10 */
	public static float outlineThickness = 4f;
	public static float outlineSpeed = 0.35f;
	public static float outlineSpread = 0.6f;
	/** Deckkraft der Füllung im Block (0 = aus) */
	public static float outlineFill = 0.0f;

	// Theme
	public static int theme = 0;

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
		hud = bool(p, "hud", hud);
		showFpsCps = bool(p, "showFpsCps", showFpsCps);
		showArmor = bool(p, "showArmor", showArmor);
		showEffects = bool(p, "showEffects", showEffects);
		hudOpacity = num(p, "hudOpacity", hudOpacity);
		hitboxes = bool(p, "hitboxes", hitboxes);
		hitboxesPlayersOnly = bool(p, "hitboxesPlayersOnly", hitboxesPlayersOnly);
		targetCrosshair = bool(p, "targetCrosshair", targetCrosshair);
		toggleSprint = bool(p, "toggleSprint", toggleSprint);
		noHurtCam = bool(p, "noHurtCam", noHurtCam);
		savedTilt = num(p, "savedTilt", (float) savedTilt);
		rainbowOutline = bool(p, "rainbowOutline", rainbowOutline);
		outlineMode = (int) num(p, "outlineMode", outlineMode);
		outlineHue = num(p, "outlineHue", outlineHue);
		outlineSaturation = num(p, "outlineSaturation", outlineSaturation);
		outlineOpacity = num(p, "outlineOpacity", outlineOpacity);
		outlineThickness = num(p, "outlineThickness", outlineThickness);
		outlineSpeed = num(p, "outlineSpeed", outlineSpeed);
		outlineSpread = num(p, "outlineSpread", outlineSpread);
		outlineFill = num(p, "outlineFill", outlineFill);
		theme = (int) num(p, "theme", theme);
	}

	public static void save() {
		Properties p = new Properties();
		p.setProperty("hud", String.valueOf(hud));
		p.setProperty("showFpsCps", String.valueOf(showFpsCps));
		p.setProperty("showArmor", String.valueOf(showArmor));
		p.setProperty("showEffects", String.valueOf(showEffects));
		p.setProperty("hudOpacity", String.valueOf(hudOpacity));
		p.setProperty("hitboxes", String.valueOf(hitboxes));
		p.setProperty("hitboxesPlayersOnly", String.valueOf(hitboxesPlayersOnly));
		p.setProperty("targetCrosshair", String.valueOf(targetCrosshair));
		p.setProperty("toggleSprint", String.valueOf(toggleSprint));
		p.setProperty("noHurtCam", String.valueOf(noHurtCam));
		p.setProperty("savedTilt", String.valueOf(savedTilt));
		p.setProperty("rainbowOutline", String.valueOf(rainbowOutline));
		p.setProperty("outlineMode", String.valueOf(outlineMode));
		p.setProperty("outlineHue", String.valueOf(outlineHue));
		p.setProperty("outlineSaturation", String.valueOf(outlineSaturation));
		p.setProperty("outlineOpacity", String.valueOf(outlineOpacity));
		p.setProperty("outlineThickness", String.valueOf(outlineThickness));
		p.setProperty("outlineSpeed", String.valueOf(outlineSpeed));
		p.setProperty("outlineSpread", String.valueOf(outlineSpread));
		p.setProperty("outlineFill", String.valueOf(outlineFill));
		p.setProperty("theme", String.valueOf(theme));
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE)) {
				p.store(w, "Blutgold PvP - true = an, false = aus. Im Spiel mit M einstellbar.");
			}
		} catch (IOException ignored) {
		}
	}

	private static boolean bool(Properties p, String key, boolean def) {
		return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
	}

	private static float num(Properties p, String key, float def) {
		try {
			return Float.parseFloat(p.getProperty(key, String.valueOf(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
