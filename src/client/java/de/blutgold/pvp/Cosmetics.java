package de.blutgold.pvp;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Client-seitige Cosmetics nur für dich selbst: Drachenflügel (rot-gold),
 * Heiligenschein und Partikel. Andere Spieler sehen davon nichts.
 */
public final class Cosmetics {
	public static final String[] WING_COLORS = {"Blutgold", "Theme", "Regenbogen", "Schatten"};
	public static final String[] HALO_COLORS = {"Gold", "Theme", "Regenbogen", "Blutrot"};
	public static final String[] PARTICLE_TYPES = {"Blutgold-Staub", "Flammen", "Seelenfeuer", "Glitzer", "Herzen", "Funken", "Rauch"};

	/** Alles, was wir zum Zeichnen brauchen, beim Extrahieren eingesammelt (thread-sicher). */
	private record State(double x, double y, double z, float bodyYaw, float headYaw, double eye,
			boolean crouch, boolean fast, boolean flying, boolean moving, boolean showWings, boolean showHalo) {}

	private static volatile State state = null;
	private static final Random RANDOM = new Random();
	private static double particleBudget = 0;

	private Cosmetics() {}

	// ================================================================ Extrahieren

	public static void extract(LevelExtractionContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();
		if (p == null || !thirdPerson || p.isInvisible() || p.isSpectator() || (!Config.wings && !Config.halo)) {
			state = null;
			return;
		}
		float pt = ctx.deltaTracker().getGameTimeDeltaPartialTick(true);
		double x = Mth.lerp(pt, p.xo, p.getX());
		double y = Mth.lerp(pt, p.yo, p.getY());
		double z = Mth.lerp(pt, p.zo, p.getZ());
		float body = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
		float head = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
		double speed = Math.sqrt(Mth.square(p.getX() - p.xo) + Mth.square(p.getZ() - p.zo));
		boolean flying = p.getAbilities().flying || (!p.onGround() && p.getY() - p.yo < -0.4);
		state = new State(x, y, z, body, head, p.getEyeHeight(), p.isShiftKeyDown(), p.isSprinting(),
				flying, speed > 0.02, Config.wings, Config.halo);
	}

	// ================================================================ Zeichnen

	public static void render(LevelRenderContext ctx) {
		State s = state;
		if (s == null) return;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		double time = System.currentTimeMillis() / 1000.0;
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buf) -> {
			if (s.showWings()) {
				drawWing(p, buf, s, time, 1);
				drawWing(p, buf, s, time, -1);
			}
			if (s.showHalo()) drawHalo(p, buf, s, time);
		});
		pose.popPose();
	}

	// ---------------------------------------------------------------- Flügel

	/** Knochen (Gold) entlang der Oberkante: u, v, Breite. */
	private static final double[][] BONE = {
			{0.06, 0.05, 0.11}, {0.22, 0.40, 0.095}, {0.45, 0.62, 0.08}, {0.80, 0.88, 0.065},
			{1.20, 1.12, 0.05}, {1.62, 1.34, 0.035}, {2.02, 1.56, 0.0}
	};
	/** Untere S-Kralle (Gold) am Schulteransatz. */
	private static final double[][] HOOK = {
			{0.06, 0.05, 0.08}, {0.01, -0.25, 0.075}, {0.09, -0.50, 0.065}, {0.27, -0.63, 0.05},
			{0.37, -0.52, 0.04}, {0.31, -0.40, 0.0}
	};
	/** Goldene Zacken (Dreiecke) – Kamm und "Drachenkopf" vorne. */
	private static final double[][] SPIKES = {
			{0.16, 0.36, 0.30, 0.47, 0.10, 0.80},
			{0.36, 0.56, 0.52, 0.65, 0.40, 0.98},
			{0.64, 0.76, 0.80, 0.87, 0.80, 1.12},
			{1.02, 1.00, 1.18, 1.10, 1.24, 1.30},
			{0.24, 0.42, 0.10, 0.30, 0.50, 0.30},
			{0.30, 0.52, 0.20, 0.44, 0.58, 0.52},
			{0.08, -0.18, 0.16, -0.30, 0.44, -0.06},
			{0.20, -0.58, 0.30, -0.62, 0.30, -0.86},
	};
	private static final int BLADES = 8;

	private static void drawWing(PoseStack.Pose p, VertexConsumer buf, State s, double time, int side) {
		double scale = Config.wingScale;
		double yaw = Math.toRadians(s.bodyYaw());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));

		// Flügelschlag
		double speedMul = Config.wingFlapSpeed * (s.flying() ? 2.2 : s.fast() ? 1.6 : 1.0);
		boolean active = s.moving() || s.flying() || !Config.wingFlapOnlyMoving;
		double amp = s.flying() ? 0.42 : active ? 0.22 : 0.05;
		double base = 0.15 + Config.wingSpread * 0.75;
		double a = base + amp * Math.sin(time * Math.PI * 2 * 0.7 * speedMul);

		Vec3 root = new Vec3(s.x(), s.y() + (s.crouch() ? 1.12 : 1.36), s.z())
				.add(back.scale(0.16)).add(right.scale(side * 0.06));
		Vec3 dir = right.scale(side * Math.cos(a)).add(back.scale(Math.sin(a)));
		Vec3 normal = back.scale(Math.cos(a)).subtract(right.scale(side * Math.sin(a)));
		Frame f = new Frame(p, buf, root, dir, normal, scale);

		Palette c = wingPalette(time);
		float alpha = Config.wingOpacity;

		// Innere dunkle Haut am Ansatz
		f.tri(0.04, 0.05, 0.52, 0.64, 0.46, -0.56, wa(c.base, alpha), wa(c.base, alpha), wa(c.base, alpha), 0);
		f.tri(0.04, 0.05, 0.46, -0.56, 0.12, -0.46, wa(c.base, alpha), wa(c.base, alpha), wa(c.base, alpha), 0);

		// Federn / Klingen
		double[] prevMid = null;
		for (int i = 0; i < BLADES; i++) {
			double frac = 0.10 + i * (0.80 / (BLADES - 1));
			double[] bp = along(BONE, frac);
			double ang = Math.toRadians(-100 + 82 * (i / (double) (BLADES - 1)));
			double len = 1.15 - 0.25 * (i / (double) (BLADES - 1));
			double tx = bp[0] + Math.cos(ang) * len, ty = bp[1] + Math.sin(ang) * len;
			// senkrecht zur Klinge
			double px = -Math.sin(ang), py = Math.cos(ang);
			double w0 = 0.15, w1 = 0.12;
			double mx = bp[0] + Math.cos(ang) * len * 0.42, my = bp[1] + Math.sin(ang) * len * 0.42;
			int cb = wa(c.base, alpha), cm = wa(Theme.mix(c.base, bladeTip(c, i, time), 0.55f), alpha), ct = wa(bladeTip(c, i, time), alpha);
			// Klinge als Drachen-Form: Basis -> Mitte (breit) -> Spitze
			f.tri(bp[0] - px * w0, bp[1] - py * w0, bp[0] + px * w0, bp[1] + py * w0, mx + px * w1, my + py * w1, cb, cb, cm, 0);
			f.tri(bp[0] - px * w0, bp[1] - py * w0, mx + px * w1, my + py * w1, mx - px * w1, my - py * w1, cb, cm, cm, 0);
			f.tri(mx - px * w1, my - py * w1, mx + px * w1, my + py * w1, tx, ty, cm, cm, ct, 0);

			if (Config.wingVeins) {
				int vein = veinColor(c, time, i);
				f.strip(bp[0], bp[1], tx, ty, 0.022, vein, 0.008);
				if (prevMid != null) f.strip(prevMid[0], prevMid[1], mx, my, 0.018, vein, 0.008);
				// kleine Seitenadern
				double qx = bp[0] + Math.cos(ang) * len * 0.7, qy = bp[1] + Math.sin(ang) * len * 0.7;
				f.strip(qx, qy, qx + px * 0.1 + Math.cos(ang) * 0.12, qy + py * 0.1 + Math.sin(ang) * 0.12, 0.016, vein, 0.008);
			}
			prevMid = new double[] {mx, my};
		}

		// Gold-Gerüst (leicht vor/hinter der Haut, damit es nicht flackert)
		f.polyStrip(BONE, c.gold, c.goldDark, 0.015);
		f.polyStrip(HOOK, c.gold, c.goldDark, 0.015);
		for (double[] t : SPIKES) {
			f.tri(t[0], t[1], t[2], t[3], t[4], t[5], c.goldDark, c.gold, c.goldLight, 0.016);
		}
	}

	private record Palette(int base, int tip, int vein, int gold, int goldDark, int goldLight) {}

	private static Palette wingPalette(double time) {
		Theme t = Theme.current();
		return switch (Config.wingColor) {
			case 1 -> new Palette(Theme.darker(t.accent(), 0.22f), t.accent(), Theme.mix(t.accent(), 0xFFFFFFFF, 0.35f),
					t.highlight(), Theme.darker(t.highlight(), 0.7f), Theme.mix(t.highlight(), 0xFFFFFFFF, 0.4f));
			case 2 -> new Palette(0xFF14060E, 0xFFFF0000, 0xFFFFFFFF, 0xFFFFC94A, 0xFFB8862A, 0xFFFFF0B0);
			case 3 -> new Palette(0xFF050507, 0xFF3A3A48, 0xFF9B4DFF, 0xFFB8BEC6, 0xFF6E747C, 0xFFF0F2F5);
			default -> new Palette(0xFF3A0006, 0xFFE0102A, 0xFFFF4A58, 0xFFFFC94A, 0xFFB8862A, 0xFFFFF0B0);
		};
	}

	private static int bladeTip(Palette c, int i, double time) {
		if (Config.wingColor == 2) return Theme.hsv(time * 0.25 + i * 0.09, 1f, 1f);
		return c.tip;
	}

	private static int veinColor(Palette c, double time, int i) {
		float pulse = 0.55f + 0.45f * (float) Math.sin(time * 3.0 - i * 0.6);
		int v = Config.wingColor == 2 ? Theme.hsv(time * 0.25 + i * 0.09 + 0.5, 0.6f, 1f) : c.vein;
		return Theme.withAlpha(v, Math.min(1f, Config.wingOpacity + 0.1f) * pulse);
	}

	private static int wa(int color, float alpha) {
		return Theme.withAlpha(color, alpha);
	}

	/** Punkt bei Anteil frac (0..1) entlang einer Linie aus Punkten. */
	private static double[] along(double[][] pts, double frac) {
		double total = 0;
		for (int i = 1; i < pts.length; i++) total += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
		double target = frac * total, acc = 0;
		for (int i = 1; i < pts.length; i++) {
			double seg = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
			if (acc + seg >= target) {
				double k = (target - acc) / seg;
				return new double[] {pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * k, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * k};
			}
			acc += seg;
		}
		double[] last = pts[pts.length - 1];
		return new double[] {last[0], last[1]};
	}

	// ---------------------------------------------------------------- Heiligenschein

	private static void drawHalo(PoseStack.Pose p, VertexConsumer buf, State s, double time) {
		double yaw = Math.toRadians(s.headYaw());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 up = new Vec3(0, 1, 0);

		double bob = Config.haloBob ? Math.sin(time * 2.2) * 0.035 : 0;
		Vec3 center = new Vec3(s.x(), s.y() + s.eye() + Config.haloHeight + bob - (s.crouch() ? 0.25 : 0), s.z())
				.add(fwd.scale(-0.04));
		double r = 0.30 * Config.haloSize;
		double band = 0.045 * Config.haloSize, height = 0.035 * Config.haloSize;
		int seg = 32;
		double spin = time * Config.haloSpin * 1.2;

		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
			int c0 = haloColor(a0, time), c1 = haloColor(a1, time);
			Vec3 i0 = ring(center, right, fwd, up, a0, r - band), i1 = ring(center, right, fwd, up, a1, r - band);
			Vec3 o0 = ring(center, right, fwd, up, a0, r + band), o1 = ring(center, right, fwd, up, a1, r + band);
			// flacher Ring
			quad2(p, buf, i0, o0, o1, i1, Theme.darker(c0, 0.85f), c0, c1, Theme.darker(c1, 0.85f));
			// Außenkante als kleines Band nach oben
			Vec3 o0u = o0.add(0, height, 0), o1u = o1.add(0, height, 0);
			quad2(p, buf, o0, o1, o1u, o0u, Theme.darker(c0, 0.7f), Theme.darker(c1, 0.7f), c1, c0);
		}

		if (Config.haloSpikes) {
			int n = 8;
			for (int k = 0; k < n; k++) {
				double a = spin + k * Math.PI * 2 / n;
				double w = 0.06;
				Vec3 b0 = ring(center, right, fwd, up, a - w, r + band).add(0, height, 0);
				Vec3 b1 = ring(center, right, fwd, up, a + w, r + band).add(0, height, 0);
				Vec3 tip = ring(center, right, fwd, up, a, r + band + 0.03).add(0, height + (k % 2 == 0 ? 0.13 : 0.08) * Config.haloSize, 0);
				int c = haloColor(a, time);
				quad2(p, buf, b0, b1, tip, tip, Theme.darker(c, 0.75f), Theme.darker(c, 0.75f), Theme.mix(c, 0xFFFFFFFF, 0.5f), Theme.mix(c, 0xFFFFFFFF, 0.5f));
			}
		}
	}

	/** Punkt auf dem leicht nach hinten gekippten Ring. */
	private static Vec3 ring(Vec3 c, Vec3 right, Vec3 fwd, Vec3 up, double a, double r) {
		double cx = Math.cos(a) * r, cz = Math.sin(a) * r;
		return c.add(right.scale(cx)).add(fwd.scale(cz)).add(up.scale(-cz * 0.28));
	}

	private static int haloColor(double angle, double time) {
		float glow = 0.85f + 0.15f * (float) Math.sin(time * 3 + angle * 2);
		int base = switch (Config.haloColor) {
			case 1 -> Theme.current().highlight();
			case 2 -> Theme.hsv(angle / (Math.PI * 2) + time * 0.2 * Config.haloSpin, 1f, 1f);
			case 3 -> 0xFFE0102A;
			default -> 0xFFFFC94A;
		};
		return Theme.mix(Theme.darker(base, glow), 0xFFFFFFFF, (glow - 0.85f) * 1.5f) | 0xFF000000;
	}

	// ---------------------------------------------------------------- Partikel

	private static final ParticleOptions RED_DUST = new DustParticleOptions(0xE0102A, 1.0f);
	private static final ParticleOptions GOLD_DUST = new DustParticleOptions(0xFFC94A, 0.8f);

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!Config.particles || p == null || mc.level == null || p.isInvisible() || p.isSpectator()) return;
		if (!Config.particlesFirstPerson && mc.options.getCameraType().isFirstPerson()) return;
		double speed = Math.sqrt(Mth.square(p.getX() - p.xo) + Mth.square(p.getZ() - p.zo));
		if (Config.particlesOnlyMoving && speed < 0.03 && p.onGround()) return;

		particleBudget += Config.particleAmount * 3.0;
		while (particleBudget >= 1) {
			particleBudget -= 1;
			double x = p.getX() + (RANDOM.nextDouble() - 0.5) * 0.7;
			double y = p.getY() + 0.1 + RANDOM.nextDouble() * 1.5;
			double z = p.getZ() + (RANDOM.nextDouble() - 0.5) * 0.7;
			ParticleOptions type = switch (Config.particleType) {
				case 1 -> ParticleTypes.FLAME;
				case 2 -> ParticleTypes.SOUL_FIRE_FLAME;
				case 3 -> ParticleTypes.END_ROD;
				case 4 -> ParticleTypes.HEART;
				case 5 -> ParticleTypes.ELECTRIC_SPARK;
				case 6 -> ParticleTypes.SMOKE;
				default -> RANDOM.nextInt(3) == 0 ? GOLD_DUST : RED_DUST;
			};
			double vy = Config.particleType == 4 ? 0.05 : 0.01 + RANDOM.nextDouble() * 0.02;
			double vxz = Config.particleType == 3 ? 0.02 : 0.005;
			mc.level.addParticle(type, x, y, z, (RANDOM.nextDouble() - 0.5) * vxz, vy, (RANDOM.nextDouble() - 0.5) * vxz);
		}
	}

	// ================================================================ Geometrie-Helfer

	private static void quad2(PoseStack.Pose p, VertexConsumer v, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
		vtx(p, v, a, ca); vtx(p, v, b, cb); vtx(p, v, c, cc); vtx(p, v, d, cd);
		vtx(p, v, d, cd); vtx(p, v, c, cc); vtx(p, v, b, cb); vtx(p, v, a, ca);
	}

	private static void vtx(PoseStack.Pose p, VertexConsumer v, Vec3 pt, int c) {
		v.addVertex(p, (float) pt.x, (float) pt.y, (float) pt.z).setColor(c);
	}

	/** Wandelt Flügel-Koordinaten (u = nach außen, v = nach oben, n = Normale) in Welt-Punkte. */
	private record Frame(PoseStack.Pose p, VertexConsumer buf, Vec3 root, Vec3 dir, Vec3 normal, double scale) {
		Vec3 at(double u, double v, double n) {
			return root.add(dir.scale(u * scale)).add(0, v * scale, 0).add(normal.scale(n));
		}

		/** Dreieck, von beiden Seiten sichtbar (n = Abstand zur Flügelfläche, beidseitig). */
		void tri(double u1, double v1, double u2, double v2, double u3, double v3, int c1, int c2, int c3, double n) {
			if (n == 0) {
				one(u1, v1, u2, v2, u3, v3, c1, c2, c3, 0);
			} else {
				one(u1, v1, u2, v2, u3, v3, c1, c2, c3, n);
				one(u1, v1, u2, v2, u3, v3, c1, c2, c3, -n);
			}
		}

		private void one(double u1, double v1, double u2, double v2, double u3, double v3, int c1, int c2, int c3, double n) {
			Vec3 a = at(u1, v1, n), b = at(u2, v2, n), c = at(u3, v3, n);
			quad2(p, buf, a, b, c, c, c1, c2, c3, c3);
		}

		/** Dünner Streifen (Ader) von A nach B. */
		void strip(double u1, double v1, double u2, double v2, double width, int color, double n) {
			double dx = u2 - u1, dy = v2 - v1, len = Math.hypot(dx, dy);
			if (len < 1e-6) return;
			double px = -dy / len * width / 2, py = dx / len * width / 2;
			tri(u1 - px, v1 - py, u1 + px, v1 + py, u2 + px, v2 + py, color, color, color, n);
			tri(u1 - px, v1 - py, u2 + px, v2 + py, u2 - px, v2 - py, color, color, color, n);
		}

		/** Linie mit abnehmender Breite (pts: u, v, Breite) – für das Gold-Gerüst. */
		void polyStrip(double[][] pts, int light, int dark, double n) {
			for (int i = 1; i < pts.length; i++) {
				double[] a = pts[i - 1], b = pts[i];
				double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
				double nx = -dy / len, ny = dx / len;
				double wa = a[2] / 2, wb = Math.max(0.004, b[2] / 2);
				tri(a[0] - nx * wa, a[1] - ny * wa, a[0] + nx * wa, a[1] + ny * wa, b[0] + nx * wb, b[1] + ny * wb, dark, light, light, n);
				tri(a[0] - nx * wa, a[1] - ny * wa, b[0] + nx * wb, b[1] + ny * wb, b[0] - nx * wb, b[1] - ny * wb, dark, light, dark, n);
			}
		}
	}
}
