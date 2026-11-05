package de.botarmee;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Die ganze Schlacht: Aufstellen, Countdown, Ablauf (Sequenzen), Kampf-KI und das "Rigging".
 * Läuft auf dem Server (im Einzelspieler auf dem eingebauten Server).
 */
public final class Battle {
	private Battle() {}

	public enum State { IDLE, READY, COUNTDOWN, FIGHT, PAUSED, OVER }

	static final class Side {
		final int index;
		final BattleConfig.Team cfg;
		final List<BotEntity> bots = new ArrayList<>();
		Vec3 start, anchor, forward, right;
		int phase = 0;
		int phaseTicks = 0;
		int startCount;
		double baseDamage = 1, damageMult = 1, takenMult = 1;
		double speed = 0.3;
		PlayerTeam team;

		Side(int index, BattleConfig.Team cfg) {
			this.index = index;
			this.cfg = cfg;
		}

		int action() {
			if (cfg.sequence.isEmpty()) return BattleConfig.ACT_ATTACK;
			return cfg.sequence.get(Math.min(phase, cfg.sequence.size() - 1)).action;
		}

		int alive() {
			return bots.size();
		}
	}

	/** Position und Ausrichtung einer gebauten Arena (Teams stellen sich entlang der Achse auf). */
	public record ArenaInfo(Vec3 center, Vec3 axis, double halfLength, boolean round) {}

	static State state = State.IDLE;
	static BattleConfig cfg = BattleConfig.defaults();
	static final List<Side> sides = new ArrayList<>();
	static ServerLevel level;
	static Vec3 center = Vec3.ZERO;
	static ArenaInfo arena;
	static int countdown;
	static int tick;
	/** 0 = Episch, 1 = Vereinfacht, 2 = Sparmodus (bei sehr vielen Bots). */
	static int ai = 0;
	static int overTicks;
	static int statusIn;
	static Side winnerSide;

	// ------------------------------------------------------------------ Befehle aus dem Menü

	public static void handle(ServerPlayer player, String action, String data) {
		ServerLevel lvl = player.level();
		switch (action) {
			case "setup" -> setup(player, BattleConfig.decode(data));
			case "start" -> {
				if (state == State.IDLE || state == State.OVER || !data.isEmpty() && state != State.READY) setup(player, BattleConfig.decode(data));
				if (state == State.READY) startCountdown();
			}
			case "pause" -> togglePause();
			case "clear" -> {
				clear();
				msg(player, "Alle Bots entfernt.");
			}
			case "arena" -> {
				BattleConfig c = BattleConfig.decode(data);
				arena = ArenaBuilder.build(lvl, player, c.arena, c.arenaSize);
				msg(player, "Arena \"" + ArenaBuilder.NAMES[Math.floorMod(c.arena, ArenaBuilder.NAMES.length)] + "\" wird gebaut …");
			}
			case "forgetArena" -> {
				arena = null;
				msg(player, "Arena vergessen – Armeen stellen sich wieder vor dir auf.");
			}
			default -> {}
		}
		sendStatus(lvl.getServer());
	}

	static void msg(ServerPlayer p, String text) {
		p.sendSystemMessage(Component.literal("§6[Bot-Armee] §f" + text));
	}

	// ------------------------------------------------------------------ Aufstellen

	static void setup(ServerPlayer player, BattleConfig config) {
		clear();
		cfg = config;
		level = player.level();
		RandomSource random = level.getRandom();
		BotNames.reset();

		// Wo findet die Schlacht statt?
		Vec3 axis;
		double half = cfg.distance / 2.0;
		boolean round = false;
		if (cfg.useArena && arena != null) {
			center = arena.center();
			axis = arena.axis();
			half = Math.min(half, arena.halfLength() * 0.8);
			round = arena.round();
		} else {
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
			flat = flat.normalize();
			center = player.position().add(flat.scale(Math.max(18, half * 0.9)));
			axis = new Vec3(-flat.z, 0, flat.x); // quer vor dir: links gegen rechts
		}

		int n = cfg.teams.size();
		int total = 0;
		for (int i = 0; i < n; i++) {
			Side s = new Side(i, cfg.teams.get(i));
			double angle = n == 2 && !round ? (i == 0 ? Math.PI : 0) : Math.PI + i * (Math.PI * 2 / n);
			Vec3 dir = rotateY(axis, angle);
			s.start = center.add(dir.scale(half));
			s.anchor = s.start;
			s.forward = center.subtract(s.start).multiply(1, 0, 1).normalize();
			s.right = new Vec3(-s.forward.z, 0, s.forward.x);
			sides.add(s);
		}

		Scoreboard board = level.getScoreboard();
		for (Side s : sides) {
			String teamName = "botarmee_" + s.index;
			PlayerTeam old = board.getPlayerTeam(teamName);
			if (old != null) board.removePlayerTeam(old);
			s.team = board.addPlayerTeam(teamName);
			s.team.setColor(Optional.of(TeamColor.VALUES.get(BattleConfig.COLOR_TEAM[Math.floorMod(s.cfg.color, BattleConfig.COLOR_TEAM.length)])));
			s.team.setAllowFriendlyFire(false);

			double edge = (s.cfg.edge - 50) / 50.0; // -1 .. +1
			s.baseDamage = 1 + 0.3 * edge;
			s.damageMult = s.baseDamage;
			s.takenMult = 1 - 0.15 * edge;
			s.speed = 0.3 * (1 + 0.06 * edge) * (cfg.speed / 100.0);

			int count = Math.min(s.cfg.count, BattleConfig.MAX_TOTAL - total);
			int archers = (int) Math.round(count * s.cfg.archers / 100.0);
			for (int i = 0; i < count; i++) {
				boolean archer = i >= count - archers;
				BotEntity bot = spawnBot(s, i, archer, random, edge);
				if (bot != null) s.bots.add(bot);
			}
			s.startCount = s.bots.size();
			total += s.startCount;
		}

		state = State.READY;
		tick = 0;
		winnerSide = null;
		ai = BattleConfig.aiLevel(total);
		msg(player, total + " Bots aufgestellt. Drück \"Los!\" im Menü (Taste B), um die Schlacht zu starten.");
		if (ai > 0) {
			msg(player, "§6⚠ KI-Modus: " + BattleConfig.AI_LEVELS[ai] + "§f – bei so vielen Bots kämpfen sie einfacher, damit es nicht laggt.");
		}
	}

	private static BotEntity spawnBot(Side s, int slot, boolean archer, RandomSource random, double edge) {
		BotEntity bot = new BotEntity(BotArmee.BOT, level);
		bot.side = s;
		bot.slot = slot;
		bot.archer = archer;
		bot.flanker = slot % 2 == 1;
		bot.setTeamIndex(s.index);
		bot.setSkinSeed(random.nextInt(1 << 30));
		bot.retargetIn = random.nextInt(10);

		Vec3 p = slotPos(s, slot, s.anchor);
		float yaw = (float) (Mth.atan2(s.forward.z, s.forward.x) * Mth.RAD_TO_DEG) - 90.0F;
		bot.snapTo(p.x, p.y, p.z, yaw, 0);
		bot.setYHeadRot(yaw);
		bot.setYBodyRot(yaw);

		String name = BotNames.next(random);
		bot.setCustomName(Component.literal(name));
		bot.setCustomNameVisible(cfg.names);
		bot.setGlowingTag(cfg.glow);

		int dye = cfg.dyeLeather ? BattleConfig.COLOR_DYE[Math.floorMod(s.cfg.color, BattleConfig.COLOR_DYE.length)] : -1;
		for (int i = 0; i < 6; i++) {
			BattleConfig.Piece piece = s.cfg.kit[i];
			if (archer && i == 4) {
				piece = new BattleConfig.Piece(s.cfg.ranged == 1 ? "minecraft:crossbow" : "minecraft:bow");
			}
			if (archer && i == 5 && piece.item.contains("shield")) piece = new BattleConfig.Piece();
			int down = i < 4 ? Kits.rollDowngrade(random, s.cfg.variation) : 0;
			ItemStack stack = Kits.build(level.registryAccess(), piece, i, dye, down);
			bot.setItemSlot(Kits.SLOTS[i], stack);
		}

		double hp = 20 * (1 + 0.25 * edge);
		bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		bot.setHealth((float) hp);
		bot.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(s.speed * (0.92 + random.nextDouble() * 0.16));

		if (!level.addFreshEntity(bot)) return null;
		level.getScoreboard().addPlayerToTeam(bot.getScoreboardName(), s.team);
		return bot;
	}

	/** Platz eines Bots in der Formation (vorne Nahkämpfer, hinten Bogenschützen). */
	static Vec3 slotPos(Side s, int slot, Vec3 anchor) {
		int n = Math.max(1, s.startCount > 0 ? s.startCount : s.cfg.count);
		double x, back;
		switch (s.cfg.formation) {
			case 1 -> { // breite Linie, 2-3 Reihen
				int cols = Math.max(1, (int) Math.ceil(n / 2.5));
				x = (slot % cols - (cols - 1) / 2.0) * 1.5;
				back = (slot / cols) * 1.8;
			}
			case 2 -> { // Keil: Spitze vorne
				int row = 0, start = 0;
				while (start + (2 * row + 1) <= slot) {
					start += 2 * row + 1;
					row++;
				}
				int inRow = slot - start;
				x = (inRow - row) * 1.5;
				back = row * 1.6;
			}
			case 3 -> { // locker
				int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * 1.4)));
				double jx = ((slot * 7919) % 100) / 100.0 - 0.5;
				double jz = ((slot * 104729) % 100) / 100.0 - 0.5;
				x = (slot % cols - (cols - 1) / 2.0) * 2.6 + jx * 1.4;
				back = (slot / cols) * 2.6 + jz * 1.4;
			}
			default -> { // Block
				int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * 1.6)));
				x = (slot % cols - (cols - 1) / 2.0) * 1.6;
				back = (slot / cols) * 1.6;
			}
		}
		Vec3 p = anchor.add(s.right.scale(x)).subtract(s.forward.scale(back));
		int y = level == null ? (int) p.y : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
		return new Vec3(p.x, y, p.z);
	}

	// ------------------------------------------------------------------ Steuerung

	static void startCountdown() {
		if (state != State.READY) return;
		if (cfg.countdown) {
			state = State.COUNTDOWN;
			countdown = 3 * 20 + 1;
		} else {
			beginFight();
		}
	}

	static void beginFight() {
		state = State.FIGHT;
		tick = 0;
		for (Side s : sides) {
			s.phase = 0;
			s.phaseTicks = 0;
		}
		title("§c§lKAMPF!", "", 0, 20, 10);
	}

	static void togglePause() {
		if (state == State.FIGHT || state == State.PAUSED) {
			boolean pause = state == State.FIGHT;
			state = pause ? State.PAUSED : State.FIGHT;
			for (Side s : sides) {
				for (BotEntity b : s.bots) {
					b.setNoAi(pause);
					if (pause) b.getNavigation().stop();
				}
			}
		}
	}

	public static void clear() {
		for (Side s : sides) {
			for (BotEntity b : s.bots) b.discard();
			if (s.team != null && level != null) {
				Scoreboard board = level.getScoreboard();
				if (board.getPlayerTeam(s.team.getName()) != null) board.removePlayerTeam(s.team);
			}
		}
		sides.clear();
		state = State.IDLE;
		winnerSide = null;
	}

	public static void onServerStopping() {
		sides.clear();
		state = State.IDLE;
		level = null;
		arena = null;
	}

	// ------------------------------------------------------------------ Jeder Server-Tick

	public static void tickServer(MinecraftServer server) {
		ArenaBuilder.tick();
		if (level == null || sides.isEmpty()) {
			if (--statusIn <= 0) {
				statusIn = 20;
				sendStatus(server);
			}
			return;
		}

		for (Side s : sides) s.bots.removeIf(b -> b.isRemoved() || !b.isAlive());

		switch (state) {
			case COUNTDOWN -> {
				countdown--;
				if (countdown % 20 == 0 && countdown > 0) {
					int sec = countdown / 20;
					String c = sec == 3 ? "§c" : sec == 2 ? "§6" : "§e";
					title(c + "§l" + sec, "§7Macht euch bereit …", 0, 18, 2);
				}
				if (countdown <= 0) beginFight();
			}
			case FIGHT -> {
				tick++;
				for (Side s : sides) advancePhase(s);
				if (tick % 10 == 0) rig();
				checkVictory();
			}
			case OVER -> overTicks++;
			default -> {}
		}

		if (--statusIn <= 0) {
			statusIn = 10;
			sendStatus(server);
		}
	}

	private static void advancePhase(Side s) {
		s.phaseTicks++;
		if (s.phase < s.cfg.sequence.size()) {
			int secs = s.cfg.sequence.get(s.phase).seconds;
			if (secs > 0 && s.phaseTicks >= secs * 20 && s.phase < s.cfg.sequence.size() - 1) {
				s.phase++;
				s.phaseTicks = 0;
				for (BotEntity b : s.bots) b.repathIn = 0;
			}
		}
		int a = s.action();
		if (a == BattleConfig.ACT_MARCH) {
			Vec3 toCenter = center.subtract(s.anchor).multiply(1, 0, 1);
			if (toCenter.length() > 6) s.anchor = s.anchor.add(s.forward.scale(0.06 * cfg.speed / 100.0));
		} else if (a == BattleConfig.ACT_RETREAT) {
			s.anchor = s.start;
		}
	}

	private static void checkVictory() {
		Side last = null;
		int aliveSides = 0;
		for (Side s : sides) {
			if (s.alive() > 0) {
				aliveSides++;
				last = s;
			}
		}
		if (aliveSides <= 1) {
			state = State.OVER;
			overTicks = 0;
			winnerSide = last;
			if (cfg.winnerTitle) {
				if (last == null) {
					title("§7Unentschieden!", "", 5, 70, 20);
				} else {
					title(colorCode(last.cfg.color) + "§l" + last.cfg.name.toUpperCase() + " GEWINNT!",
							"§7" + last.alive() + " von " + last.startCount + " überlebt", 5, 80, 20);
				}
			}
			for (Side s : sides) for (BotEntity b : s.bots) b.getNavigation().stop();
		}
	}

	/**
	 * Rigging: wenn ein Sieger eingestellt ist, wird sein Team unauffällig stärker gemacht,
	 * sobald es zurückfällt – so wirkt die Schlacht knapp, aber das Team gewinnt.
	 */
	private static void rig() {
		if (cfg.winner < 0 || cfg.winner >= sides.size()) return;
		Side w = sides.get(cfg.winner);
		if (w.startCount == 0) return;
		double ws = w.alive() / (double) w.startCount;
		double best = 0;
		int enemiesAlive = 0, totalStart = w.startCount, totalAlive = w.alive();
		for (Side s : sides) {
			if (s == w || s.startCount == 0) continue;
			best = Math.max(best, s.alive() / (double) s.startCount);
			enemiesAlive += s.alive();
			totalStart += s.startCount;
			totalAlive += s.alive();
		}
		// Fortschritt der Schlacht: 0 = alle leben, 1 = fast alle tot.
		double progress = 1 - totalAlive / (double) Math.max(1, totalStart);
		// Wie weit der Sieger vorne liegen soll – am Anfang gar nicht, zum Ende hin immer mehr.
		double lead = (0.30 * (1 - cfg.closeness / 100.0) + 0.04) * progress;
		double target = best + lead;
		boolean endgame = w.alive() <= 3 && enemiesAlive >= w.alive();
		double deficit = target - ws;
		if (endgame) {
			w.damageMult = Math.min(w.baseDamage * 5.0, w.damageMult * 1.3);
			w.takenMult = Math.max(0.15, w.takenMult * 0.75);
		} else if (deficit > 0.01) {
			// je weiter hinten, desto stärker nachhelfen
			w.damageMult = Math.min(w.baseDamage * 4.0, w.damageMult * (1 + Math.min(0.3, deficit * 2.0)));
			w.takenMult = Math.max(0.2, w.takenMult * (1 - Math.min(0.22, deficit * 1.6)));
		} else if (deficit < -0.06) {
			// zu weit vorne: wieder normal (bei "knapp" sogar etwas schwächer), damit es spannend bleibt
			double floor = w.baseDamage * (1 - 0.35 * cfg.closeness / 100.0);
			w.damageMult = Math.max(floor, w.damageMult * 0.93);
			w.takenMult = Math.min(1 + 0.3 * cfg.closeness / 100.0, w.takenMult * 1.05);
		}
	}

	public static float modifyDamage(BotEntity attacker, BotEntity victim, float damage) {
		double d = damage;
		if (attacker.side != null) d *= attacker.side.damageMult;
		if (victim.side != null) d *= victim.side.takenMult;
		return (float) d;
	}

	// ------------------------------------------------------------------ KI pro Bot

	public static void tickBot(BotEntity bot, ServerLevel lvl) {
		Side s = bot.side;
		if (s == null || !sides.contains(s)) {
			bot.discard();
			return;
		}
		if (bot.attackCooldown > 0) bot.attackCooldown--;

		switch (state) {
			case READY, COUNTDOWN -> holdSlot(bot, s, 0.8, true);
			case FIGHT -> fight(bot, s, lvl);
			case OVER -> celebrate(bot, s);
			default -> {}
		}
	}

	private static void celebrate(BotEntity bot, Side s) {
		bot.getNavigation().stop();
		if (s == winnerSide && bot.onGround() && bot.getRandom().nextInt(25) == 0) {
			bot.getJumpControl().jump();
			bot.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		}
	}

	private static void fight(BotEntity bot, Side s, ServerLevel lvl) {
		if (--bot.retargetIn <= 0) {
			bot.retargetIn = ai == 0 ? 8 + bot.getRandom().nextInt(6) : ai == 1 ? 18 + bot.getRandom().nextInt(12) : 35 + bot.getRandom().nextInt(25);
			LivingEntity old = bot.enemy;
			// im Sparmodus am alten Gegner dranbleiben, solange er nah ist
			if (ai > 0 && old != null && old.isAlive() && old.distanceToSqr(bot) < 36) {
				// bleibt
			} else {
				bot.enemy = nearestEnemy(bot, s);
			}
		}
		LivingEntity enemy = bot.enemy;
		if (enemy != null && (!enemy.isAlive() || enemy.isRemoved())) {
			enemy = bot.enemy = nearestEnemy(bot, s);
		}
		int action = s.action();
		double dist = enemy == null ? 9999 : bot.distanceTo(enemy);

		if (action == BattleConfig.ACT_RETREAT) {
			holdSlot(bot, s, 1.15, false);
			if (enemy != null && dist < 2.5) melee(bot, enemy, lvl, dist, s);
			return;
		}

		boolean engaged = enemy != null && dist < (bot.archer ? 3.5 : 5.0);
		if (bot.archer) {
			boolean mayShoot = action == BattleConfig.ACT_ATTACK || action == BattleConfig.ACT_VOLLEY || action == BattleConfig.ACT_FLANK;
			if (enemy != null && mayShoot && dist < 48) {
				ranged(bot, enemy, lvl, dist, action == BattleConfig.ACT_ATTACK || action == BattleConfig.ACT_FLANK);
			} else if (engaged) {
				melee(bot, enemy, lvl, dist, s);
			} else {
				stopDrawing(bot);
				holdSlot(bot, s, action == BattleConfig.ACT_MARCH ? 0.75 : 0.9, false);
			}
			return;
		}

		switch (action) {
			case BattleConfig.ACT_ATTACK -> {
				if (enemy != null) melee(bot, enemy, lvl, dist, s);
			}
			case BattleConfig.ACT_FLANK -> {
				if (enemy == null) return;
				if (ai < 2 && bot.flanker && dist > 10 && !reachedFlank(bot, s)) {
					Vec3 wp = flankPoint(bot, s);
					moveTo(bot, wp, 1.2);
				} else {
					melee(bot, enemy, lvl, dist, s);
				}
			}
			default -> {
				if (engaged) melee(bot, enemy, lvl, dist, s);
				else holdSlot(bot, s, action == BattleConfig.ACT_MARCH ? 0.75 : 0.9, false);
			}
		}
	}

	private static boolean reachedFlank(BotEntity bot, Side s) {
		return bot.position().distanceToSqr(flankPoint(bot, s)) < 36;
	}

	private static Vec3 flankPoint(BotEntity bot, Side s) {
		double sign = (bot.slot / 2) % 2 == 0 ? 1 : -1;
		double width = Math.max(12, cfg.distance * 0.45);
		return center.add(s.right.scale(sign * width)).subtract(s.forward.scale(4));
	}

	private static LivingEntity nearestEnemy(BotEntity bot, Side own) {
		BotEntity best = null;
		double bestD = Double.MAX_VALUE;
		int budget = ai == 0 ? Integer.MAX_VALUE : ai == 1 ? 60 : 24;
		for (Side s : sides) {
			if (s == own || s.bots.isEmpty()) continue;
			int n = s.bots.size();
			int checks = Math.min(n, budget);
			// bei vielen Bots nur eine Stichprobe anschauen statt alle
			int step = Math.max(1, n / checks);
			int start = bot.getRandom().nextInt(n);
			for (int k = 0; k < checks; k++) {
				BotEntity e = s.bots.get((start + k * step) % n);
				if (!e.isAlive()) continue;
				double d = e.distanceToSqr(bot);
				// etwas Zufall, damit nicht alle denselben Gegner jagen
				d *= 0.85 + ((e.getId() * 31 + bot.getId() * 17) % 30) / 100.0;
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
		}
		return best;
	}

	private static void holdSlot(BotEntity bot, Side s, double speed, boolean faceForward) {
		Vec3 target = slotPos(s, bot.slot, s.anchor);
		double d2 = bot.position().distanceToSqr(target.x, bot.getY(), target.z);
		if (d2 > 1.4) {
			moveTo(bot, target, speed);
		} else {
			bot.getNavigation().stop();
			LivingEntity look = state == State.FIGHT ? bot.enemy : null;
			if (look != null && !faceForward) {
				bot.getLookControl().setLookAt(look, 30, 30);
			} else {
				Vec3 f = bot.getEyePosition().add(s.forward.scale(10));
				bot.getLookControl().setLookAt(f.x, f.y, f.z);
			}
			if (state == State.READY && bot.getRandom().nextInt(400) == 0) {
				bot.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false); // ungeduldig
			}
		}
	}

	private static void moveTo(BotEntity bot, Vec3 target, double speed) {
		if (ai == 2) {
			// Sparmodus: ohne Wegsuche einfach geradeaus laufen
			bot.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
			return;
		}
		if (--bot.repathIn <= 0 || bot.getNavigation().isDone()) {
			bot.repathIn = (ai == 0 ? 10 : 22) + bot.getRandom().nextInt(8);
			bot.getNavigation().moveTo(target.x, target.y, target.z, speed);
		}
	}

	private static void melee(BotEntity bot, LivingEntity enemy, ServerLevel lvl, double dist, Side s) {
		stopDrawing(bot);
		bot.getLookControl().setLookAt(enemy, 30, 30);
		double aggr = cfg.aggression / 100.0;
		if (!bot.isWithinMeleeAttackRange(enemy)) {
			if (ai == 2) {
				bot.getMoveControl().setWantedPosition(enemy.getX(), enemy.getY(), enemy.getZ(), 1.25);
			} else if (--bot.repathIn <= 0 || bot.getNavigation().isDone()) {
				bot.repathIn = ai == 0 ? (dist < 8 ? 4 : 10) : (dist < 8 ? 8 : 20);
				bot.getNavigation().moveTo(enemy, 1.25);
			}
			return;
		}
		// im Nahkampf: ein bisschen tänzeln
		bot.getNavigation().stop();
		if (ai == 0 && bot.getRandom().nextInt(4) == 0) {
			bot.getMoveControl().strafe(bot.getRandom().nextFloat() * 0.6F - 0.1F, bot.getRandom().nextFloat() * 1.2F - 0.6F);
		}
		if (bot.attackCooldown <= 0 && bot.hasLineOfSight(enemy)) {
			bot.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			bot.doHurtTarget(lvl, enemy);
			int base = 10 + bot.getRandom().nextInt(9);
			bot.attackCooldown = Math.max(5, (int) Math.round(base / aggr));
			if (ai == 0 && bot.onGround() && bot.getRandom().nextInt(4) == 0) bot.getJumpControl().jump(); // Crit-Sprung
		}
	}

	private static void ranged(BotEntity bot, LivingEntity enemy, ServerLevel lvl, double dist, boolean reposition) {
		bot.getLookControl().setLookAt(enemy, 30, 30);
		boolean crossbow = bot.getMainHandItem().is(Items.CROSSBOW);
		if (bot.drawTicks > 0) {
			bot.getNavigation().stop();
			if (--bot.drawTicks == 0) {
				shoot(bot, enemy, lvl, crossbow);
				bot.stopUsingItem();
				double aggr = cfg.aggression / 100.0;
				bot.attackCooldown = Math.max(8, (int) Math.round((18 + bot.getRandom().nextInt(16)) / aggr));
			}
			return;
		}
		if (reposition && ai < 2) {
			if (ai == 0 && dist < 8) {
				Vec3 away = bot.position().subtract(enemy.position()).multiply(1, 0, 1).normalize().scale(6).add(bot.position());
				moveTo(bot, away, 1.1);
			} else if (dist > 22) {
				moveTo(bot, enemy.position(), 1.0);
			} else {
				bot.getNavigation().stop();
			}
		}
		if (bot.attackCooldown <= 0 && bot.hasLineOfSight(enemy)) {
			bot.startUsingItem(InteractionHand.MAIN_HAND);
			bot.drawTicks = crossbow ? 26 : 18 + bot.getRandom().nextInt(6);
		}
	}

	private static void stopDrawing(BotEntity bot) {
		if (bot.drawTicks > 0) {
			bot.drawTicks = 0;
			bot.stopUsingItem();
		}
	}

	private static void shoot(BotEntity bot, LivingEntity target, ServerLevel lvl, boolean crossbow) {
		ItemStack weapon = bot.getMainHandItem();
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(bot, ammo, 1.0F, weapon);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double xd = target.getX() - bot.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - bot.getZ();
		double horiz = Math.sqrt(xd * xd + zd * zd);
		float spread = (float) Math.max(1.5, 7 - cfg.aggression / 40.0);
		Projectile.spawnProjectileUsingShoot(arrow, lvl, ammo, xd, yd + horiz * 0.2, zd, crossbow ? 2.2F : 1.7F, spread);
		bot.playSound(crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (bot.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	// ------------------------------------------------------------------ Anzeige

	static String colorCode(int color) {
		String[] codes = {"§c", "§9", "§a", "§e", "§d", "§b", "§6", "§f"};
		return codes[Math.floorMod(color, codes.length)];
	}

	private static void title(String title, String subtitle, int in, int stay, int out) {
		if (level == null) return;
		for (ServerPlayer p : level.players()) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
		}
	}

	/** Schickt den Spielstand (Bots pro Team) an alle Spieler für die Anzeige oben am Bildschirm. */
	static void sendStatus(MinecraftServer server) {
		StringBuilder sb = new StringBuilder();
		sb.append(state.name()).append(';').append(arena != null ? 1 : 0);
		for (Side s : sides) {
			sb.append(';').append(s.cfg.name.replace(";", " ").replace(",", " ")).append(',')
					.append(s.cfg.color).append(',').append(s.alive()).append(',').append(s.startCount);
		}
		sb.append(';').append(cfg.hud ? 1 : 0);
		Net.StatusPayload payload = new Net.StatusPayload(sb.toString());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(p, Net.StatusPayload.TYPE)) ServerPlayNetworking.send(p, payload);
		}
	}

	private static Vec3 rotateY(Vec3 v, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
	}
}
