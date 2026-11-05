package de.botarmee;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Ein Fake-Spieler. Sieht aus wie ein Spieler (Skin + Name), denkt aber mit der Schlacht-KI aus {@link Battle}.
 */
public class BotEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> DATA_TEAM = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);

	// ---- nur auf dem Server benutzt
	Battle.Side side;
	int slot;
	boolean archer;
	boolean flanker;
	LivingEntity enemy;
	int retargetIn;
	int attackCooldown;
	int drawTicks;
	int repathIn;
	int idleFidget;
	// ---- menschlicheres Verhalten
	boolean hero;
	/** Platz auf einem Podest (nur Bogenschützen im Kolosseum). */
	net.minecraft.world.phys.Vec3 perch;
	float wobble;
	int phase;
	int hesitate;
	int blockTicks;
	int blockCooldown;
	int backoff;
	int strafeDir = 1;
	// ---- Held: Emotes (1 = ducken, 2 = drehen)
	int emote;
	int emoteTicks;
	float emoteYaw;

	public BotEntity(EntityType<? extends BotEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		for (EquipmentSlot s : EquipmentSlot.values()) this.setDropChance(s, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_TEAM, 0);
		entityData.define(DATA_SKIN, 0);
	}

	public int getTeamIndex() {
		return this.entityData.get(DATA_TEAM);
	}

	public void setTeamIndex(int team) {
		this.entityData.set(DATA_TEAM, team);
	}

	public int getSkinSeed() {
		return this.entityData.get(DATA_SKIN);
	}

	public void setSkinSeed(int seed) {
		this.entityData.set(DATA_SKIN, seed);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		Battle.tickBot(this, level);
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, float damage) {
		if (Battle.isPlayerOnSide(source.getEntity(), this.side)) return false; // du triffst dein eigenes Team nicht
		if (source.getEntity() instanceof BotEntity attacker) {
			if (attacker.side != null && attacker.side == this.side) return false; // kein Friendly Fire
			damage = Battle.modifyDamage(attacker, this, damage);
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void pushEntities() {
		// Gegenseitiges Schubsen ist bei hunderten Bots teuer – im Sparmodus seltener berechnen
		if (Battle.ai == 0 || (Battle.ai == 1 && this.tickCount % 2 == 0) || this.tickCount % 4 == 0) super.pushEntities();
	}

	@Override
	public int getMaxFallDistance() {
		// trauen sich auch mal von einer Mauer zu springen, statt oben festzustecken
		return Math.max(super.getMaxFallDistance(), 6);
	}

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		// Bots gehören zu einer laufenden Schlacht und werden nicht mit der Welt gespeichert.
		return false;
	}

	@Override
	protected boolean shouldDropLoot(final ServerLevel level) {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(final ServerLevel level) {
		return 0;
	}
}
