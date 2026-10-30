package de.blutgold.pvp;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** FPS + CPS oben links, aktive Tränke darunter, Rüstung + Waffe unten rechts. */
public final class PvpHud {
	// Farben kommen aus dem gewählten Theme (Menü: M)
	private static int BG, ACCENT, GOLD, WHITE, GREY;

	private static final Deque<Long> leftClicks = new ArrayDeque<>();
	private static final Deque<Long> rightClicks = new ArrayDeque<>();
	private static boolean wasLeft, wasRight;

	private static int frames, fps;
	private static long fpsWindowStart = System.currentTimeMillis();

	private PvpHud() {}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null) return;

		long now = System.currentTimeMillis();
		countFps(now);
		countClicks(mc, now);

		if (!Config.hud) return;
		Theme t = Theme.current();
		BG = Theme.withAlpha(t.bg(), Config.hudOpacity);
		ACCENT = t.accent();
		GOLD = t.highlight();
		WHITE = t.text();
		GREY = t.muted();
		Font font = mc.font;
		int y = 4;

		if (Config.showFpsCps) {
			String fpsText = "FPS " + fps;
			String cpsText = "CPS " + leftClicks.size() + " | " + rightClicks.size();
			y = panel(g, font, 4, y, new String[] {fpsText, cpsText}, new int[] {WHITE, GOLD});
		}

		// Info-Anzeigen
		java.util.List<String> info = new java.util.ArrayList<>();
		java.util.List<Integer> infoColors = new java.util.ArrayList<>();
		if (Config.showCoords) {
			info.add(String.format("XYZ %d %d %d", player.getBlockX(), player.getBlockY(), player.getBlockZ()));
			infoColors.add(WHITE);
		}
		if (Config.showDirection) {
			String[] dirs = {"Süden", "Westen", "Norden", "Osten"};
			int idx = Math.floorMod(Math.round(player.getYRot() / 90f), 4);
			info.add("Richtung " + dirs[idx]);
			infoColors.add(WHITE);
		}
		if (Config.showSpeed) {
			double bps = Math.sqrt(Math.pow(player.getX() - player.xo, 2) + Math.pow(player.getZ() - player.zo, 2)) * 20;
			info.add(String.format("Tempo %.1f m/s", bps));
			infoColors.add(WHITE);
		}
		if (Config.showClock) {
			info.add("Uhr " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
			infoColors.add(WHITE);
		}
		if (Config.showCombo && CombatTracker.combo > 1) {
			info.add("Combo " + CombatTracker.combo + "x");
			infoColors.add(GOLD);
		}
		if (Config.showReach && CombatTracker.lastReach >= 0 && now - CombatTracker.lastHitTime < 2500) {
			info.add(String.format("Reach %.2f", CombatTracker.lastReach));
			infoColors.add(ACCENT);
		}
		if (!info.isEmpty()) {
			y += 3;
			int[] cols = new int[infoColors.size()];
			for (int k = 0; k < cols.length; k++) cols[k] = infoColors.get(k);
			y = panel(g, font, 4, y, info.toArray(new String[0]), cols);
		}

		if (Config.showEffects && !player.getActiveEffects().isEmpty()) {
			y += 3;
			int count = player.getActiveEffects().size();
			String[] lines = new String[count];
			int[] colors = new int[count];
			int i = 0;
			for (MobEffectInstance effect : player.getActiveEffects()) {
				String name = effect.getEffect().value().getDisplayName().getString();
				int level = effect.getAmplifier() + 1;
				lines[i] = name + (level > 1 ? " " + roman(level) : "") + "  " + time(effect.getDuration());
				colors[i] = 0xFF000000 | effect.getEffect().value().getColor();
				i++;
			}
			panel(g, font, 4, y, lines, colors);
		}

		if (Config.showArmor) armor(g, font, player);
	}

	/** Kleines Panel mit rotem Akzent links. Gibt die y-Position darunter zurück. */
	private static int panel(GuiGraphicsExtractor g, Font font, int x, int y, String[] lines, int[] colors) {
		int w = 0;
		for (String l : lines) w = Math.max(w, font.width(l));
		int h = lines.length * 10 + 4;
		if (Config.hudRight) {
			x = g.guiWidth() - x - (w + 10);
			g.fill(x, y, x + w + 10, y + h, BG);
			g.fill(x + w + 8, y, x + w + 10, y + h, ACCENT);
		} else {
			g.fill(x, y, x + w + 10, y + h, BG);
			g.fill(x, y, x + 2, y + h, ACCENT);
		}
		for (int i = 0; i < lines.length; i++) {
			g.text(font, lines[i], x + (Config.hudRight ? 4 : 6), y + 3 + i * 10, colors[i], Config.hudShadow);
		}
		return y + h;
	}

	private static void armor(GuiGraphicsExtractor g, Font font, Player player) {
		ItemStack[] items = {
				player.getItemBySlot(EquipmentSlot.HEAD),
				player.getItemBySlot(EquipmentSlot.CHEST),
				player.getItemBySlot(EquipmentSlot.LEGS),
				player.getItemBySlot(EquipmentSlot.FEET),
				player.getMainHandItem()
		};
		int x = g.guiWidth() - 58;
		int y = g.guiHeight() - 30 - items.length * 18;
		for (ItemStack stack : items) {
			if (stack.isEmpty()) continue;
			g.fill(x - 2, y - 1, x + 56, y + 17, BG);
			g.fill(x - 2, y - 1, x, y + 17, ACCENT);
			g.fakeItem(stack, x + 2, y);
			g.itemDecorations(font, stack, x + 2, y, null);
			if (stack.isDamageableItem()) {
				int left = stack.getMaxDamage() - stack.getDamageValue();
				float ratio = left / (float) stack.getMaxDamage();
				g.text(font, String.valueOf(left), x + 22, y + 5, durabilityColor(ratio), Config.hudShadow);
			} else if (stack.getCount() > 1) {
				g.text(font, "x" + stack.getCount(), x + 22, y + 5, GREY, Config.hudShadow);
			}
			y += 18;
		}
	}

	private static int durabilityColor(float r) {
		if (r > 0.6f) return 0xFF55FF55;
		if (r > 0.3f) return 0xFFFFD24A;
		if (r > 0.1f) return 0xFFFF8A1E;
		return 0xFFFF2626;
	}

	private static void countFps(long now) {
		frames++;
		if (now - fpsWindowStart >= 1000) {
			fps = frames;
			frames = 0;
			fpsWindowStart = now;
		}
	}

	/** Zählt Klicks über die Angriffs-/Benutzen-Taste (jeder Druck = 1 Klick). */
	private static void countClicks(Minecraft mc, long now) {
		boolean left = mc.gui.screen() == null && mc.options.keyAttack.isDown();
		boolean right = mc.gui.screen() == null && mc.options.keyUse.isDown();
		if (left && !wasLeft) leftClicks.addLast(now);
		if (right && !wasRight) rightClicks.addLast(now);
		wasLeft = left;
		wasRight = right;
		while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) leftClicks.pollFirst();
		while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) rightClicks.pollFirst();
	}

	private static String time(int ticks) {
		if (ticks < 0) return "∞";
		int s = ticks / 20;
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}
}
