package de.botarmee;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Eigener Render-Zustand für Bots. Absichtlich KEIN AvatarRenderState: Lunar Client behandelt
 * alles mit AvatarRenderState als echten Spieler und stürzt sonst ab.
 */
public class BotRenderState extends HumanoidRenderState {
	public PlayerSkin skin;
	public boolean slim;
}
