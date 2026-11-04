package de.botarmee;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public class BotArmeeClient implements ClientModInitializer {
	private static KeyMapping keyMenu;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(BotArmee.BOT, BotRenderer::new);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BotArmee.MOD_ID, "main"));
		keyMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.botarmee.menu", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_B, category));

		ClientPlayNetworking.registerGlobalReceiver(Net.StatusPayload.TYPE, (payload, context) -> ClientState.onStatus(payload.data()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientState.state = "IDLE";
			ClientState.teams.clear();
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (keyMenu.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new ArmeeScreen());
			}
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(BotArmee.MOD_ID, "counter"), BotArmeeClient::drawHud);
	}

	/** Zähler oben in der Mitte: Teamname, lebende Bots und ein Balken. */
	private static void drawHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!ClientState.hud || ClientState.teams.isEmpty()) return;
		String st = ClientState.state;
		if (!(st.equals("FIGHT") || st.equals("PAUSED") || st.equals("COUNTDOWN") || st.equals("OVER") || st.equals("READY"))) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int sw = g.guiWidth();
		int n = ClientState.teams.size();
		int boxW = Math.min(110, (sw - 40) / n);
		int totalW = n * boxW + (n - 1) * 6;
		int x = (sw - totalW) / 2, y = 6;
		for (ClientState.TeamStatus t : ClientState.teams) {
			int rgb = BattleConfig.COLOR_RGB[Math.floorMod(t.color(), BattleConfig.COLOR_RGB.length)];
			g.fill(x, y, x + boxW, y + 26, 0x99000000);
			g.fill(x, y, x + boxW, y + 1, rgb);
			String name = t.name();
			while (name.length() > 1 && font.width(name) > boxW - 34) name = name.substring(0, name.length() - 1);
			g.text(font, name, x + 4, y + 4, 0xFFFFFFFF, true);
			String c = String.valueOf(t.alive());
			g.pose().pushMatrix();
			g.pose().translate(x + boxW - 4 - font.width(c) * 1.5F, y + 3);
			g.pose().scale(1.5F, 1.5F);
			g.text(font, c, 0, 0, t.alive() == 0 ? 0xFF777777 : rgb, true);
			g.pose().popMatrix();
			int bw = boxW - 8;
			int fill = t.start() == 0 ? 0 : Math.round(bw * t.alive() / (float) t.start());
			g.fill(x + 4, y + 18, x + 4 + bw, y + 21, 0x55FFFFFF);
			g.fill(x + 4, y + 18, x + 4 + fill, y + 21, rgb);
			x += boxW + 6;
		}
	}
}
