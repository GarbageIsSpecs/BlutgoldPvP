package de.blutgold.pvp;

import java.util.ArrayList;
import java.util.List;
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

	/** Gesamtgröße der Flügel bei "Größe 1.00x". */
	private static final double WING_BASE_SCALE = 0.6;

	/** Knochen (Gold) entlang der Oberkante: u, v, Breite. */
	private static final double[][] BONE = {
			{0.06, 0.05, 0.12}, {0.22, 0.40, 0.10}, {0.45, 0.62, 0.085}, {0.80, 0.88, 0.07},
			{1.20, 1.12, 0.055}, {1.62, 1.34, 0.04}, {2.02, 1.56, 0.0}
	};
	/** Untere S-Kralle (Gold) am Schulteransatz. */
	private static final double[][] HOOK = {
			{0.06, 0.05, 0.09}, {0.01, -0.25, 0.08}, {0.09, -0.50, 0.07}, {0.27, -0.63, 0.055},
			{0.37, -0.52, 0.04}, {0.31, -0.40, 0.0}
	};
	/** Goldene Zacken (Dreiecke) – Kamm und "Drachenkopf" vorne. */
	private static final double[][] SPIKES = {
			{0.16, 0.36, 0.30, 0.47, 0.12, 0.74},
			{0.36, 0.56, 0.52, 0.65, 0.42, 0.90},
			{0.64, 0.76, 0.80, 0.87, 0.78, 1.04},
			{0.24, 0.42, 0.10, 0.30, 0.50, 0.30},
			{0.30, 0.52, 0.20, 0.44, 0.58, 0.52},
			{0.08, -0.18, 0.16, -0.30, 0.42, -0.08},
			{0.20, -0.58, 0.30, -0.62, 0.30, -0.82},
	};

	private static final int BLADES = 10;
	/** Je Klinge: Basis (u,v), Richtung (cos,sin), Länge. */
	private static final double[][] BLADE = new double[BLADES][5];
	/** Risse in der Haut: Segmente u1,v1,u2,v2,Phase. */
	private static final List<double[]> CRACKS = new ArrayList<>();
	private static final double FILL = 0.62;

	static {
		for (int i = 0; i < BLADES; i++) {
			double k = i / (double) (BLADES - 1);
			double[] bp = along(BONE, 0.07 + 0.83 * k);
			double ang = Math.toRadians(-104 + 86 * k);
			BLADE[i] = new double[] {bp[0], bp[1], Math.cos(ang), Math.sin(ang), 1.18 - 0.30 * k};
		}
		Random r = new Random(1337);
		// Zell-Risse in den Flächen zwischen zwei Klingen
		for (int i = 0; i + 1 < BLADES; i++) {
			double[][] grid = new double[4][3];
			double[][][] pts = new double[4][3][];
			for (int a = 0; a < 4; a++) {
				for (int b = 0; b < 3; b++) {
					double sx = a / 3.0, ty = b / 2.0;
					if (a > 0 && a < 3) sx += (r.nextDouble() - 0.5) * 0.18;
					if (b > 0) ty += (r.nextDouble() - 0.5) * 0.22;
					pts[a][b] = gapPoint(i, sx, Math.min(1, Math.max(0.05, ty)));
				}
			}
			for (int a = 0; a < 4; a++) {
				for (int b = 0; b < 3; b++) {
					if (a + 1 < 4 && b > 0 && r.nextDouble() < 0.8) addCrack(pts[a][b], pts[a + 1][b], r);
					if (b + 1 < 3 && a > 0 && a < 3 && r.nextDouble() < 0.85) addCrack(pts[a][b], pts[a][b + 1], r);
				}
			}
		}
		// Adern in den Klingen: Mittelader + schräge Äste
		for (int i = 0; i < BLADES; i++) {
			addCrack(bladePoint(i, 0.05, 0), bladePoint(i, 0.92, 0), r);
			for (double t : new double[] {0.28, 0.5, 0.72}) {
				double side = r.nextBoolean() ? 1 : -1;
				addCrack(bladePoint(i, t, 0), bladePoint(i, t + 0.14, side * 0.8), r);
				if (r.nextDouble() < 0.6) addCrack(bladePoint(i, t + 0.05, 0), bladePoint(i, t + 0.2, -side * 0.75), r);
			}
		}
	}

	private static void addCrack(double[] a, double[] b, Random r) {
		// leicht geknickt, damit es nach echten Rissen aussieht
		double mx = (a[0] + b[0]) / 2 + (r.nextDouble() - 0.5) * 0.06;
		double my = (a[1] + b[1]) / 2 + (r.nextDouble() - 0.5) * 0.06;
		double phase = r.nextDouble() * Math.PI * 2;
		CRACKS.add(new double[] {a[0], a[1], mx, my, phase});
		CRACKS.add(new double[] {mx, my, b[0], b[1], phase});
	}

	/** Punkt in Klinge i: t = 0 (Basis) .. 1 (Spitze), s = -1 .. 1 quer. */
	private static double[] bladePoint(int i, double t, double s) {
		double[] b = BLADE[i];
		double len = b[4];
		double half = t < 0.42 ? 0.20 + (0.17 - 0.20) * (t / 0.42) : 0.17 * (1 - (t - 0.42) / 0.58);
		double px = -b[3], py = b[2];
		return new double[] {b[0] + b[2] * len * t + px * half * s, b[1] + b[3] * len * t + py * half * s};
	}

	/** Punkt in der Fläche zwischen Klinge i und i+1 (sx quer, ty von Knochen nach unten). */
	private static double[] gapPoint(int i, double sx, double ty) {
		double[] a = {BLADE[i][0], BLADE[i][1]}, b = {BLADE[i + 1][0], BLADE[i + 1][1]};
		double[] d = bladePoint(i, FILL, 0), c = bladePoint(i + 1, FILL, 0);
		double tx = a[0] + (b[0] - a[0]) * sx, tyy = a[1] + (b[1] - a[1]) * sx;
		double bx = d[0] + (c[0] - d[0]) * sx, by = d[1] + (c[1] - d[1]) * sx;
		return new double[] {tx + (bx - tx) * ty, tyy + (by - tyy) * ty};
	}

	private static void drawWing(PoseStack.Pose p, VertexConsumer buf, State s, double time, int side) {
		double scale = Config.wingScale * WING_BASE_SCALE;
		double yaw = Math.toRadians(s.bodyYaw());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));

		// Flügelschlag
		double speedMul = Config.wingFlapSpeed * (s.flying() ? 2.2 : s.fast() ? 1.6 : 1.0);
		boolean active = s.moving() || s.flying() || !Config.wingFlapOnlyMoving;
		double amp = s.flying() ? 0.42 : active ? 0.2 : 0.05;
		double base = 0.15 + Config.wingSpread * 0.75;
		double a = base + amp * Math.sin(time * Math.PI * 2 * 0.7 * speedMul);

		Vec3 root = new Vec3(s.x(), s.y() + (s.crouch() ? 1.1 : 1.32), s.z())
				.add(back.scale(0.17)).add(right.scale(side * 0.05));
		Vec3 dir = right.scale(side * Math.cos(a)).add(back.scale(Math.sin(a)));
		Vec3 normal = back.scale(Math.cos(a)).subtract(right.scale(side * Math.sin(a)));
		Frame f = new Frame(p, buf, root, dir, normal, scale);

		Palette c = wingPalette(time);
		float alpha = Config.wingOpacity;

		// 1) Haut: dunkle Fläche zwischen den Klingen (Zellen)
		int cellDark = wa(c.base, alpha), cellMid = wa(Theme.mix(c.base, c.tip, 0.35f), alpha);
		double[] r0 = {0.04, 0.05}, r1 = bladePoint(0, FILL, 0);
		f.tri(r0[0], r0[1], BLADE[0][0], BLADE[0][1], r1[0], r1[1], cellDark, cellDark, cellMid, 0);
		f.tri(r0[0], r0[1], r1[0], r1[1], 0.12, -0.46, cellDark, cellMid, cellDark, 0);
		for (int i = 0; i + 1 < BLADES; i++) {
			double[] A = {BLADE[i][0], BLADE[i][1]}, B = {BLADE[i + 1][0], BLADE[i + 1][1]};
			double[] D = bladePoint(i, FILL, 0), C = bladePoint(i + 1, FILL, 0);
			f.tri(A[0], A[1], B[0], B[1], C[0], C[1], cellDark, cellDark, cellMid, 0);
			f.tri(A[0], A[1], C[0], C[1], D[0], D[1], cellDark, cellMid, cellMid, 0);
		}

		// 2) Klingen mit Verlauf: dunkel am Knochen -> leuchtend an der Spitze
		for (int i = 0; i < BLADES; i++) {
			int tip = bladeTip(c, i, time);
			int cb = wa(Theme.mix(c.base, tip, 0.15f), alpha), cm = wa(Theme.mix(c.base, tip, 0.5f), alpha), ct = wa(tip, Math.min(1f, alpha + 0.1f));
			double[] b0l = bladePoint(i, 0, -1), b0r = bladePoint(i, 0, 1), ml = bladePoint(i, 0.42, -1), mr = bladePoint(i, 0.42, 1), t = bladePoint(i, 1, 0);
			f.tri(b0l[0], b0l[1], b0r[0], b0r[1], mr[0], mr[1], cb, cb, cm, 0);
			f.tri(b0l[0], b0l[1], mr[0], mr[1], ml[0], ml[1], cb, cm, cm, 0);
			f.tri(ml[0], ml[1], mr[0], mr[1], t[0], t[1], cm, cm, ct, 0);

			// Leuchtende Kanten + weicher Glow drumherum
			int edge = Theme.withAlpha(Theme.mix(tip, 0xFFFFFFFF, 0.15f), 0.75f * alpha);
			int glow = Theme.withAlpha(tip, 0.16f * alpha);
			f.strip(ml[0], ml[1], t[0], t[1], 0.075, glow, 0.003);
			f.strip(mr[0], mr[1], t[0], t[1], 0.075, glow, 0.003);
			f.strip(ml[0], ml[1], t[0], t[1], 0.018, edge, 0.006);
			f.strip(mr[0], mr[1], t[0], t[1], 0.018, edge, 0.006);
		}

		// 3) Risse: breiter schwacher Glow + heller Kern, Puls läuft vom Körper nach außen
		if (Config.wingVeins) {
			for (double[] k : CRACKS) {
				double dist = Math.hypot(k[0], k[1]);
				float pulse = 0.55f + 0.45f * (float) Math.sin(time * 3.2 - dist * 3.5 + k[4] * 0.3);
				int core = crackColor(c, time, dist);
				f.strip(k[0], k[1], k[2], k[3], 0.07, Theme.withAlpha(core, 0.18f * pulse * alpha), 0.007);
				f.strip(k[0], k[1], k[2], k[3], 0.02, Theme.withAlpha(Theme.mix(core, 0xFFFFFFFF, 0.25f * pulse), Math.min(1f, (0.55f + 0.45f * pulse) * (alpha + 0.15f))), 0.009);
			}
		}

		// 4) Gold-Gerüst mit wanderndem Glanz und leichtem Gold-Glow
		f.polyGlow(BONE, Theme.withAlpha(c.gold, 0.22f), 0.08, 0.012);
		f.polyGlow(HOOK, Theme.withAlpha(c.gold, 0.22f), 0.07, 0.012);
		f.polyStrip(BONE, c, time, 0.016);
		f.polyStrip(HOOK, c, time, 0.016);
		for (double[] t : SPIKES) {
			int shine = shine(c, (t[4]), time);
			f.tri(t[0], t[1], t[2], t[3], t[4], t[5], c.goldDark, c.gold, shine, 0.017);
		}
	}

	private record Palette(int base, int tip, int vein, int gold, int goldDark, int goldLight) {}

	private static Palette wingPalette(double time) {
		Theme t = Theme.current();
		return switch (Config.wingColor) {
			case 1 -> new Palette(Theme.darker(t.accent(), 0.15f), t.accent(), Theme.mix(t.accent(), 0xFFFFFFFF, 0.3f),
					t.highlight(), Theme.darker(t.highlight(), 0.6f), Theme.mix(t.highlight(), 0xFFFFFFFF, 0.6f));
			case 2 -> new Palette(0xFF100408, 0xFFFF0000, 0xFFFFFFFF, 0xFFFFC94A, 0xFFA8761E, 0xFFFFF6D0);
			case 3 -> new Palette(0xFF040406, 0xFF34343F, 0xFF9B4DFF, 0xFFB8BEC6, 0xFF5E646C, 0xFFFFFFFF);
			default -> new Palette(0xFF1C0003, 0xFFE8102C, 0xFFFF2E44, 0xFFFFC94A, 0xFFA8761E, 0xFFFFF6D0);
		};
	}

	private static int bladeTip(Palette c, int i, double time) {
		if (Config.wingColor == 2) return Theme.hsv(time * 0.25 + i * 0.08, 1f, 1f);
		return c.tip;
	}

	private static int crackColor(Palette c, double time, double dist) {
		if (Config.wingColor == 2) return Theme.hsv(time * 0.25 + dist * 0.35, 0.85f, 1f);
		return c.vein;
	}

	/** Gold mit wanderndem Glanz-Streifen. */
	private static int shine(Palette c, double u, double time) {
		double w = Math.sin(time * 2.0 - u * 3.0);
		float k = (float) Math.pow(Math.max(0, w), 10);
		return Theme.mix(c.gold, c.goldLight, 0.25f + 0.75f * k);
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

	/** Sauberer, voll leuchtender Ring: heller Kern + mehrere weiche Glow-Schichten. */
	private static void drawHalo(PoseStack.Pose p, VertexConsumer buf, State s, double time) {
		double yaw = Math.toRadians(s.headYaw());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 up = new Vec3(0, 1, 0);

		double bob = Config.haloBob ? Math.sin(time * 2.2) * 0.025 : 0;
		Vec3 center = new Vec3(s.x(), s.y() + s.eye() + Config.haloHeight + bob - (s.crouch() ? 0.25 : 0), s.z())
				.add(fwd.scale(-0.03));
		double r = 0.21 * Config.haloSize;
		float glow = Config.haloGlow;
		int seg = 48;
		// Schichten: {halbe Breite, Deckkraft, Weiß-Anteil}
		double[][] layers = {
				{0.075 * glow, 0.10, 0.0},
				{0.045 * glow, 0.22, 0.0},
				{0.024, 0.55, 0.15},
				{0.011, 1.0, 0.55},
		};
		for (int L = 0; L < layers.length; L++) {
			double hw = layers[L][0] * Config.haloSize;
			float al = (float) layers[L][1], white = (float) layers[L][2];
			double lift = L * 0.0015;
			for (int i = 0; i < seg; i++) {
				double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
				int c0 = Theme.withAlpha(Theme.mix(haloColor(a0, time), 0xFFFFFFFF, white), al);
				int c1 = Theme.withAlpha(Theme.mix(haloColor(a1, time), 0xFFFFFFFF, white), al);
				int e0 = L < 2 ? Theme.withAlpha(c0, 0f) : c0, e1 = L < 2 ? Theme.withAlpha(c1, 0f) : c1;
				Vec3 m0 = ring(center, right, fwd, up, a0, r).add(0, lift, 0), m1 = ring(center, right, fwd, up, a1, r).add(0, lift, 0);
				Vec3 i0 = ring(center, right, fwd, up, a0, r - hw).add(0, lift, 0), i1 = ring(center, right, fwd, up, a1, r - hw).add(0, lift, 0);
				Vec3 o0 = ring(center, right, fwd, up, a0, r + hw).add(0, lift, 0), o1 = ring(center, right, fwd, up, a1, r + hw).add(0, lift, 0);
				// zur Mitte hin voll, nach außen ausblendend (weicher Glow)
				quad2(p, buf, i0, m0, m1, i1, e0, c0, c1, e1);
				quad2(p, buf, m0, o0, o1, m1, c0, e0, e1, c1);
			}
		}
	}

	/** Punkt auf dem leicht nach hinten gekippten Ring. */
	private static Vec3 ring(Vec3 c, Vec3 right, Vec3 fwd, Vec3 up, double a, double r) {
		double cx = Math.cos(a) * r, cz = Math.sin(a) * r;
		return c.add(right.scale(cx)).add(fwd.scale(cz)).add(up.scale(-cz * 0.22));
	}

	private static int haloColor(double angle, double time) {
		double spin = time * Config.haloSpin;
		float shimmer = (float) Math.pow(Math.max(0, Math.sin(angle - spin * 1.5)), 6);
		int base = switch (Config.haloColor) {
			case 1 -> Theme.current().highlight();
			case 2 -> Theme.hsv(angle / (Math.PI * 2) + spin * 0.2, 1f, 1f);
			case 3 -> 0xFFFF1E36;
			default -> 0xFFFFC94A;
		};
		return Theme.mix(base, 0xFFFFFFFF, 0.1f + 0.45f * shimmer) | 0xFF000000;
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

		/** Gold-Linie mit abnehmender Breite und wanderndem Glanz (pts: u, v, Breite). */
		void polyStrip(double[][] pts, Palette c, double time, double n) {
			for (int i = 1; i < pts.length; i++) {
				double[] a = pts[i - 1], b = pts[i];
				double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
				double nx = -dy / len, ny = dx / len;
				double wa = a[2] / 2, wb = Math.max(0.004, b[2] / 2);
				int la = shine(c, a[0], time), lb = shine(c, b[0], time);
				// Oberkante hell, Unterkante dunkler -> wirkt plastisch
				tri(a[0] - nx * wa, a[1] - ny * wa, a[0] + nx * wa, a[1] + ny * wa, b[0] + nx * wb, b[1] + ny * wb, c.goldDark, la, lb, n);
				tri(a[0] - nx * wa, a[1] - ny * wa, b[0] + nx * wb, b[1] + ny * wb, b[0] - nx * wb, b[1] - ny * wb, c.goldDark, lb, c.goldDark, n);
			}
		}

		/** Weicher, breiter Schein hinter einer Linie. */
		void polyGlow(double[][] pts, int color, double width, double n) {
			for (int i = 1; i < pts.length; i++) {
				strip(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1], width, color, n);
			}
		}
	}
}
