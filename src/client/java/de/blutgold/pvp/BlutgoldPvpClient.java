package de.blutgold.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class BlutgoldPvpClient implements ClientModInitializer {
	public static final String MOD_ID = "blutgoldpvp";

	private static KeyMapping keyHitboxes;
	private static KeyMapping keyHud;
	private static KeyMapping keySprint;
	private static KeyMapping keyHurtCam;
	private static boolean hurtCamApplied = false;

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		keyHitboxes = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blutgoldpvp.hitboxes", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_H, category));
		keyHud = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blutgoldpvp.hud", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_J, category));
		keySprint = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blutgoldpvp.sprint", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_K, category));
		keyHurtCam = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blutgoldpvp.hurtcam", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_N, category));

		// Rote Hitboxen in der Welt
		LevelExtractionEvents.END_EXTRACTION.register(Hitboxes::extract);
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(Hitboxes::render);

		// Crosshair wird rot, wenn ein Gegner im Visier ist
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, TargetCrosshair::wrap);

		// FPS/CPS, Rüstung und Tränke
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES,
				Identifier.fromNamespaceAndPath(MOD_ID, "pvp_hud"), PvpHud::extract);

		ClientTickEvents.END_CLIENT_TICK.register(BlutgoldPvpClient::tick);
	}

	private static void tick(Minecraft mc) {
		while (keyHitboxes.consumeClick()) {
			Config.hitboxes = !Config.hitboxes;
			notify(mc, "Hitboxen", Config.hitboxes);
		}
		while (keyHud.consumeClick()) {
			Config.hud = !Config.hud;
			notify(mc, "PvP-HUD", Config.hud);
		}
		while (keySprint.consumeClick()) {
			Config.toggleSprint = !Config.toggleSprint;
			if (!Config.toggleSprint) mc.options.keySprint.setDown(false);
			notify(mc, "Toggle-Sprint", Config.toggleSprint);
		}
		while (keyHurtCam.consumeClick()) {
			Config.noHurtCam = !Config.noHurtCam;
			hurtCamApplied = false;
			notify(mc, "Kein Kamera-Wackeln", Config.noHurtCam);
		}

		applyHurtCam(mc);

		// Toggle-Sprint: hält die Sprint-Taste für dich gedrückt, solange du vorwärts läufst
		if (Config.toggleSprint && mc.player != null && mc.screen == null) {
			boolean forward = mc.options.keyUp.isDown();
			mc.options.keySprint.setDown(forward && !mc.player.isShiftKeyDown());
		}
	}

	/** Setzt die Vanilla-Option "Schadensneigung" auf 0 (bzw. zurück). */
	private static void applyHurtCam(Minecraft mc) {
		if (hurtCamApplied) return;
		double current = mc.options.damageTiltStrength().get();
		if (Config.noHurtCam) {
			if (current > 0) Config.savedTilt = current;
			mc.options.damageTiltStrength().set(0.0);
		} else if (current == 0) {
			mc.options.damageTiltStrength().set(Config.savedTilt > 0 ? Config.savedTilt : 1.0);
		}
		Config.save();
		hurtCamApplied = true;
	}

	private static void notify(Minecraft mc, String what, boolean on) {
		Config.save();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("§6[Blutgold PvP] §f" + what + ": " + (on ? "§aAN" : "§cAUS")));
		}
	}
}
