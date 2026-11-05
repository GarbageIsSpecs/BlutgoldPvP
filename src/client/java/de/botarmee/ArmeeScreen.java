package de.botarmee;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.mojang.blaze3d.Blaze3D;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Das Bot-Armee-Menü (Taste B).
 * Tabs: Teams · Ausrüstung (Armor-Stand zum Anklicken) · Ablauf (Sequenzen + Rigging) · Arena · Extras.
 */
public class ArmeeScreen extends Screen {
	// ---- Farben
	static final int BG = 0xF2141820, PANEL = 0xFF1C212C, PANEL_LIGHT = 0xFF272E3C, ACCENT = 0xFFE8B84A,
			ACCENT_DARK = 0xFF9C7425, TEXT = 0xFFF1F1F1, MUTED = 0xFF9AA3B5, DANGER = 0xFFE5484D, GOOD = 0xFF46C25B;

	private static final int TOP = 24, BOTTOM = 30, SIDEBAR = 104;
	private static final String[] TABS = {"Teams", "Ausrüstung", "Ablauf", "Arena", "Extras"};
	private static int tab = 0;
	private static int team = 0;
	private static int slot = 1;

	private final BattleConfig cfg = ClientState.config();
	private int px, py, pw, ph;
	private double scroll = 0;
	private Slider dragging = null;
	private EditBox nameBox;
	private ArmorStand stand;
	private final List<Button> buttons = new ArrayList<>();

	/** Öffnet beim nächsten Mal einen bestimmten Tab (auch für den automatischen Test). */
	public static void select(int tabIndex, int teamIndex, int slotIndex) {
		tab = tabIndex;
		team = teamIndex;
		slot = slotIndex;
	}

	public ArmeeScreen() {
		super(Component.literal("Bot-Armee"));
	}

	private BattleConfig.Team cur() {
		team = Math.max(0, Math.min(team, cfg.teams.size() - 1));
		return cfg.teams.get(team);
	}

	@Override
	protected void init() {
		pw = Math.min(480, width - 12);
		ph = Math.min(300, height - 12);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		nameBox = new EditBox(font, 0, 0, 120, 16, Component.literal("Teamname"));
		nameBox.setMaxLength(24);
		nameBox.setValue(cur().name);
		nameBox.setResponder(v -> {
			cur().name = v.isBlank() ? "Team " + (team + 1) : v;
		});
		nameBox.visible = false;
		addRenderableWidget(nameBox);
		if (minecraft != null && minecraft.level != null) {
			stand = new ArmorStand(minecraft.level, 0, 0, 0);
			stand.setShowArms(true);
			stand.setNoBasePlate(true);
			stand.setId(-424242); // nur zur Vorschau, braucht trotzdem eine ID
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false; // die Schlacht läuft weiter, während das Menü offen ist
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void onClose() {
		ClientState.save();
		super.onClose();
	}

	// ====================================================================== Zeichnen

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		buttons.clear();
		nameBox.visible = false;

		g.fill(0, 0, width, height, 0x66000000);
		g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x66000000);
		g.fill(px, py, px + pw, py + ph, BG);
		g.outline(px, py, pw, ph, 0x55E8B84A);

		// Kopfzeile
		g.fill(px, py, px + pw, py + TOP, PANEL);
		g.fill(px, py + TOP - 1, px + pw, py + TOP, 0x99E8B84A);
		g.fill(px + 8, py + 7, px + 11, py + 17, DANGER);
		g.fill(px + 12, py + 7, px + 15, py + 17, 0xFF3E7BFA);
		g.text(font, "BOT-ARMEE", px + 20, py + 8, ACCENT, false);
		int tx = px + pw - 6;
		for (int i = TABS.length - 1; i >= 0; i--) {
			int w = font.width(TABS[i]) + 12;
			tx -= w;
			boolean sel = i == tab, hov = in(mx, my, tx, py + 3, w, TOP - 6);
			if (sel) {
				g.fill(tx, py + 3, tx + w, py + TOP - 3, PANEL_LIGHT);
				g.fill(tx, py + TOP - 4, tx + w, py + TOP - 3, ACCENT);
			} else if (hov) g.fill(tx, py + 3, tx + w, py + TOP - 3, 0x66272E3C);
			g.text(font, TABS[i], tx + 6, py + 8, sel ? TEXT : MUTED, false);
			final int idx = i;
			buttons.add(new Button(tx, py + 3, w, TOP - 6, () -> {
				tab = idx;
				scroll = 0;
			}));
			tx -= 2;
		}

		drawSidebar(g, mx, my);
		drawBottomBar(g, mx, my);

		switch (tab) {
			case 1 -> drawEquipment(g, mx, my);
			default -> drawRows(g, mx, my, rowsForTab());
		}

		super.extractRenderState(g, mx, my, delta); // Textfeld
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
		int sy = py + TOP;
		g.fill(px, sy, px + SIDEBAR, py + ph - BOTTOM, PANEL);
		g.text(font, "TEAMS", px + 8, sy + 6, MUTED, false);
		int y = sy + 18;
		for (int i = 0; i < cfg.teams.size(); i++) {
			BattleConfig.Team t = cfg.teams.get(i);
			boolean sel = i == team, hov = in(mx, my, px, y, SIDEBAR, 26);
			if (sel) g.fill(px, y, px + SIDEBAR, y + 26, PANEL_LIGHT);
			else if (hov) g.fill(px, y, px + SIDEBAR, y + 26, 0x66272E3C);
			g.fill(px, y, px + 3, y + 26, t.rgb());
			g.text(font, trim(t.name, SIDEBAR - 16), px + 9, y + 4, sel ? TEXT : 0xFFD0D4DC, false);
			String info = t.count + " Bots";
			if (cfg.winner == i) info += " · ★";
			g.text(font, info, px + 9, y + 15, MUTED, false);
			final int idx = i;
			buttons.add(new Button(px, y, SIDEBAR, 26, () -> {
				team = idx;
				nameBox.setValue(cur().name);
			}));
			y += 28;
		}
		if (cfg.teams.size() < BattleConfig.MAX_TEAMS) {
			y = smallButton(g, mx, my, px + 6, y + 2, SIDEBAR - 12, "+ Team", ACCENT, () -> {
				int c = firstFreeColor();
				BattleConfig.Team t = new BattleConfig.Team(BattleConfig.COLOR_NAMES[c], c);
				BattleConfig.Team base = cur();
				for (int s = 0; s < 6; s++) t.kit[s] = BattleConfig.Piece.decode(base.kit[s].encode());
				t.sequence.add(new BattleConfig.Phase(BattleConfig.ACT_ATTACK, 0));
				cfg.teams.add(t);
				team = cfg.teams.size() - 1;
				nameBox.setValue(t.name);
			}) + 2;
		}
		if (cfg.teams.size() > 2) {
			smallButton(g, mx, my, px + 6, y + 2, SIDEBAR - 12, "− Team entfernen", DANGER, () -> {
				cfg.teams.remove(team);
				if (cfg.winner >= cfg.teams.size()) cfg.winner = -1;
				team = Math.max(0, team - 1);
				nameBox.setValue(cur().name);
			});
		}
		int total = 0;
		for (BattleConfig.Team t : cfg.teams) total += t.count;
		int over = Math.max(0, total - BattleConfig.MAX_TOTAL);
		int level = BattleConfig.aiLevel(Math.min(total, BattleConfig.MAX_TOTAL));
		int gy = py + ph - BOTTOM - 22;
		g.text(font, "Gesamt: " + Math.min(total, BattleConfig.MAX_TOTAL), px + 8, gy, level == 0 ? MUTED : warnColor(level), false);
		if (level > 0) {
			warnIcon(g, px + SIDEBAR - 16, gy - 2, level);
			if (in(mx, my, px + SIDEBAR - 16, gy - 2, 11, 11)) {
				g.setTooltipForNextFrame(font, font.split(Component.literal(warnText(level)), 200), mx, my);
			}
		}
		if (over > 0) g.text(font, "max " + BattleConfig.MAX_TOTAL + "!", px + 8, gy + 11, DANGER, false);
		else if (level > 0) g.text(font, "KI: " + BattleConfig.AI_LEVELS[level], px + 8, gy + 11, warnColor(level), false);
	}

	private int firstFreeColor() {
		for (int c = 0; c < BattleConfig.COLOR_RGB.length; c++) {
			boolean used = false;
			for (BattleConfig.Team t : cfg.teams) used |= t.color == c;
			if (!used) return c;
		}
		return 0;
	}

	private void drawBottomBar(GuiGraphicsExtractor g, int mx, int my) {
		int by = py + ph - BOTTOM;
		g.fill(px, by, px + pw, py + ph, PANEL);
		g.fill(px, by, px + pw, by + 1, 0x55E8B84A);
		int x = px + 8, y = by + 7, h = 16;
		String st = ClientState.state;
		boolean fight = st.equals("FIGHT"), paused = st.equals("PAUSED");
		x = bigButton(g, mx, my, x, y, h, "Aufstellen", PANEL_LIGHT, TEXT, () -> ClientState.send("setup")) + 4;
		x = bigButton(g, mx, my, x, y, h, "▶ LOS!", 0xFF2F7D3B, TEXT, () -> ClientState.send("start")) + 4;
		x = bigButton(g, mx, my, x, y, h, paused ? "▶ Weiter" : "Ⅱ Pause", PANEL_LIGHT, fight || paused ? TEXT : MUTED, () -> ClientState.send("pause")) + 4;
		x = bigButton(g, mx, my, x, y, h, "✖ Aufräumen", 0xFF6B2326, TEXT, () -> ClientState.send("clear")) + 8;

		String status;
		int color = MUTED;
		if (!ClientState.connected()) {
			status = "Server hat die Mod nicht!";
			color = DANGER;
		} else {
			status = switch (st) {
				case "READY" -> "Armeen stehen bereit";
				case "COUNTDOWN" -> "Countdown …";
				case "FIGHT" -> "Kampf läuft";
				case "PAUSED" -> "Pausiert";
				case "OVER" -> "Schlacht vorbei";
				default -> "Bereit zum Aufstellen";
			};
			if (fight || paused || st.equals("READY") || st.equals("OVER")) {
				StringBuilder sb = new StringBuilder();
				for (ClientState.TeamStatus t : ClientState.teams) {
					if (sb.length() > 0) sb.append("  ");
					sb.append(t.alive());
				}
				if (sb.length() > 0) status += ": " + sb;
			}
		}
		g.text(font, trim(status, px + pw - 8 - x), x, y + 4, color, false);
	}

	// ====================================================================== Zeilen-Tabs

	private List<Row> rowsForTab() {
		List<Row> rows = new ArrayList<>();
		BattleConfig.Team t = cur();
		switch (tab) {
			case 0 -> {
				rows.add(new Header("Team: " + t.name));
				rows.add(new NameRow());
				rows.add(new Choice("Farbe", () -> BattleConfig.COLOR_NAMES, () -> t.color, v -> t.color = v).swatch(() -> t.rgb()));
				rows.add(new Slider("Anzahl Bots", 1, BattleConfig.MAX_PER_TEAM, 1, () -> t.count, v -> t.count = (int) v, v -> String.valueOf((int) v)));
				rows.add(new Warning());
				rows.add(new Choice("Formation", () -> BattleConfig.FORMATIONS, () -> t.formation, v -> t.formation = v));
				rows.add(new Header("Truppen"));
				rows.add(new Slider("Bogenschützen", 0, 100, 5, () -> t.archers, v -> t.archers = (int) v, v -> (int) v + "%  (" + Math.round(t.count * v / 100) + ")"));
				rows.add(new Choice("Fernkampf-Waffe", () -> BattleConfig.RANGED, () -> t.ranged, v -> t.ranged = v));
				rows.add(new Slider("Ausrüstungs-Mix", 0, 100, 5, () -> t.variation, v -> t.variation = (int) v, v -> v < 1 ? "alle gleich" : (int) v + "% zufällig"));
				rows.add(new Label("Mix = manche Bots haben schlechtere oder fehlende Teile."));
				rows.add(new Header("Stärke"));
				rows.add(new Slider("Vorteil", 0, 100, 5, () -> t.edge, v -> t.edge = (int) v, ArmeeScreen::edgeText));
				rows.add(new Label("50 = fair. Höher = etwas mehr Leben, Schaden, Tempo."));
			}
			case 2 -> {
				rows.add(new Header("Rigging"));
				rows.add(new Choice("Wer soll gewinnen?", this::winnerOptions, () -> cfg.winner + 1, v -> cfg.winner = v - 1));
				rows.add(new Slider("Wie knapp?", 0, 100, 5, () -> cfg.closeness, v -> cfg.closeness = (int) v,
						v -> v >= 80 ? "super knapp" : v >= 50 ? "knapp" : v >= 20 ? "deutlich" : "Vernichtung").visibleIf(() -> cfg.winner >= 0));
				rows.add(new Label("Das Sieger-Team wird unauffällig stärker, sobald es zurückfällt.").visibleIf(() -> cfg.winner >= 0));
				rows.add(new Header("Ablauf: " + t.name));
				for (int i = 0; i < t.sequence.size(); i++) rows.add(new PhaseRow(t, i));
				if (t.sequence.size() < 6) rows.add(new ButtonRow("+ Schritt hinzufügen", () -> t.sequence.add(new BattleConfig.Phase(BattleConfig.ACT_ATTACK, 0))));
				rows.add(new Label("Schritte laufen nacheinander ab. 0 s = bis zum Ende."));
				rows.add(new Label("Bogensalve: nur Bogenschützen feuern, Rest hält die Linie."));
				rows.add(new Label("Flankieren: die Hälfte läuft außen herum."));
				rows.add(new Header("Kampf"));
				rows.add(new Slider("Abstand der Armeen", 10, 120, 2, () -> cfg.distance, v -> cfg.distance = (int) v, v -> (int) v + " Blöcke"));
				rows.add(new Slider("Lauftempo", 40, 200, 5, () -> cfg.speed, v -> cfg.speed = (int) v, v -> (int) v + "%"));
				rows.add(new Slider("Angriffstempo", 30, 200, 5, () -> cfg.aggression, v -> cfg.aggression = (int) v, v -> (int) v + "%"));
				rows.add(new Toggle("Countdown 3-2-1", () -> cfg.countdown, v -> cfg.countdown = v));
			}
			case 3 -> {
				rows.add(new Header("Schlachtfeld"));
				rows.add(new ArenaGrid());
				rows.add(new Choice("Größe", () -> ArenaBuilder.SIZES, () -> cfg.arenaSize, v -> cfg.arenaSize = v));
				rows.add(new ButtonRow("Arena hier bauen", () -> ClientState.send("arena")));
				rows.add(new Label("Wird um deine Position gebaut – flieg am besten drüber."));
				rows.add(new Label("Tipp: in einer Superflach- oder Kreativ-Welt bauen."));
				rows.add(new Header("Aufstellung"));
				rows.add(new Toggle("Gebaute Arena benutzen", () -> cfg.useArena, v -> cfg.useArena = v));
				rows.add(new Label(ClientState.hasArena ? "§aArena gespeichert – Teams stellen sich dort auf." : "Keine Arena: Teams stellen sich vor dir auf."));
				rows.add(new ButtonRow("Arena vergessen", () -> ClientState.send("forgetArena")).visibleIf(() -> ClientState.hasArena));
			}
			default -> {
				rows.add(new Header("Anzeige"));
				rows.add(new Toggle("Namen über den Köpfen", () -> cfg.names, v -> cfg.names = v));
				rows.add(new Toggle("Team-Leuchten (Umriss)", () -> cfg.glow, v -> cfg.glow = v));
				rows.add(new Toggle("Zähler oben anzeigen", () -> cfg.hud, v -> cfg.hud = v));
				rows.add(new Toggle("Sieger-Titel", () -> cfg.winnerTitle, v -> cfg.winnerTitle = v));
				rows.add(new Toggle("Leder in Teamfarbe färben", () -> cfg.dyeLeather, v -> cfg.dyeLeather = v));
				rows.add(new Label("Namen/Leuchten gelten ab dem nächsten Aufstellen."));
				rows.add(new Label("Tipp bei vielen Bots: Namen aus = weniger Lag."));
				rows.add(new Header("Skins"));
				rows.add(new Choice("Skins", () -> BattleConfig.SKIN_MODES, () -> cfg.skinMode, v -> cfg.skinMode = v));
				rows.add(new ButtonRow("Skin-Ordner öffnen", () -> {
					Skins.loadCustom();
					Blaze3D.openPath(Skins.folder());
				}));
				rows.add(new ButtonRow("Skins neu laden (" + Skins.customCount() + " gefunden)", Skins::loadCustom));
				rows.add(new Label("64x64-PNGs in den Ordner legen. \"slim\" im Namen = dünne Arme."));
				rows.add(new Header("Für Videos"));
				rows.add(new Label("F1 = Interface aus · /tick rate 10 = Zeitlupe"));
				rows.add(new Label("Zuschauer-Modus (F3+N) für Kamerafahrten."));
				rows.add(new Label("/botarmee clear entfernt alle Bots."));
			}
		}
		return rows;
	}

	private String[] winnerOptions() {
		String[] o = new String[cfg.teams.size() + 1];
		o[0] = "Fair (Zufall)";
		for (int i = 0; i < cfg.teams.size(); i++) o[i + 1] = cfg.teams.get(i).name;
		return o;
	}

	private static String edgeText(double v) {
		int d = (int) v - 50;
		if (d == 0) return "fair";
		return (d > 0 ? "+" : "") + d * 2 + "%";
	}

	private int cx() { return px + SIDEBAR + 10; }
	private int cw() { return pw - SIDEBAR - 22; }
	private int ctop() { return py + TOP + 6; }
	private int cbottom() { return py + ph - BOTTOM - 4; }

	private void drawRows(GuiGraphicsExtractor g, int mx, int my, List<Row> rows) {
		int x = cx(), w = cw(), top = ctop(), bottom = cbottom();
		int total = 0;
		for (Row r : rows) if (r.visible()) total += r.height() + 3;
		int max = Math.max(0, total - (bottom - top));
		scroll = Math.max(0, Math.min(scroll, max));
		int y = top - (int) scroll;
		g.enableScissor(x - 2, top, x + w + 2, bottom); // halb sichtbare Zeilen sauber abschneiden
		for (Row r : rows) {
			if (!r.visible()) continue;
			int h = r.height();
			if (y + h > top && y < bottom) {
				boolean hover = in(mx, my, x, y, w, h) && my >= top && my < bottom;
				r.draw(g, x, y, w, hover, mx, my);
				if (y >= top && y + h <= bottom) r.addButtons(x, y, w);
			}
			y += h + 3;
		}
		g.disableScissor();
		if (max > 0) {
			int trackH = bottom - top;
			int barH = Math.max(14, trackH * trackH / (trackH + max));
			int barY = top + (int) ((trackH - barH) * (scroll / max));
			g.fill(px + pw - 5, barY, px + pw - 3, barY + barH, 0xAAE8B84A);
		}
		currentRows = rows;
	}

	private List<Row> currentRows = List.of();

	// ====================================================================== Ausrüstung (Armor-Stand)

	private static final String[] SLOT_HINT = {"Klick auf den Kopf", "Klick auf die Brust", "Klick auf die Beine", "Klick auf die Füße", "Klick auf die rechte Hand", "Klick auf die linke Hand"};

	private void drawEquipment(GuiGraphicsExtractor g, int mx, int my) {
		BattleConfig.Team t = cur();
		int x = cx(), top = ctop(), bottom = cbottom();
		int bw = 118, bh = bottom - top - 44;
		int bx = x, by = top;
		g.fill(bx, by, bx + bw, by + bh, PANEL);
		g.outline(bx, by, bw, bh, 0x33FFFFFF);
		g.text(font, t.name, bx + 5, by + 4, t.rgb(), false);

		// Armor-Stand mit der Team-Ausrüstung
		int size = Math.min(52, (bh - 20) / 2);
		int ecx = bx + bw / 2, ecy = by + (12 + bh - 16) / 2;
		if (stand != null && minecraft != null && minecraft.level != null) {
			int dye = cfg.dyeLeather ? BattleConfig.COLOR_DYE[Math.floorMod(t.color, BattleConfig.COLOR_DYE.length)] : -1;
			for (int i = 0; i < 6; i++) {
				stand.setItemSlot(Kits.SLOTS[i], Kits.build(minecraft.level.registryAccess(), t.kit[i], i, dye, 0));
			}
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, bx + 4, by + 12, bx + bw - 4, by + bh - 16, size, 0.0625F,
					ecx + (mx - ecx) * 0.35F, ecy - 30, stand);
		}

		// Klick-Zonen
		int standTop = ecy - (int) (size * 0.99), standH = (int) (size * 1.98);
		int[][] zones = zones(ecx, standTop, standH, size);
		int hoverSlot = -1;
		for (int i = 0; i < zones.length; i++) {
			int[] z = zones[i];
			boolean hov = in(mx, my, z[0], z[1], z[2], z[3]);
			if (hov) hoverSlot = i;
			if (i == slot) g.outline(z[0], z[1], z[2], z[3], ACCENT);
			else if (hov) g.outline(z[0], z[1], z[2], z[3], 0x88FFFFFF);
			final int idx = i;
			buttons.add(new Button(z[0], z[1], z[2], z[3], () -> {
				slot = idx;
				eqScroll = 0;
			}));
		}
		String hint = hoverSlot >= 0 ? BattleConfig.SLOT_NAMES[hoverSlot] : "Teil anklicken";
		g.centeredText(font, hint, ecx, by + bh - 12, hoverSlot >= 0 ? TEXT : MUTED);

		// Schnell-Vorlagen
		String[][] presets = {{"Nackt", ""}, {"Leder", "leather"}, {"Eisen", "iron"}, {"Gold", "golden"}, {"Diamant", "diamond"}, {"Netherite", "netherite"}};
		int pyy = by + bh + 4, pbw = (bw - 4) / 3;
		for (int i = 0; i < presets.length; i++) {
			int bxx = bx + (i % 3) * (pbw + 2), byy = pyy + (i / 3) * 19;
			final String tier = presets[i][1];
			smallButton(g, mx, my, bxx, byy, pbw, presets[i][0], MUTED, () -> applyPreset(t, tier));
		}

		// Auswahl rechts (scrollbar, falls das Fenster klein ist)
		int rx = bx + bw + 8, rw = x + cw() - rx;
		int viewTop = top + 12, viewBottom = bottom;
		String head = BattleConfig.SLOT_NAMES[slot].toUpperCase();
		g.text(font, head, rx, top, ACCENT, false);
		g.text(font, trim(SLOT_HINT[slot], rw - font.width(head) - 6), rx + font.width(head) + 6, top, MUTED, false);
		BattleConfig.Piece piece = t.kit[slot];
		List<String> options = new ArrayList<>();
		for (String id : Kits.OPTIONS.get(slot)) if (id.isEmpty() || Kits.item(id) != Items.AIR) options.add(id);
		int cell = rw >= 200 ? 20 : 18;
		int cols = Math.max(1, rw / cell);
		hoverName = null;
		int y = viewTop - (int) eqScroll;

		y = section(g, mx, my, rx, y, rw, null, viewTop, viewBottom);
		for (int i = 0; i < options.size(); i++) {
			String id = options.get(i);
			ItemStack icon = id.isEmpty() ? new ItemStack(Items.BARRIER) : preview(id, piece, t);
			cellAt(g, mx, my, rx + (i % cols) * cell, y + (i / cols) * cell, cell, icon, id.equals(piece.item),
					id.isEmpty() ? Component.literal("Nichts") : icon.getHoverName(), () -> piece.item = id, viewTop, viewBottom);
		}
		y += ((options.size() + cols - 1) / cols) * cell + 5;

		if (slot < 4) {
			boolean trimmable = !piece.item.isEmpty() && !piece.item.contains("elytra");
			y = section(g, mx, my, rx, y, rw, trimmable ? "Trim-Muster" : "Trim-Muster (nicht möglich)", viewTop, viewBottom);
			int n = Kits.PATTERNS.length + 1;
			for (int i = 0; i < n; i++) {
				String pat = i == 0 ? "" : Kits.PATTERNS[i - 1];
				cellAt(g, mx, my, rx + (i % cols) * cell, y + (i / cols) * cell, cell,
						i == 0 ? new ItemStack(Items.BARRIER) : Kits.patternIcon(pat), pat.equals(piece.pattern),
						Component.literal(i == 0 ? "Kein Trim" : "Muster: " + pat), () -> {
							piece.pattern = pat;
							if (!pat.isEmpty() && piece.material.isEmpty()) piece.material = "gold";
						}, viewTop, viewBottom);
			}
			y += ((n + cols - 1) / cols) * cell + 4;
			y = section(g, mx, my, rx, y, rw, "Trim-Material", viewTop, viewBottom);
			for (int i = 0; i < Kits.MATERIALS.length; i++) {
				String m = Kits.MATERIALS[i];
				cellAt(g, mx, my, rx + (i % cols) * cell, y + (i / cols) * cell, cell, Kits.materialIcon(m), m.equals(piece.material),
						Component.literal("Material: " + m), () -> {
							piece.material = m;
							if (piece.pattern.isEmpty()) piece.pattern = "sentry";
						}, viewTop, viewBottom);
			}
			y += ((Kits.MATERIALS.length + cols - 1) / cols) * cell + 4;
		} else if (slot == 4) {
			for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal("Bogenschützen bekommen automatisch Bogen oder Armbrust (Tab Teams)."), rw)) {
				if (y >= viewTop && y + 9 <= viewBottom) g.text(font, line, rx, y, MUTED, false);
				y += 10;
			}
			y += 4;
		}
		// Glitzern
		if (y >= viewTop && y + 16 <= viewBottom) {
			boolean hovG = in(mx, my, rx, y, rw, 16);
			g.fill(rx, y, rx + rw, y + 16, hovG ? PANEL_LIGHT : PANEL);
			g.text(font, "Verzaubert (Glitzern)", rx + 6, y + 4, TEXT, false);
			drawSwitch(g, rx + rw - 28, y + 3, piece.glint);
			buttons.add(new Button(rx, y, rw, 16, () -> piece.glint = !piece.glint));
		}
		y += 20;
		if (y >= viewTop && y + 17 <= viewBottom) {
			smallButton(g, mx, my, rx, y, rw, "Für alle Teams übernehmen", MUTED, () -> {
				for (BattleConfig.Team other : cfg.teams) {
					if (other == t) continue;
					for (int s = 0; s < 6; s++) other.kit[s] = BattleConfig.Piece.decode(t.kit[s].encode());
				}
			});
		}
		y += 19;
		int contentH = y + (int) eqScroll - viewTop;
		eqMax = Math.max(0, contentH - (viewBottom - viewTop));
		eqScroll = Math.max(0, Math.min(eqScroll, eqMax));
		if (eqMax > 0) {
			int trackH = viewBottom - viewTop;
			int barH = (int) Math.max(14, trackH * trackH / (trackH + eqMax));
			int barY = viewTop + (int) ((trackH - barH) * (eqScroll / eqMax));
			g.fill(px + pw - 5, barY, px + pw - 3, barY + barH, 0xAAE8B84A);
		}

		if (hoverName != null) g.setTooltipForNextFrame(hoverName, mx, my);
	}

	/** Zonen am Armor-Stand: Kopf, Brust, Beine, Füße, rechte Hand (Waffe), linke Hand. */
	private double eqScroll = 0, eqMax = 0;
	private Component hoverName;

	private int section(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String title, int top, int bottom) {
		if (title == null) return y;
		if (y >= top && y + 9 <= bottom) g.text(font, title, x, y, title.contains("nicht") ? MUTED : TEXT, false);
		return y + 11;
	}

	private void cellAt(GuiGraphicsExtractor g, int mx, int my, int x, int y, int cell, ItemStack icon, boolean sel, Component name,
			Runnable action, int top, int bottom) {
		if (y < top || y + cell - 1 > bottom) return;
		int s = cell - 1;
		boolean hov = in(mx, my, x, y, s, s);
		g.fill(x, y, x + s, y + s, sel ? ACCENT_DARK : hov ? PANEL_LIGHT : PANEL);
		if (sel) g.outline(x, y, s, s, ACCENT);
		g.item(icon, x + (s - 16) / 2, y + (s - 16) / 2);
		if (hov) hoverName = name;
		buttons.add(new Button(x, y, s, s, action));
	}

	private static int[][] zones(int cx, int top, int h, int size) {
		int bodyW = (int) (size * 0.62);
		int head = (int) (h * 0.24), chest = (int) (h * 0.32), legs = (int) (h * 0.26);
		int handW = (int) (size * 0.34);
		int y0 = top, y1 = y0 + head, y2 = y1 + chest, y3 = y2 + legs, y4 = top + h;
		return new int[][] {
				{cx - bodyW / 2, y0, bodyW, y1 - y0},
				{cx - bodyW / 2, y1, bodyW, y2 - y1},
				{cx - bodyW / 2, y2, bodyW, y3 - y2},
				{cx - bodyW / 2, y3, bodyW, y4 - y3},
				{cx - bodyW / 2 - handW, y1 + 2, handW, chest + 6},
				{cx + bodyW / 2, y1 + 2, handW, chest + 6}
		};
	}

	private ItemStack preview(String id, BattleConfig.Piece piece, BattleConfig.Team t) {
		BattleConfig.Piece p = BattleConfig.Piece.decode(piece.encode());
		p.item = id;
		int dye = cfg.dyeLeather ? BattleConfig.COLOR_DYE[Math.floorMod(t.color, BattleConfig.COLOR_DYE.length)] : -1;
		ItemStack s = Kits.build(minecraft != null && minecraft.level != null ? minecraft.level.registryAccess() : null, p, slot, dye, 0);
		return s.isEmpty() ? new ItemStack(Kits.item(id)) : s;
	}

	private static void applyPreset(BattleConfig.Team t, String tier) {
		String[] types = {"helmet", "chestplate", "leggings", "boots"};
		for (int i = 0; i < 4; i++) {
			BattleConfig.Piece p = t.kit[i];
			p.item = tier.isEmpty() ? "" : "minecraft:" + tier + "_" + types[i];
		}
		if (tier.isEmpty()) {
			t.kit[4].item = "";
			t.kit[5].item = "";
		} else {
			String sword = switch (tier) {
				case "leather" -> "minecraft:wooden_sword";
				case "chainmail" -> "minecraft:stone_sword";
				default -> "minecraft:" + tier + "_sword";
			};
			t.kit[4].item = sword;
		}
	}

	// ====================================================================== Kleine Zeichen-Helfer

	private int bigButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int h, String label, int bg, int fg, Runnable action) {
		int w = font.width(label) + 14;
		boolean hov = in(mx, my, x, y, w, h);
		g.fill(x, y, x + w, y + h, hov ? lighten(bg) : bg);
		g.outline(x, y, w, h, hov ? 0xAAE8B84A : 0x33FFFFFF);
		g.text(font, label, x + 7, y + 4, fg, false);
		buttons.add(new Button(x, y, w, h, action));
		return x + w;
	}

	private int smallButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, int fg, Runnable action) {
		boolean hov = in(mx, my, x, y, w, 17);
		g.fill(x, y, x + w, y + 17, hov ? PANEL_LIGHT : 0xFF20262F);
		g.outline(x, y, w, 17, hov ? 0x88E8B84A : 0x22FFFFFF);
		g.centeredText(font, trim(label, w - 4), x + w / 2, y + 5, hov ? TEXT : fg);
		buttons.add(new Button(x, y, w, 17, action));
		return y + 17;
	}

	private static void drawSwitch(GuiGraphicsExtractor g, int x, int y, boolean on) {
		g.fill(x, y, x + 22, y + 10, on ? ACCENT : 0xFF3A4250);
		int kx = on ? x + 13 : x + 1;
		g.fill(kx, y + 1, kx + 8, y + 9, on ? 0xFFFFFFFF : MUTED);
	}

	private static int lighten(int c) {
		int r = Math.min(255, ((c >> 16) & 0xFF) + 25), gg = Math.min(255, ((c >> 8) & 0xFF) + 25), b = Math.min(255, (c & 0xFF) + 25);
		return 0xFF000000 | r << 16 | gg << 8 | b;
	}

	private String trim(String s, int w) {
		if (font.width(s) <= w) return s;
		while (s.length() > 1 && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ====================================================================== Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (nameBox.visible && in(mx, my, nameBox.getX(), nameBox.getY(), nameBox.getWidth(), nameBox.getHeight())) {
			return super.mouseClicked(event, doubleClick);
		}
		if (getFocused() == nameBox) setFocused(null); // sonst bekommt das Feld beim 2. Mal keinen Fokus mehr
		// Schieberegler zuerst (die liegen in den Zeilen)
		if (tab != 1) {
			int x = cx(), w = cw(), top = ctop(), bottom = cbottom();
			if (my >= top && my < bottom && mx >= x && mx < x + w) {
				int y = top - (int) scroll;
				for (Row r : currentRows) {
					if (!r.visible()) continue;
					int h = r.height();
					if (in(mx, my, x, y, w, h)) {
						if (r instanceof Slider s) {
							dragging = s;
							s.setFromMouse(mx, x, w);
							return true;
						}
						if (r.click(mx, my, x, y, w, event.button())) {
							ClientState.save();
							return true;
						}
					}
					y += h + 3;
				}
			}
		}
		for (int i = buttons.size() - 1; i >= 0; i--) {
			Button b = buttons.get(i);
			if (in(mx, my, b.x, b.y, b.w, b.h)) {
				b.action.run();
				ClientState.save();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			dragging.setFromMouse(event.x(), cx(), cw());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			ClientState.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (tab == 1) {
			eqScroll = Math.max(0, Math.min(eqMax, eqScroll - sy * 16));
			return true;
		}
		scroll -= sy * 16;
		if (scroll < 0) scroll = 0;
		return true;
	}

	// ====================================================================== Zeilen-Typen

	private record Button(int x, int y, int w, int h, Runnable action) {}

	private abstract class Row {
		private BooleanSupplier visible = () -> true;

		int height() { return 18; }

		boolean visible() { return visible.getAsBoolean(); }

		Row visibleIf(BooleanSupplier v) {
			this.visible = v;
			return this;
		}

		abstract void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my);

		/** Für Zeilen mit eigenen Knöpfen (z.B. Arena-Karten, Schritte). */
		void addButtons(int x, int y, int w) {}

		boolean click(double mx, double my, int x, int y, int w, int button) { return false; }

		void bg(GuiGraphicsExtractor g, int x, int y, int w, boolean hover) {
			g.fill(x, y, x + w, y + height(), hover ? PANEL_LIGHT : PANEL);
		}
	}

	private final class Header extends Row {
		final String text;

		Header(String text) { this.text = text; }

		@Override int height() { return 14; }

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			g.text(font, text.toUpperCase(), x, y + 4, ACCENT, false);
			g.fill(x + font.width(text.toUpperCase()) + 6, y + 8, x + w, y + 9, 0x33E8B84A);
		}
	}

	static int warnColor(int level) {
		return level >= 2 ? DANGER : 0xFFF08A24;
	}

	static String warnText(int level) {
		return level >= 2
				? "Sparmodus (über " + BattleConfig.SIMPLE_LIMIT + " Bots): Dein PC könnte laggen! Die Bots laufen geradeaus, suchen Gegner seltener und flankieren nicht. Rigging funktioniert weiter."
				: "Über " + BattleConfig.EPIC_LIMIT + " Bots: Dein PC könnte etwas laggen. Die KI wird einfacher: kein Ausweichen, keine Crit-Sprünge, Gegner werden seltener gesucht. Rigging funktioniert weiter.";
	}

	/** Gelbes/rotes Dreieck mit Ausrufezeichen. */
	void warnIcon(GuiGraphicsExtractor g, int x, int y, int level) {
		int c = warnColor(level);
		for (int i = 0; i < 6; i++) g.fill(x + 5 - i, y + 1 + i * 2, x + 6 + i, y + 3 + i * 2, c);
		g.fill(x + 5, y + 4, x + 6, y + 9, 0xFF1A1A1A);
		g.fill(x + 5, y + 10, x + 6, y + 11, 0xFF1A1A1A);
	}

	/** Hinweis-Kasten, der nur bei sehr vielen Bots erscheint. */
	private final class Warning extends Row {
		@Override
		boolean visible() {
			return BattleConfig.aiLevel(cfg.totalBots()) > 0;
		}

		@Override
		int height() {
			return 12 + 10 * font.split(Component.literal(warnText(BattleConfig.aiLevel(cfg.totalBots()))), cw() - 30).size();
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			int level = BattleConfig.aiLevel(cfg.totalBots());
			int c = warnColor(level);
			g.fill(x, y, x + w, y + height(), level >= 2 ? 0xFF3A1A1C : 0xFF3A2A16);
			g.fill(x, y, x + 2, y + height(), c);
			warnIcon(g, x + 6, y + 5, level);
			int ly = y + 6;
			for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(warnText(level)), w - 30)) {
				g.text(font, line, x + 24, ly, 0xFFF1E6D0, false);
				ly += 10;
			}
		}
	}

	private final class Label extends Row {
		final String text;

		Label(String text) { this.text = text; }

		@Override int height() { return 10; }

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			g.text(font, trim(text, w), x + 2, y + 1, MUTED, false);
		}
	}

	private final class Toggle extends Row {
		final String label;
		final BooleanSupplier get;
		final Consumer<Boolean> set;

		Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
			this.label = label;
			this.get = get;
			this.set = set;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			bg(g, x, y, w, hover);
			g.text(font, label, x + 6, y + 5, TEXT, false);
			drawSwitch(g, x + w - 28, y + 4, get.getAsBoolean());
		}

		@Override
		boolean click(double mx, double my, int x, int y, int w, int button) {
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	private final class Slider extends Row {
		final String label;
		final double min, max, step;
		final Supplier<Number> get;
		final java.util.function.DoubleConsumer set;
		final DoubleFunction<String> fmt;

		Slider(String label, double min, double max, double step, Supplier<Number> get, java.util.function.DoubleConsumer set, DoubleFunction<String> fmt) {
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.fmt = fmt;
		}

		@Override int height() { return 24; }

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			bg(g, x, y, w, hover || dragging == this);
			double v = get.get().doubleValue();
			g.text(font, label, x + 6, y + 4, TEXT, false);
			String val = fmt.apply(v);
			g.text(font, val, x + w - 6 - font.width(val), y + 4, ACCENT, false);
			int tx = x + 6, tw = w - 12, ty = y + 16;
			int fx = tx + (int) Math.round((v - min) / (max - min) * tw);
			g.fill(tx, ty, tx + tw, ty + 2, 0xFF3A4250);
			g.fill(tx, ty, fx, ty + 2, ACCENT);
			g.fill(fx - 2, ty - 3, fx + 2, ty + 5, 0xFFFFFFFF);
		}

		void setFromMouse(double mx, int x, int w) {
			int tx = x + 6, tw = w - 12;
			double frac = Math.max(0, Math.min(1, (mx - tx) / tw));
			double v = Math.round((min + frac * (max - min)) / step) * step;
			set.accept(Math.max(min, Math.min(max, v)));
		}
	}

	private final class Choice extends Row {
		final String label;
		final Supplier<String[]> options;
		final IntSupplier get;
		final IntConsumer set;
		IntSupplier swatch;

		Choice(String label, Supplier<String[]> options, IntSupplier get, IntConsumer set) {
			this.label = label;
			this.options = options;
			this.get = get;
			this.set = set;
		}

		Choice swatch(IntSupplier color) {
			this.swatch = color;
			return this;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			bg(g, x, y, w, hover);
			g.text(font, label, x + 6, y + 5, TEXT, false);
			String[] o = options.get();
			int i = Math.floorMod(get.getAsInt(), o.length);
			String val = "‹ " + o[i] + " ›";
			int vx = x + w - 6 - font.width(val);
			g.text(font, val, vx, y + 5, ACCENT, false);
			if (swatch != null) g.fill(vx - 14, y + 4, vx - 4, y + 14, swatch.getAsInt());
		}

		@Override
		boolean click(double mx, double my, int x, int y, int w, int button) {
			int n = options.get().length;
			int dir = button == 1 || mx < x + w / 2.0 ? -1 : 1;
			if (button == 1) dir = -1;
			if (mx >= x + w / 2.0 && button == 0) dir = 1;
			set.accept(Math.floorMod(get.getAsInt() + dir, n));
			return true;
		}
	}

	private final class ButtonRow extends Row {
		final String label;
		final Runnable action;

		ButtonRow(String label, Runnable action) {
			this.label = label;
			this.action = action;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			g.fill(x, y, x + w, y + height(), hover ? 0xFF3A3220 : 0xFF262418);
			g.outline(x, y, w, height(), hover ? ACCENT : 0x66E8B84A);
			g.centeredText(font, label, x + w / 2, y + 5, hover ? ACCENT : TEXT);
		}

		@Override
		boolean click(double mx, double my, int x, int y, int w, int button) {
			action.run();
			return true;
		}
	}

	private final class NameRow extends Row {
		@Override int height() { return 20; }

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			bg(g, x, y, w, false);
			g.text(font, "Name", x + 6, y + 6, TEXT, false);
			int bw = Math.min(160, w - 60);
			if (y >= ctop() && y + height() <= cbottom()) {
				nameBox.setX(x + w - bw - 4);
				nameBox.setY(y + 2);
				nameBox.setWidth(bw);
				nameBox.visible = true;
			}
		}
	}

	private final class PhaseRow extends Row {
		final BattleConfig.Team t;
		final int index;

		PhaseRow(BattleConfig.Team t, int index) {
			this.t = t;
			this.index = index;
		}

		@Override int height() { return 20; }

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			bg(g, x, y, w, hover);
			BattleConfig.Phase p = t.sequence.get(index);
			g.fill(x, y, x + 2, y + height(), t.rgb());
			g.text(font, (index + 1) + ".", x + 6, y + 6, MUTED, false);
			String act = "‹ " + BattleConfig.ACTIONS[p.action] + " ›";
			boolean hovA = in(mx, my, x + 20, y, 110, 20);
			g.text(font, act, x + 20, y + 6, hovA ? ACCENT : TEXT, false);
			String dur = p.seconds == 0 ? "bis Ende" : p.seconds + " s";
			int dx = x + w - 112;
			g.text(font, "−", dx, y + 6, in(mx, my, dx - 3, y, 12, 20) ? ACCENT : MUTED, false);
			g.centeredText(font, dur, dx + 34, y + 6, TEXT);
			g.text(font, "+", dx + 62, y + 6, in(mx, my, dx + 59, y, 12, 20) ? ACCENT : MUTED, false);
			if (t.sequence.size() > 1) g.text(font, "✖", x + w - 14, y + 6, in(mx, my, x + w - 18, y, 16, 20) ? DANGER : MUTED, false);
			if (index > 0) g.text(font, "▲", x + w - 34, y + 6, in(mx, my, x + w - 37, y, 14, 20) ? ACCENT : MUTED, false);
		}

		@Override
		boolean click(double mx, double my, int x, int y, int w, int button) {
			BattleConfig.Phase p = t.sequence.get(index);
			int dx = x + w - 112;
			if (in(mx, my, x + w - 18, y, 16, 20) && t.sequence.size() > 1) {
				t.sequence.remove(index);
			} else if (index > 0 && in(mx, my, x + w - 37, y, 14, 20)) {
				t.sequence.remove(index);
				t.sequence.add(index - 1, p);
			} else if (in(mx, my, dx - 3, y, 12, 20)) {
				p.seconds = Math.max(0, p.seconds - (p.seconds > 10 ? 5 : 1));
			} else if (in(mx, my, dx + 59, y, 12, 20)) {
				p.seconds = Math.min(120, p.seconds + (p.seconds >= 10 ? 5 : 1));
			} else if (in(mx, my, x + 20, y, 110, 20)) {
				p.action = Math.floorMod(p.action + (button == 1 ? -1 : 1), BattleConfig.ACTIONS.length);
			}
			return true;
		}
	}

	private final class ArenaGrid extends Row {
		private int cardW() {
			return (cw() - 4) / 2;
		}

		private int thumbH() {
			return (cardW() - 6) / 2; // Vorschaubild im Format 2:1
		}

		private int cardH() {
			return thumbH() + 36;
		}

		@Override int height() { return 3 * cardH() + 2 * 4; }

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover, int mx, int my) {
			int cw = cardW(), ch = cardH(), th = thumbH();
			int top = ctop(), bottom = cbottom();
			for (int i = 0; i < ArenaBuilder.NAMES.length; i++) {
				int cx = x + (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
				if (cy + ch < top || cy > bottom) continue;
				boolean sel = cfg.arena == i, hov = in(mx, my, cx, cy, cw, ch) && my >= top && my < bottom;
				g.fill(cx, cy, cx + cw, cy + ch, sel ? 0xFF2E2818 : hov ? PANEL_LIGHT : PANEL);
				// Vorschaubild
				int tx = cx + 3, ty = cy + 3, tw = cw - 6;
				g.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(BotArmee.MOD_ID, "textures/gui/arena_" + i + ".png"),
						tx, ty, 0, 0, tw, th, 256, 128, 256, 128);
				if (!sel) g.fill(tx, ty, tx + tw, ty + th, hov ? 0x10000000 : 0x40000000);
				g.outline(cx, cy, cw, ch, sel ? ACCENT : hov ? 0x88E8B84A : 0x22FFFFFF);
				if (sel) {
					g.fill(tx, ty, tx + 52, ty + 12, 0xCC000000);
					g.text(font, "✔ gewählt", tx + 3, ty + 2, ACCENT, false);
				}
				g.item(new ItemStack(arenaIcon(i)), cx + 4, cy + th + 6);
				g.text(font, ArenaBuilder.NAMES[i], cx + 23, cy + th + 6, sel ? ACCENT : TEXT, false);
				List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(ArenaBuilder.DESCRIPTIONS[i]), cw - 27);
				for (int l = 0; l < Math.min(2, lines.size()); l++) g.text(font, lines.get(l), cx + 23, cy + th + 16 + l * 9, MUTED, false);
			}
		}

		@Override
		boolean click(double mx, double my, int x, int y, int w, int button) {
			int cw = cardW(), ch = cardH();
			for (int i = 0; i < ArenaBuilder.NAMES.length; i++) {
				int cx = x + (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
				if (in(mx, my, cx, cy, cw, ch)) cfg.arena = i;
			}
			return true;
		}

		private Item arenaIcon(int i) {
			return switch (i) {
				case 0 -> Items.GRASS_BLOCK;
				case 1 -> Items.STONE_BRICKS;
				case 2 -> Items.SAND;
				case 3 -> Items.SNOW_BLOCK;
				case 4 -> Items.CHISELED_STONE_BRICKS;
				default -> Items.NETHERRACK;
			};
		}
	}
}
