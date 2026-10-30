package de.blutgold.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

/** Zählt deine Combo (Treffer ohne selbst getroffen zu werden) und misst die Reichweite jedes Schlags. */
public final class CombatTracker {
	public static int combo = 0;
	public static double lastReach = -1;
	public static long lastHitTime = 0;
	private static int lastHurtTime = 0;

	private CombatTracker() {}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			Minecraft mc = Minecraft.getInstance();
			if (player == mc.player && entity instanceof LivingEntity) {
				Vec3 eye = player.getEyePosition();
				AABB box = entity.getBoundingBox();
				double cx = Math.max(box.minX, Math.min(eye.x, box.maxX));
				double cy = Math.max(box.minY, Math.min(eye.y, box.maxY));
				double cz = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
				lastReach = eye.distanceTo(new Vec3(cx, cy, cz));
				combo++;
				lastHitTime = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null) {
			combo = 0;
			return;
		}
		int hurt = mc.player.hurtTime;
		if (hurt > 0 && lastHurtTime == 0) combo = 0; // du wurdest getroffen
		lastHurtTime = hurt;
		if (combo > 0 && System.currentTimeMillis() - lastHitTime > 3000) combo = 0;
	}
}
