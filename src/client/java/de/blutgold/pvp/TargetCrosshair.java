package de.blutgold.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * Ersetzt das Vanilla-Crosshair, solange ein Gegner in Schlagreichweite im Visier ist.
 * Es nutzt nur, was das Spiel ohnehin weiß (dasselbe wie der Vanilla-Angriffsindikator).
 */
public final class TargetCrosshair {
	private static final int RED = 0xFFFF2626;
	private static final int ORANGE = 0xFFFF8A1E;
	private static final int OUTLINE = 0xFF2A0000;

	private TargetCrosshair() {}

	public static HudElement wrap(HudElement original) {
		return (graphics, deltaTracker) -> {
			Entity target = currentTarget();
			if (target == null) {
				original.extractRenderState(graphics, deltaTracker);
			} else {
				draw(graphics, target instanceof Player ? Theme.current().accent() : Theme.current().highlight());
			}
		};
	}

	private static Entity currentTarget() {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.targetCrosshair || mc.player == null || mc.player.isSpectator()) return null;
		if (!mc.options.getCameraType().isFirstPerson()) return null;
		if (mc.hitResult instanceof EntityHitResult hit
				&& hit.getEntity() instanceof LivingEntity living
				&& living.isAlive()) {
			if (Config.hitboxesPlayersOnly && !(living instanceof Player)) return null;
			return living;
		}
		return null;
	}

	private static void draw(GuiGraphicsExtractor g, int color) {
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		int gap = Math.round(Config.crosshairGap), len = 4;
		// vier Arme mit dunklem Rand
		bar(g, cx - gap - len, cy, len, 1, color);
		bar(g, cx + gap + 1, cy, len, 1, color);
		bar(g, cx, cy - gap - len, 1, len, color);
		bar(g, cx, cy + gap + 1, 1, len, color);
		// Mittelpunkt
		bar(g, cx, cy, 1, 1, color);
		// kleine Eck-Klammern
		int r = 5 + gap;
		if (Config.crosshairBrackets) {
		corner(g, cx - r, cy - r, 1, 1, color);
		corner(g, cx + r, cy - r, -1, 1, color);
		corner(g, cx - r, cy + r, 1, -1, color);
		corner(g, cx + r, cy + r, -1, -1, color);
		}

		// Schlag-Abklingzeit als kleiner Balken (ersetzt den Vanilla-Indikator)
		Minecraft mc = Minecraft.getInstance();
		float strength = mc.player.getAttackStrengthScale(0.0f);
		if (Config.crosshairCooldown && strength < 1.0f) {
			int w = 15;
			int filled = (int) (strength * w);
			g.fill(cx - 8, cy + 10, cx - 8 + w + 2, cy + 13, 0xA0000000);
			g.fill(cx - 7, cy + 11, cx - 7 + filled, cy + 12, color);
		}
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTLINE);
		g.fill(x, y, x + w, y + h, color);
	}

	private static void corner(GuiGraphicsExtractor g, int x, int y, int dx, int dy, int color) {
		int x2 = x + dx * 3;
		int y2 = y + dy * 3;
		g.fill(Math.min(x, x2), y, Math.max(x, x2) + 1, y + 1, color);
		g.fill(x, Math.min(y, y2), x + 1, Math.max(y, y2) + 1, color);
	}
}
