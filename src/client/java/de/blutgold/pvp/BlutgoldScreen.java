package de.blutgold.pvp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Das Blutgold-PvP-Menü (Taste M): Kategorien links, Einstellungen rechts.
 * Komplett selbst gezeichnet, damit es zum gewählten Theme passt.
 */
public class BlutgoldScreen extends Screen {
	private static final int TOP = 24;
	private static final int SIDEBAR = 98;
	private static final int CAT_H = 22;

	/** Zuletzt geöffnete Kategorie merken. */
	private static int selected = 0;

	private final List<Category> categories = new ArrayList<>();
	private double scroll = 0;
	private Slider dragging = null;

	// Layout (wird in init berechnet)
	private int px, py, pw, ph;

	public BlutgoldScreen() {
		super(Component.literal("Blutgold PvP"));
		buildCategories();
	}

	// ---------------------------------------------------------------- Inhalte

	private void buildCategories() {
		categories.add(new Category("HUD", List.of(
				new Toggle("HUD anzeigen", () -> Config.hud, v -> Config.hud = v),
				new Toggle("FPS & CPS", () -> Config.showFpsCps, v -> Config.showFpsCps = v),
				new Toggle("Rüstung & Waffe", () -> Config.showArmor, v -> Config.showArmor = v),
				new Toggle("Trank-Effekte", () -> Config.showEffects, v -> Config.showEffects = v),
				new Slider("Hintergrund-Deckkraft", 0, 1, 0.05, () -> Config.hudOpacity, v -> Config.hudOpacity = (float) v, BlutgoldScreen::percent)
		)));
		categories.add(new Category("Hitboxen", List.of(
				new Toggle("Hitboxen anzeigen", () -> Config.hitboxes, v -> Config.hitboxes = v),
				new Toggle("Nur Spieler", () -> Config.hitboxesPlayersOnly, v -> Config.hitboxesPlayersOnly = v),
				new Toggle("Ziel-Crosshair", () -> Config.targetCrosshair, v -> Config.targetCrosshair = v)
		)));
		categories.add(new Category("Block-Rahmen", List.of(
				new Preview(),
				new Toggle("Rahmen anzeigen", () -> Config.rainbowOutline, v -> Config.rainbowOutline = v),
				new Choice("Farbmodus", RainbowOutline.MODES, () -> Config.outlineMode, v -> Config.outlineMode = v),
				new Slider("Farbe", 0, 1, 0.01, () -> Config.outlineHue, v -> Config.outlineHue = (float) v, v -> "")
						.hueTrack().visibleIf(() -> Config.outlineMode == 1),
				new Slider("Sättigung", 0, 1, 0.05, () -> Config.outlineSaturation, v -> Config.outlineSaturation = (float) v, BlutgoldScreen::percent)
						.visibleIf(() -> Config.outlineMode != 2),
				new Slider("Deckkraft", 0.1, 1, 0.05, () -> Config.outlineOpacity, v -> Config.outlineOpacity = (float) v, BlutgoldScreen::percent),
				new Slider("Dicke", 1, 10, 1, () -> Config.outlineThickness, v -> Config.outlineThickness = (float) v, v -> String.valueOf((int) Math.round(v))),
				new Slider("Geschwindigkeit", 0, 2, 0.05, () -> Config.outlineSpeed, v -> Config.outlineSpeed = (float) v, v -> String.format("%.2fx", v)),
				new Slider("Farb-Dichte", 0.1, 2, 0.05, () -> Config.outlineSpread, v -> Config.outlineSpread = (float) v, v -> String.format("%.2f", v))
						.visibleIf(() -> Config.outlineMode != 1),
				new Slider("Füllung", 0, 0.5, 0.02, () -> Config.outlineFill, v -> Config.outlineFill = (float) v, v -> v < 0.005 ? "aus" : percent(v))
		)));
		categories.add(new Category("Komfort", List.of(
				new Toggle("Toggle-Sprint", () -> Config.toggleSprint, v -> {
					Config.toggleSprint = v;
					if (!v && minecraft != null) minecraft.options.keySprint.setDown(false);
				}),
				new Toggle("Kein Kamera-Wackeln", () -> Config.noHurtCam, v -> {
					Config.noHurtCam = v;
					BlutgoldPvpClient.reapplyHurtCam();
				})
		)));
		List<Row> themes = new ArrayList<>();
		for (int i = 0; i < Theme.ALL.length; i++) themes.add(new ThemeCard(i));
		categories.add(new Category("Themes", themes));
	}

	private static String percent(double v) {
		return Math.round(v * 100) + "%";
	}

	// ---------------------------------------------------------------- Layout & Zeichnen

	@Override
	protected void init() {
		pw = Math.min(400, width - 16);
		ph = Math.min(260, height - 16);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		clampScroll();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// eigener, leichter Hintergrund (kein Blur), damit man die Welt dahinter sieht
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Theme t = Theme.current();
		Font font = this.font;

		g.fill(0, 0, width, height, 0x66000000);

		// Fenster mit Schatten
		g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x55000000);
		g.fill(px, py, px + pw, py + ph, t.bg());
		outline(g, px, py, pw, ph, Theme.withAlpha(t.accent(), 0.35f));

		// Kopfzeile
		g.fill(px, py, px + pw, py + TOP, t.panel());
		g.fill(px, py + TOP - 1, px + pw, py + TOP, Theme.withAlpha(t.accent(), 0.6f));
		g.fill(px + 8, py + 8, px + 12, py + 16, t.accent());
		g.fill(px + 12, py + 8, px + 14, py + 16, t.highlight());
		g.text(font, "BLUTGOLD", px + 19, py + 8, t.accent(), false);
		g.text(font, "PVP", px + 19 + font.width("BLUTGOLD "), py + 8, t.text(), false);
		String hint = "M / ESC zum Schließen";
		g.text(font, hint, px + pw - 8 - font.width(hint), py + 8, t.muted(), false);

		// Seitenleiste
		int sy = py + TOP;
		g.fill(px, sy, px + SIDEBAR, py + ph, t.panel());
		g.fill(px + SIDEBAR - 1, sy, px + SIDEBAR, py + ph, Theme.withAlpha(t.accent(), 0.2f));
		for (int i = 0; i < categories.size(); i++) {
			int cy = sy + 6 + i * CAT_H;
			boolean sel = i == selected;
			boolean hover = in(mouseX, mouseY, px, cy, SIDEBAR - 1, CAT_H);
			if (sel) {
				g.fill(px, cy, px + SIDEBAR - 1, cy + CAT_H, t.panelLight());
				g.fill(px, cy, px + 2, cy + CAT_H, t.accent());
			} else if (hover) {
				g.fill(px, cy, px + SIDEBAR - 1, cy + CAT_H, Theme.withAlpha(t.panelLight(), 0.6f));
			}
			g.text(font, categories.get(i).name, px + 12, cy + 7, sel ? t.text() : t.muted(), false);
		}
		String ver = "v1.2.0";
		g.text(font, ver, px + 12, py + ph - 14, Theme.withAlpha(t.muted(), 0.6f), false);

		// Inhalt
		Category cat = categories.get(selected);
		int cx = contentX(), cw = contentW();
		int top = contentTop(), bottom = contentBottom();
		g.text(font, cat.name.toUpperCase(), cx, top, t.highlight(), false);
		g.fill(cx, top + 11, cx + cw, top + 12, Theme.withAlpha(t.muted(), 0.25f));

		int y = top + 18 - (int) scroll;
		for (Row row : cat.rows) {
			if (!row.visible()) continue;
			int h = row.height();
			if (y + h > top + 16 && y < bottom) {
				boolean hover = in(mouseX, mouseY, cx, y, cw, h) && mouseY > top + 16;
				row.draw(g, font, t, cx, y, cw, hover);
			}
			y += h + 4;
		}

		// Scroll-Hinweis
		if (maxScroll() > 0) {
			int trackH = bottom - (top + 18);
			int barH = Math.max(14, trackH * trackH / (trackH + maxScroll()));
			int barY = top + 18 + (int) ((trackH - barH) * (scroll / maxScroll()));
			g.fill(px + pw - 4, barY, px + pw - 2, barY + barH, Theme.withAlpha(t.accent(), 0.7f));
		}
	}

	private int contentX() { return px + SIDEBAR + 12; }
	private int contentW() { return pw - SIDEBAR - 24; }
	private int contentTop() { return py + TOP + 10; }
	private int contentBottom() { return py + ph - 8; }

	private int contentHeight() {
		int total = 0;
		for (Row r : categories.get(selected).rows) if (r.visible()) total += r.height() + 4;
		return total;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - (contentBottom() - (contentTop() + 18)));
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		g.fill(x, y, x + w, y + 1, c);
		g.fill(x, y + h - 1, x + w, y + h, c);
		g.fill(x, y, x + 1, y + h, c);
		g.fill(x + w - 1, y, x + w, y + h, c);
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------------------------------------------------------------- Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int button = event.button();

		// Kategorie wechseln
		int sy = py + TOP;
		for (int i = 0; i < categories.size(); i++) {
			int cy = sy + 6 + i * CAT_H;
			if (in(mx, my, px, cy, SIDEBAR - 1, CAT_H)) {
				if (selected != i) {
					selected = i;
					scroll = 0;
				}
				return true;
			}
		}

		// Zeilen
		int cx = contentX(), cw = contentW();
		int top = contentTop();
		if (my > top + 16 && my < contentBottom()) {
			int y = top + 18 - (int) scroll;
			for (Row row : categories.get(selected).rows) {
				if (!row.visible()) continue;
				int h = row.height();
				if (in(mx, my, cx, y, cw, h)) {
					if (row instanceof Slider s) {
						dragging = s;
						s.setFromMouse(mx, cx, cw);
					} else {
						row.click(button);
					}
					clampScroll();
					Config.save();
					return true;
				}
				y += h + 4;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			dragging.setFromMouse(event.x(), contentX(), contentW());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			Config.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll -= sy * 16;
		clampScroll();
		return true;
	}

	@Override
	public void onClose() {
		Config.save();
		super.onClose();
	}

	// ---------------------------------------------------------------- Zeilen-Typen

	private record Category(String name, List<Row> rows) {}

	private abstract static class Row {
		private BooleanSupplier visible = () -> true;

		int height() { return 20; }
		boolean visible() { return visible.getAsBoolean(); }

		Row visibleIf(BooleanSupplier v) {
			this.visible = v;
			return this;
		}

		abstract void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover);
		void click(int button) {}

		static void rowBg(GuiGraphicsExtractor g, Theme t, int x, int y, int w, int h, boolean hover) {
			g.fill(x, y, x + w, y + h, hover ? t.panelLight() : t.panel());
		}
	}

	private static final class Toggle extends Row {
		private final String label;
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;

		Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
			this.label = label;
			this.get = get;
			this.set = set;
		}

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			rowBg(g, t, x, y, w, height(), hover);
			boolean on = get.getAsBoolean();
			g.text(font, label, x + 8, y + 6, t.text(), false);
			int sw = 22, sh = 10, sx = x + w - sw - 8, sy = y + 5;
			g.fill(sx, sy, sx + sw, sy + sh, on ? t.accent() : Theme.mix(t.panelLight(), 0xFFFFFFFF, 0.12f));
			int kx = on ? sx + sw - 9 : sx + 1;
			g.fill(kx, sy + 1, kx + 8, sy + sh - 1, on ? 0xFFFFFFFF : t.muted());
		}

		@Override
		void click(int button) {
			set.accept(!get.getAsBoolean());
		}
	}

	private final class Slider extends Row {
		private final String label;
		private final double min, max, step;
		private final DoubleSupplier get;
		private final java.util.function.DoubleConsumer set;
		private final DoubleFunction<String> fmt;
		private boolean hue = false;

		Slider(String label, double min, double max, double step, DoubleSupplier get,
				java.util.function.DoubleConsumer set, DoubleFunction<String> fmt) {
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.fmt = fmt;
		}

		Slider hueTrack() {
			this.hue = true;
			return this;
		}

		@Override
		int height() { return 26; }

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			rowBg(g, t, x, y, w, height(), hover || dragging == this);
			double v = get.getAsDouble();
			g.text(font, label, x + 8, y + 4, t.text(), false);
			String val = fmt.apply(v);
			g.text(font, val, x + w - 8 - font.width(val), y + 4, t.muted(), false);

			int tx = x + 8, tw = w - 16, ty = y + 17;
			double frac = (v - min) / (max - min);
			int fx = tx + (int) Math.round(frac * tw);
			if (hue) {
				for (int i = 0; i < tw; i++) {
					g.fill(tx + i, ty - 1, tx + i + 1, ty + 3, Theme.hsv(i / (double) tw, Config.outlineSaturation, 1f));
				}
			} else {
				g.fill(tx, ty, tx + tw, ty + 2, Theme.mix(t.panelLight(), 0xFFFFFFFF, 0.12f));
				g.fill(tx, ty, fx, ty + 2, t.accent());
			}
			g.fill(fx - 2, ty - 3, fx + 2, ty + 5, 0xFFFFFFFF);
		}

		void setFromMouse(double mx, int cx, int cw) {
			int tx = cx + 8, tw = cw - 16;
			double frac = Math.max(0, Math.min(1, (mx - tx) / tw));
			double v = min + frac * (max - min);
			v = Math.round(v / step) * step;
			v = Math.max(min, Math.min(max, v));
			set.accept(v);
		}
	}

	private static final class Choice extends Row {
		private final String label;
		private final String[] options;
		private final IntSupplier get;
		private final IntConsumer set;

		Choice(String label, String[] options, IntSupplier get, IntConsumer set) {
			this.label = label;
			this.options = options;
			this.get = get;
			this.set = set;
		}

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			rowBg(g, t, x, y, w, height(), hover);
			g.text(font, label, x + 8, y + 6, t.text(), false);
			int i = Math.floorMod(get.getAsInt(), options.length);
			String val = "< " + options[i] + " >";
			g.text(font, val, x + w - 8 - font.width(val), y + 6, t.highlight(), false);
		}

		@Override
		void click(int button) {
			int d = button == 1 ? -1 : 1;
			set.accept(Math.floorMod(get.getAsInt() + d, options.length));
		}
	}

	/** Animierte Farbvorschau des Block-Rahmens. */
	private static final class Preview extends Row {
		@Override
		int height() { return 22; }

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			g.fill(x, y, x + w, y + height(), t.panel());
			double time = RainbowOutline.time();
			int bx = x + 6, bw = w - 12, by = y + 5, bh = height() - 10;
			// Rahmen-Vorschau: vier Seiten eines Kastens, Farbe läuft drumherum
			int perimeter = 2 * (bw + bh);
			int thick = Math.max(1, Math.round(Config.outlineThickness / 3f));
			for (int i = 0; i < perimeter; i += 2) {
				int c = Theme.withAlpha(RainbowOutline.colorAt(i / 40.0, time), Config.outlineOpacity);
				int ex, ey;
				if (i < bw) { ex = bx + i; ey = by; }
				else if (i < bw + bh) { ex = bx + bw; ey = by + (i - bw); }
				else if (i < 2 * bw + bh) { ex = bx + bw - (i - bw - bh); ey = by + bh; }
				else { ex = bx; ey = by + bh - (i - 2 * bw - bh); }
				g.fill(ex, ey, ex + Math.max(2, thick), ey + thick, c);
			}
			if (Config.outlineFill > 0.005f) {
				g.fill(bx + thick, by + thick, bx + bw, by + bh, Theme.withAlpha(RainbowOutline.colorAt(0, time), Config.outlineFill));
			}
			String s = Config.rainbowOutline ? "Vorschau" : "Vorschau (Rahmen ist aus)";
			g.text(font, s, x + w / 2 - font.width(s) / 2, y + 7, t.muted(), false);
		}
	}

	/** Karte zur Theme-Auswahl. */
	private static final class ThemeCard extends Row {
		private final int index;

		ThemeCard(int index) {
			this.index = index;
		}

		@Override
		int height() { return 24; }

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			Theme th = Theme.ALL[index];
			boolean active = Config.theme == index;
			g.fill(x, y, x + w, y + height(), th.panel());
			if (active) {
				g.fill(x, y, x + 2, y + height(), th.accent());
				outline(g, x, y, w, height(), Theme.withAlpha(th.accent(), 0.8f));
			} else if (hover) {
				outline(g, x, y, w, height(), Theme.withAlpha(th.muted(), 0.6f));
			}
			g.text(font, th.name(), x + 10, y + 8, th.text(), false);
			// Farbmuster
			int[] sw = {th.accent(), th.highlight(), th.panelLight(), th.text()};
			int sx = x + w - 8 - sw.length * 14;
			for (int c : sw) {
				g.fill(sx, y + 6, sx + 10, y + 18, c);
				sx += 14;
			}
			if (active) {
				String a = "aktiv";
				g.text(font, a, x + w - 8 - sw.length * 14 - 8 - font.width(a), y + 8, th.highlight(), false);
			}
		}

		@Override
		void click(int button) {
			Config.theme = index;
		}
	}
}
