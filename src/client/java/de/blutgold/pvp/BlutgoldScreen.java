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

	private static final String[] TAB_NAMES = {"PvP", "Cosmetics", "Design", "Info"};
	/** Zuletzt geöffneter Tab und Kategorie je Tab merken. */
	private static int tab = 0;
	private static final int[] selectedPerTab = new int[TAB_NAMES.length];

	private final List<List<Category>> tabs = new ArrayList<>();
	private double scroll = 0;
	private Slider dragging = null;

	// Layout (wird in init berechnet)
	private int px, py, pw, ph;

	public BlutgoldScreen() {
		super(Component.literal("Blutgold PvP"));
		for (int i = 0; i < TAB_NAMES.length; i++) tabs.add(new ArrayList<>());
		buildPvp(tabs.get(0));
		buildCosmetics(tabs.get(1));
		buildDesign(tabs.get(2));
		buildInfo(tabs.get(3));
	}

	private List<Category> cats() {
		return tabs.get(tab);
	}

	private int selected() {
		return Math.min(selectedPerTab[tab], cats().size() - 1);
	}

	// ---------------------------------------------------------------- Inhalte

	private void buildPvp(List<Category> c) {
		c.add(new Category("HUD", List.of(
				new Header("Anzeigen"),
				new Toggle("HUD anzeigen", () -> Config.hud, v -> Config.hud = v),
				new Toggle("FPS & CPS", () -> Config.showFpsCps, v -> Config.showFpsCps = v),
				new Toggle("Rüstung & Waffe", () -> Config.showArmor, v -> Config.showArmor = v),
				new Toggle("Trank-Effekte", () -> Config.showEffects, v -> Config.showEffects = v),
				new Header("Aussehen"),
				new Choice("Position", new String[] {"Links", "Rechts"}, () -> Config.hudRight ? 1 : 0, v -> Config.hudRight = v == 1),
				new Toggle("Text-Schatten", () -> Config.hudShadow, v -> Config.hudShadow = v),
				new Slider("Hintergrund-Deckkraft", 0, 1, 0.05, () -> Config.hudOpacity, v -> Config.hudOpacity = (float) v, BlutgoldScreen::percent)
		)));
		c.add(new Category("Info-Anzeigen", List.of(
				new Header("Welt"),
				new Toggle("Koordinaten", () -> Config.showCoords, v -> Config.showCoords = v),
				new Toggle("Himmelsrichtung", () -> Config.showDirection, v -> Config.showDirection = v),
				new Toggle("Lauftempo", () -> Config.showSpeed, v -> Config.showSpeed = v),
				new Toggle("Uhrzeit", () -> Config.showClock, v -> Config.showClock = v),
				new Header("Kampf"),
				new Toggle("Combo-Zähler", () -> Config.showCombo, v -> Config.showCombo = v),
				new Toggle("Reichweite pro Schlag", () -> Config.showReach, v -> Config.showReach = v)
		)));
		c.add(new Category("Hitboxen", List.of(
				new Toggle("Hitboxen anzeigen", () -> Config.hitboxes, v -> Config.hitboxes = v),
				new Toggle("Nur Spieler", () -> Config.hitboxesPlayersOnly, v -> Config.hitboxesPlayersOnly = v),
				new Toggle("Linie auf Augenhöhe", () -> Config.hitboxEyeLine, v -> Config.hitboxEyeLine = v),
				new Toggle("Aufblitzen bei Treffer", () -> Config.hitboxFlash, v -> Config.hitboxFlash = v)
		)));
		c.add(new Category("Crosshair", List.of(
				new Toggle("Ziel-Crosshair", () -> Config.targetCrosshair, v -> Config.targetCrosshair = v),
				new Toggle("Abklingzeit-Balken", () -> Config.crosshairCooldown, v -> Config.crosshairCooldown = v),
				new Toggle("Eck-Klammern", () -> Config.crosshairBrackets, v -> Config.crosshairBrackets = v),
				new Slider("Abstand zur Mitte", 1, 6, 1, () -> Config.crosshairGap, v -> Config.crosshairGap = (float) v, v -> String.valueOf((int) Math.round(v)))
		)));
		c.add(new Category("Block-Rahmen", List.of(
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
		c.add(new Category("Komfort", List.of(
				new Toggle("Toggle-Sprint", () -> Config.toggleSprint, v -> {
					Config.toggleSprint = v;
					if (!v && minecraft != null) minecraft.options.keySprint.setDown(false);
				}),
				new Toggle("Kein Kamera-Wackeln", () -> Config.noHurtCam, v -> {
					Config.noHurtCam = v;
					BlutgoldPvpClient.reapplyHurtCam();
				})
		)));
	}

	private void buildCosmetics(List<Category> c) {
		c.add(new Category("Flügel", List.of(
				new Label("Nur für dich sichtbar – drück F5, um sie zu sehen."),
				new Toggle("Flügel tragen", () -> Config.wings, v -> Config.wings = v),
				new Choice("Farbe", Cosmetics.WING_COLORS, () -> Config.wingColor, v -> Config.wingColor = v),
				new Toggle("Leuchtende Adern", () -> Config.wingVeins, v -> Config.wingVeins = v),
				new Slider("Größe", 0.5, 1.6, 0.05, () -> Config.wingScale, v -> Config.wingScale = (float) v, v -> String.format("%.2fx", v)),
				new Slider("Ausbreitung", 0, 1, 0.05, () -> Config.wingSpread, v -> Config.wingSpread = (float) v, BlutgoldScreen::percent),
				new Slider("Schlag-Tempo", 0, 2.5, 0.05, () -> Config.wingFlapSpeed, v -> Config.wingFlapSpeed = (float) v, v -> String.format("%.2fx", v)),
				new Slider("Deckkraft", 0.2, 1, 0.05, () -> Config.wingOpacity, v -> Config.wingOpacity = (float) v, BlutgoldScreen::percent),
				new Toggle("Nur beim Laufen schlagen", () -> Config.wingFlapOnlyMoving, v -> Config.wingFlapOnlyMoving = v)
		)));
		c.add(new Category("Heiligenschein", List.of(
				new Label("Nur für dich sichtbar – drück F5, um ihn zu sehen."),
				new Toggle("Heiligenschein tragen", () -> Config.halo, v -> Config.halo = v),
				new Choice("Farbe", Cosmetics.HALO_COLORS, () -> Config.haloColor, v -> Config.haloColor = v),
				new Toggle("Zacken", () -> Config.haloSpikes, v -> Config.haloSpikes = v),
				new Toggle("Schweben", () -> Config.haloBob, v -> Config.haloBob = v),
				new Slider("Größe", 0.5, 1.8, 0.05, () -> Config.haloSize, v -> Config.haloSize = (float) v, v -> String.format("%.2fx", v)),
				new Slider("Höhe", 0.15, 0.8, 0.05, () -> Config.haloHeight, v -> Config.haloHeight = (float) v, v -> String.format("%.2f", v)),
				new Slider("Dreh-Tempo", 0, 3, 0.1, () -> Config.haloSpin, v -> Config.haloSpin = (float) v, v -> String.format("%.1fx", v))
		)));
		c.add(new Category("Partikel", List.of(
				new Toggle("Partikel aktiv", () -> Config.particles, v -> Config.particles = v),
				new Choice("Art", Cosmetics.PARTICLE_TYPES, () -> Config.particleType, v -> Config.particleType = v),
				new Slider("Menge", 0.05, 1, 0.05, () -> Config.particleAmount, v -> Config.particleAmount = (float) v, BlutgoldScreen::percent),
				new Toggle("Nur beim Laufen", () -> Config.particlesOnlyMoving, v -> Config.particlesOnlyMoving = v),
				new Toggle("Auch in Ego-Sicht", () -> Config.particlesFirstPerson, v -> Config.particlesFirstPerson = v)
		)));
	}

	private void buildDesign(List<Category> c) {
		List<Row> themes = new ArrayList<>();
		for (int i = 0; i < Theme.ALL.length; i++) themes.add(new ThemeCard(i));
		c.add(new Category("Themes", themes));
		c.add(new Category("Menü", List.of(
				new Slider("Hintergrund abdunkeln", 0, 0.9, 0.05, () -> Config.menuDim, v -> Config.menuDim = (float) v, BlutgoldScreen::percent),
				new Label("Das Theme färbt Menü, HUD, Crosshair, Hitboxen,"),
				new Label("Theme-Flügel und den Theme-Rahmen.")
		)));
	}

	private void buildInfo(List<Category> c) {
		c.add(new Category("Über", List.of(
				new Header("Blutgold PvP v1.3.0"),
				new Label("Gemacht von Ibrahim (GarbageIsSpecs)."),
				new Label("Reine Anzeige-Hilfen und Cosmetics – keine Cheats."),
				new Label("Cosmetics sieht nur, wer die Mod selbst hat."),
				new Label("Einstellungen: config/blutgoldpvp.properties")
		)));
		List<Row> keys = new ArrayList<>();
		keys.add(new Label("Ändern: Optionen > Steuerung > Tastenbelegung"));
		String[][] list = BlutgoldPvpClient.keyList();
		for (String[] k : list) keys.add(new KeyRow(k[0], k[1]));
		c.add(new Category("Tasten", keys));
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

		g.fill(0, 0, width, height, Theme.withAlpha(0xFF000000, Config.menuDim));

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
		// Tabs oben rechts
		int tx = px + pw - 6;
		for (int i = TAB_NAMES.length - 1; i >= 0; i--) {
			int w = font.width(TAB_NAMES[i]) + 14;
			tx -= w;
			boolean sel = i == tab;
			boolean hov = in(mouseX, mouseY, tx, py + 3, w, TOP - 6);
			if (sel) {
				g.fill(tx, py + 3, tx + w, py + TOP - 3, t.panelLight());
				g.fill(tx, py + TOP - 4, tx + w, py + TOP - 3, t.accent());
			} else if (hov) {
				g.fill(tx, py + 3, tx + w, py + TOP - 3, Theme.withAlpha(t.panelLight(), 0.6f));
			}
			g.text(font, TAB_NAMES[i], tx + 7, py + 8, sel ? t.text() : t.muted(), false);
			tx -= 3;
		}

		// Seitenleiste
		int sy = py + TOP;
		g.fill(px, sy, px + SIDEBAR, py + ph, t.panel());
		g.fill(px + SIDEBAR - 1, sy, px + SIDEBAR, py + ph, Theme.withAlpha(t.accent(), 0.2f));
		List<Category> categories = cats();
		int selected = selected();
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
		String ver = "v1.3.0  ·  ESC";
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
		for (Row r : cats().get(selected()).rows) if (r.visible()) total += r.height() + 4;
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

		// Tab wechseln
		int tx = px + pw - 6;
		for (int i = TAB_NAMES.length - 1; i >= 0; i--) {
			int w = font.width(TAB_NAMES[i]) + 14;
			tx -= w;
			if (in(mx, my, tx, py + 3, w, TOP - 6)) {
				if (tab != i) {
					tab = i;
					scroll = 0;
				}
				return true;
			}
			tx -= 3;
		}

		// Kategorie wechseln
		int sy = py + TOP;
		List<Category> categories = cats();
		for (int i = 0; i < categories.size(); i++) {
			int cy = sy + 6 + i * CAT_H;
			if (in(mx, my, px, cy, SIDEBAR - 1, CAT_H)) {
				if (selected() != i) {
					selectedPerTab[tab] = i;
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
			for (Row row : categories.get(selected()).rows) {
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

	/** Zwischenüberschrift. */
	private static final class Header extends Row {
		private final String text;

		Header(String text) {
			this.text = text;
		}

		@Override
		int height() { return 12; }

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			g.text(font, text, x + 2, y + 2, t.accent(), false);
			int lx = x + 8 + font.width(text);
			g.fill(lx, y + 6, x + w, y + 7, Theme.withAlpha(t.accent(), 0.25f));
		}
	}

	/** Reiner Hinweistext. */
	private static final class Label extends Row {
		private final String text;

		Label(String text) {
			this.text = text;
		}

		@Override
		int height() { return 12; }

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			g.text(font, text, x + 2, y + 2, t.muted(), false);
		}
	}

	/** Zeile mit Tastenbelegung. */
	private static final class KeyRow extends Row {
		private final String name, key;

		KeyRow(String name, String key) {
			this.name = name;
			this.key = key;
		}

		@Override
		void draw(GuiGraphicsExtractor g, Font font, Theme t, int x, int y, int w, boolean hover) {
			rowBg(g, t, x, y, w, height(), false);
			g.text(font, name, x + 8, y + 6, t.text(), false);
			int kw = font.width(key) + 10;
			g.fill(x + w - 8 - kw, y + 3, x + w - 8, y + height() - 3, t.panelLight());
			g.text(font, key, x + w - 8 - kw + 5, y + 6, t.highlight(), false);
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
