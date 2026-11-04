package de.botarmee;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/** Einstellungen im Menü (gespeichert in config/botarmee.properties) und der zuletzt empfangene Spielstand. */
public final class ClientState {
	private ClientState() {}

	private static BattleConfig config;

	public record TeamStatus(String name, int color, int alive, int start) {}

	public static String state = "IDLE";
	public static boolean hasArena = false;
	public static boolean hud = true;
	public static final List<TeamStatus> teams = new ArrayList<>();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("botarmee.properties");
	}

	public static BattleConfig config() {
		if (config == null) {
			try {
				Path f = file();
				config = Files.exists(f) ? BattleConfig.decode(Files.readString(f, StandardCharsets.UTF_8)) : BattleConfig.defaults();
			} catch (Exception e) {
				config = BattleConfig.defaults();
			}
		}
		return config;
	}

	public static void save() {
		try {
			Files.writeString(file(), config().encode(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	public static void send(String action) {
		save();
		if (ClientPlayNetworking.canSend(Net.ActionPayload.TYPE)) {
			ClientPlayNetworking.send(new Net.ActionPayload(action, config().encode()));
		}
	}

	public static boolean connected() {
		return ClientPlayNetworking.canSend(Net.ActionPayload.TYPE);
	}

	public static void onStatus(String data) {
		String[] parts = data.split(";");
		if (parts.length < 2) return;
		state = parts[0];
		hasArena = parts[1].equals("1");
		teams.clear();
		for (int i = 2; i < parts.length; i++) {
			String[] t = parts[i].split(",");
			if (t.length == 4) {
				try {
					teams.add(new TeamStatus(t[0], Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])));
				} catch (NumberFormatException ignored) {
				}
			} else if (t.length == 1 && i == parts.length - 1) {
				hud = t[0].equals("1");
			}
		}
	}
}
