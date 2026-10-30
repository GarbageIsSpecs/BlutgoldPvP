package de.blutgold.pvp;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Zeichnet Hitboxen (wie F3+B, nur schöner): roter Rahmen, leichte rote Füllung,
 * dunklere Linie auf Augenhöhe. Wird kurz gold, wenn das Ziel Schaden bekommt.
 * Hitboxen werden NICHT durch Wände gezeichnet und unsichtbare Spieler bleiben unsichtbar.
 */
public final class Hitboxes {
	private record Box(AABB box, double eyeY, int color, int fill) {}

	/** Wird beim Extrahieren neu gebaut und dann als Ganzes ausgetauscht (thread-sicher). */
	private static volatile List<Box> boxes = List.of();
	private static final double MAX_DIST_SQ = 64 * 64;
	private static final double EDGE = 0.012;

	private Hitboxes() {}

	public static void extract(LevelExtractionContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.hitboxes || mc.player == null || ctx.level() == null) {
			boxes = List.of();
			return;
		}
		List<Box> found = new ArrayList<>();

		float pt = ctx.deltaTracker().getGameTimeDeltaPartialTick(true);
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();

		for (Entity e : ctx.level().entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
			if (Config.hitboxesPlayersOnly && !(e instanceof Player)) continue;
			if (e == mc.player && firstPerson) continue;
			if (e.isInvisible()) continue;
			if (e.distanceToSqr(mc.player) > MAX_DIST_SQ) continue;

			double x = Mth.lerp(pt, e.xo, e.getX());
			double y = Mth.lerp(pt, e.yo, e.getY());
			double z = Mth.lerp(pt, e.zo, e.getZ());
			AABB box = e.getBoundingBox().move(x - e.getX(), y - e.getY(), z - e.getZ());

			boolean hurt = living.hurtTime > 0;
			Theme t = Theme.current();
			int base = e instanceof Player ? t.accent() : t.highlight();
			int color = hurt ? 0xFFFFFFFF : base;
			int fill = hurt ? 0x30FFFFFF : Theme.withAlpha(base, 0.13f);
			found.add(new Box(box, y + e.getEyeHeight(), color, fill));
		}
		boxes = List.copyOf(found);
	}

	public static void render(LevelRenderContext ctx) {
		List<Box> snapshot = boxes;
		if (snapshot.isEmpty()) return;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buf) -> {
			for (Box b : snapshot) {
				drawBox(p, buf, b.box(), b.fill());
				drawEdges(p, buf, b.box(), b.color(), EDGE);
				// Augenhöhe als dunklere Linie
				AABB eye = new AABB(b.box().minX, b.eyeY(), b.box().minZ, b.box().maxX, b.eyeY(), b.box().maxZ);
				drawEdges(p, buf, eye, Theme.darker(Theme.current().accent(), 0.55f), EDGE * 0.8);
			}
		});
		pose.popPose();
	}

	/** 12 Kanten als dünne Quader – funktioniert ohne Linien-Shader. */
	private static void drawEdges(PoseStack.Pose p, VertexConsumer buf, AABB b, int color, double t) {
		double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
		for (double y : ys) for (double z : zs) {
			if (b.maxY == b.minY && y == b.maxY) continue;
			drawBox(p, buf, new AABB(b.minX - t, y - t, z - t, b.maxX + t, y + t, z + t), color);
		}
		for (double x : xs) for (double z : zs) {
			if (b.maxY == b.minY) continue;
			drawBox(p, buf, new AABB(x - t, b.minY - t, z - t, x + t, b.maxY + t, z + t), color);
		}
		for (double x : xs) for (double y : ys) {
			if (b.maxY == b.minY && y == b.maxY) continue;
			drawBox(p, buf, new AABB(x - t, y - t, b.minZ - t, x + t, y + t, b.maxZ + t), color);
		}
	}

	static void drawBox(PoseStack.Pose p, VertexConsumer v, AABB b, int c) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		quad(p, v, c, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		quad(p, v, c, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
		quad(p, v, c, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
		quad(p, v, c, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
		quad(p, v, c, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
		quad(p, v, c, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
	}

	private static void quad(PoseStack.Pose p, VertexConsumer v, int c,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		v.addVertex(p, ax, ay, az).setColor(c);
		v.addVertex(p, bx, by, bz).setColor(c);
		v.addVertex(p, cx, cy, cz).setColor(c);
		v.addVertex(p, dx, dy, dz).setColor(c);
	}
}
