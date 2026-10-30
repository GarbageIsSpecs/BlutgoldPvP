package de.blutgold.pvp;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Animierter Rahmen um den Block, auf den du schaust (Regenbogen, Einfarbig oder Theme-Verlauf).
 * Gezeichnet wie die Hitboxen (dünne Quader statt Linien-Shader), deshalb
 * braucht es keine Core Shaders aus einem Resource Pack.
 */
public final class RainbowOutline {
	/** Eine Kante des Blocks: Start- und Endpunkt in Welt-Koordinaten. */
	private record Edge(double x1, double y1, double z1, double x2, double y2, double z2) {}
	private record Shape(List<Edge> edges, List<AABB> boxes) {}

	private static volatile Shape shape = new Shape(List.of(), List.of());

	public static final String[] MODES = {"Regenbogen", "Einfarbig", "Theme-Verlauf"};

	/** Kleiner Abstand nach außen, damit nichts im Block flackert. */
	private static final double GROW = 0.002;
	/** Wie viele Farb-Stücke pro Blocklänge (mehr = weicherer Verlauf). */
	private static final int SEGMENTS_PER_BLOCK = 10;

	private RainbowOutline() {}

	public static void extract(LevelExtractionContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.rainbowOutline || mc.player == null || ctx.level() == null
				|| !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			shape = new Shape(List.of(), List.of());
			return;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = ctx.level().getBlockState(pos);
		VoxelShape voxel = state.isAir() ? null : state.getShape(ctx.level(), pos, CollisionContext.of(mc.player));
		if (voxel == null || voxel.isEmpty()) {
			shape = new Shape(List.of(), List.of());
			return;
		}
		List<Edge> edges = new ArrayList<>();
		double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();
		voxel.forAllEdges((x1, y1, z1, x2, y2, z2) ->
				edges.add(new Edge(ox + x1, oy + y1, oz + z1, ox + x2, oy + y2, oz + z2)));
		List<AABB> boxes = new ArrayList<>();
		if (Config.outlineFill > 0.001f) {
			for (AABB b : voxel.toAabbs()) boxes.add(b.move(ox, oy, oz).inflate(GROW));
		}
		shape = new Shape(List.copyOf(edges), List.copyOf(boxes));
	}

	public static void render(LevelRenderContext ctx) {
		Shape snapshot = shape;
		if (snapshot.edges().isEmpty()) return;

		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		double time = time();
		double thick = Config.outlineThickness * 0.0016 + GROW;
		float opacity = Config.outlineOpacity;
		float fill = Config.outlineFill;

		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buf) -> {
			if (fill > 0.001f) {
				int fillColor = Theme.withAlpha(colorAt(0, time), fill);
				for (AABB b : snapshot.boxes()) Hitboxes.drawBox(p, buf, b, fillColor);
			}
			for (Edge e : snapshot.edges()) {
				double len = Math.sqrt(sq(e.x2 - e.x1) + sq(e.y2 - e.y1) + sq(e.z2 - e.z1));
				int pieces = Math.max(1, (int) Math.ceil(len * SEGMENTS_PER_BLOCK));
				for (int i = 0; i < pieces; i++) {
					double a = i / (double) pieces, b = (i + 1) / (double) pieces;
					double ax = lerp(a, e.x1, e.x2), ay = lerp(a, e.y1, e.y2), az = lerp(a, e.z1, e.z2);
					double bx = lerp(b, e.x1, e.x2), by = lerp(b, e.y1, e.y2), bz = lerp(b, e.z1, e.z2);
					// Farbe hängt von der Position ab -> der Verlauf "läuft" um den Block herum
					double posAlong = ((ax + bx) + (ay + by) + (az + bz)) * 0.5;
					int color = Theme.withAlpha(colorAt(posAlong, time), opacity);
					AABB piece = new AABB(
							Math.min(ax, bx) - thick, Math.min(ay, by) - thick, Math.min(az, bz) - thick,
							Math.max(ax, bx) + thick, Math.max(ay, by) + thick, Math.max(az, bz) + thick);
					Hitboxes.drawBox(p, buf, piece, color);
				}
			}
		});
		pose.popPose();
	}

	/** Aktuelle Animationszeit (Umdrehungen), abhängig von der eingestellten Geschwindigkeit. */
	public static double time() {
		return System.currentTimeMillis() / 1000.0 * Config.outlineSpeed;
	}

	/** Rahmenfarbe an einer Position (in Blöcken entlang des Rahmens) – auch für die Vorschau im Menü. */
	public static int colorAt(double posAlong, double time) {
		double phase = time + posAlong * Config.outlineSpread;
		return switch (Config.outlineMode) {
			case 1 -> {
				// Einfarbig, leichtes Pulsieren der Helligkeit
				float v = 0.85f + 0.15f * (float) Math.sin(phase * Math.PI * 2);
				yield Theme.hsv(Config.outlineHue, Config.outlineSaturation, v);
			}
			case 2 -> {
				Theme t = Theme.current();
				float k = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2);
				yield Theme.mix(t.accent(), t.highlight(), k) | 0xFF000000;
			}
			default -> Theme.hsv(phase, Config.outlineSaturation, 1f);
		};
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}

	private static double sq(double v) {
		return v * v;
	}
}
