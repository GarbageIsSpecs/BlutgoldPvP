package de.botarmee;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Skins für die Bots: die 18 Standard-Skins von Minecraft oder eigene PNGs aus
 * {@code config/botarmee/skins/}. Jeder Bot bekommt anhand seiner Zufallszahl immer denselben Skin.
 */
public final class Skins {
	private Skins() {}

	private static final String[] NAMES = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};
	private static final List<PlayerSkin> DEFAULTS = new ArrayList<>();
	private static final List<PlayerSkin> CLASSIC = new ArrayList<>();
	private static final List<PlayerSkin> CUSTOM = new ArrayList<>();
	private static boolean customLoaded = false;

	static {
		for (String n : NAMES) {
			DEFAULTS.add(def("entity/player/slim/" + n, PlayerModelType.SLIM));
			DEFAULTS.add(def("entity/player/wide/" + n, PlayerModelType.WIDE));
		}
		CLASSIC.add(def("entity/player/wide/steve", PlayerModelType.WIDE));
		CLASSIC.add(def("entity/player/slim/alex", PlayerModelType.SLIM));
	}

	private static PlayerSkin def(String path, PlayerModelType type) {
		return new PlayerSkin(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(path)), null, null, type, true);
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("botarmee").resolve("skins");
	}

	public static PlayerSkin get(int seed) {
		int mode = ClientState.config().skinMode;
		List<PlayerSkin> list = DEFAULTS;
		if (mode == 1) list = CLASSIC;
		if (mode == 2) {
			if (!customLoaded) loadCustom();
			if (!CUSTOM.isEmpty()) list = CUSTOM;
		}
		return list.get(Math.floorMod(seed, list.size()));
	}

	public static int customCount() {
		if (!customLoaded) loadCustom();
		return CUSTOM.size();
	}

	/** Lädt alle PNG-Skins (64x64) aus dem Ordner. Dateiname mit "slim" = dünne Arme. */
	public static void loadCustom() {
		customLoaded = true;
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < CUSTOM.size(); i++) {
			mc.getTextureManager().release(Identifier.fromNamespaceAndPath(BotArmee.MOD_ID, "skins/custom_" + i));
		}
		CUSTOM.clear();
		Path dir = folder();
		try {
			Files.createDirectories(dir);
		} catch (Exception ignored) {
		}
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir)) {
			s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().forEach(files::add);
		} catch (Exception ignored) {
		}
		for (Path file : files) {
			try (InputStream in = Files.newInputStream(file)) {
				NativeImage img = NativeImage.read(in);
				if (img.getWidth() != 64 || img.getHeight() != 64) {
					img.close();
					continue;
				}
				String name = file.getFileName().toString().toLowerCase();
				// Erkennung dünner Arme: bei Slim-Skins ist dieser Pixel durchsichtig
				boolean slim = name.contains("slim") || ((img.getPixel(54, 20) >>> 24) == 0 && !name.contains("wide"));
				Identifier id = Identifier.fromNamespaceAndPath(BotArmee.MOD_ID, "skins/custom_" + CUSTOM.size());
				mc.getTextureManager().register(id, new DynamicTexture(() -> "botarmee " + name, img));
				CUSTOM.add(new PlayerSkin(new ClientAsset.DownloadedTexture(id, ""), null, null,
						slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, true));
			} catch (Exception e) {
				// kaputte Datei überspringen
			}
		}
	}
}
