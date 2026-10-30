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
 * Animierter Regenbogen-Rahmen um den Block, auf den du schaust.
 * Gezeichnet wie die Hitboxen (dünne Quader statt Linien-Shader), deshalb
 * braucht es keine Core Shaders aus einem Resource Pack.
 */
public final class RainbowOutline {
	/** Eine Kante des Blocks: Start- und Endpunkt in Welt-Koordinaten. */
	private record Edge(double x1, double y1, double z1, double x2, double y2, double z2) {}

	private static volatile List<Edge> edges = List.of();

	/** Dicke der Linie (etwas dicker als Vanilla, damit der schwarze Rahmen verdeckt wird). */
	private static final double THICK = 0.0065;
	/** Kleiner Abstand nach außen, damit nichts im Block flackert. */
	private static final double GROW = 0.002;
	/** Wie viele Farb-Stücke pro Blocklänge (mehr = weicherer Verlauf). */
	private static final int SEGMENTS_PER_BLOCK = 10;
	/** Wie schnell die Farben wandern (Umdrehungen pro Sekunde). */
	private static final double SPEED = 0.35;
	/** Wie viele Regenbögen über die Kanten verteilt sind. */
	private static final double SPREAD = 0.6;

	private RainbowOutline() {}

	public static void extract(LevelExtractionContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.rainbowOutline || mc.player == null || ctx.level() == null
				|| !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			edges = List.of();
			return;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = ctx.level().getBlockState(pos);
		if (state.isAir()) {
			edges = List.of();
			return;
		}
		VoxelShape shape = state.getShape(ctx.level(), pos, CollisionContext.of(mc.player));
		if (shape.isEmpty()) {
			edges = List.of();
			return;
		}
		List<Edge> found = new ArrayList<>();
		double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();
		shape.forAllEdges((x1, y1, z1, x2, y2, z2) ->
				found.add(new Edge(ox + x1, oy + y1, oz + z1, ox + x2, oy + y2, oz + z2)));
		edges = List.copyOf(found);
	}

	public static void render(LevelRenderContext ctx) {
		List<Edge> snapshot = edges;
		if (snapshot.isEmpty()) return;

		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		double time = System.currentTimeMillis() / 1000.0 * SPEED;

		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buf) -> {
			for (Edge e : snapshot) {
				double len = Math.sqrt(sq(e.x2 - e.x1) + sq(e.y2 - e.y1) + sq(e.z2 - e.z1));
				int pieces = Math.max(1, (int) Math.ceil(len * SEGMENTS_PER_BLOCK));
				for (int i = 0; i < pieces; i++) {
					double a = i / (double) pieces, b = (i + 1) / (double) pieces;
					double ax = lerp(a, e.x1, e.x2), ay = lerp(a, e.y1, e.y2), az = lerp(a, e.z1, e.z2);
					double bx = lerp(b, e.x1, e.x2), by = lerp(b, e.y1, e.y2), bz = lerp(b, e.z1, e.z2);
					// Farbe hängt von der Position ab -> der Regenbogen "läuft" um den Block herum
					double hue = time + ((ax + bx) + (ay + by) + (az + bz)) * 0.5 * SPREAD;
					int color = rainbow(hue);
					double t = THICK + GROW;
					AABB piece = new AABB(
							Math.min(ax, bx) - t, Math.min(ay, by) - t, Math.min(az, bz) - t,
							Math.max(ax, bx) + t, Math.max(ay, by) + t, Math.max(az, bz) + t);
					Hitboxes.drawBox(p, buf, piece, color);
				}
			}
		});
		pose.popPose();
	}

	/** Regenbogenfarbe (voll gesättigt, voll hell) als ARGB. */
	private static int rainbow(double hue) {
		double h = (hue - Math.floor(hue)) * 6.0;
		int sector = (int) h;
		double f = h - sector;
		int up = (int) Math.round(f * 255), down = 255 - up;
		int r, g, b;
		switch (sector) {
			case 0 -> { r = 255; g = up; b = 0; }
			case 1 -> { r = down; g = 255; b = 0; }
			case 2 -> { r = 0; g = 255; b = up; }
			case 3 -> { r = 0; g = down; b = 255; }
			case 4 -> { r = up; g = 0; b = 255; }
			default -> { r = 255; g = 0; b = down; }
		}
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}

	private static double sq(double v) {
		return v * v;
	}
}
