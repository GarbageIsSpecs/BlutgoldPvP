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
	public static boolean hitboxes = true;
	public static boolean hitboxesPlayersOnly = true;
	public static boolean targetCrosshair = true;
	public static boolean hud = true;
	public static boolean showFpsCps = true;
	public static boolean showArmor = true;
	public static boolean showEffects = true;
	public static boolean toggleSprint = false;
	public static boolean noHurtCam = true;
	/** Ursprüngliche Stärke des Kamera-Wackelns, damit wir sie wiederherstellen können. */
	public static double savedTilt = 1.0;

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
		hitboxes = bool(p, "hitboxes", hitboxes);
		hitboxesPlayersOnly = bool(p, "hitboxesPlayersOnly", hitboxesPlayersOnly);
		targetCrosshair = bool(p, "targetCrosshair", targetCrosshair);
		hud = bool(p, "hud", hud);
		showFpsCps = bool(p, "showFpsCps", showFpsCps);
		showArmor = bool(p, "showArmor", showArmor);
		showEffects = bool(p, "showEffects", showEffects);
		toggleSprint = bool(p, "toggleSprint", toggleSprint);
		noHurtCam = bool(p, "noHurtCam", noHurtCam);
		try {
			savedTilt = Double.parseDouble(p.getProperty("savedTilt", String.valueOf(savedTilt)));
		} catch (NumberFormatException ignored) {
		}
	}

	public static void save() {
		Properties p = new Properties();
		p.setProperty("hitboxes", String.valueOf(hitboxes));
		p.setProperty("hitboxesPlayersOnly", String.valueOf(hitboxesPlayersOnly));
		p.setProperty("targetCrosshair", String.valueOf(targetCrosshair));
		p.setProperty("hud", String.valueOf(hud));
		p.setProperty("showFpsCps", String.valueOf(showFpsCps));
		p.setProperty("showArmor", String.valueOf(showArmor));
		p.setProperty("showEffects", String.valueOf(showEffects));
		p.setProperty("toggleSprint", String.valueOf(toggleSprint));
		p.setProperty("noHurtCam", String.valueOf(noHurtCam));
		p.setProperty("savedTilt", String.valueOf(savedTilt));
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE)) {
				p.store(w, "Blutgold PvP - true = an, false = aus");
			}
		} catch (IOException ignored) {
		}
	}

	private static boolean bool(Properties p, String key, boolean def) {
		return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
	}
}
