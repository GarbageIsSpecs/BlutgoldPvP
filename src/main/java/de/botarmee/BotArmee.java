package de.botarmee;

import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class BotArmee implements ModInitializer {
	public static final String MOD_ID = "botarmee";

	private static final ResourceKey<EntityType<?>> BOT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "bot"));
	public static final EntityType<BotEntity> BOT = Registry.register(BuiltInRegistries.ENTITY_TYPE, BOT_KEY,
			EntityType.Builder.<BotEntity>of(BotEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(16)
					.build(BOT_KEY));

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(BOT, BotEntity.createAttributes());

		PayloadTypeRegistry.serverboundPlay().register(Net.ActionPayload.TYPE, Net.ActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Net.StatusPayload.TYPE, Net.StatusPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Net.ActionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> Battle.handle(context.player(), payload.action(), payload.data())));

		ServerTickEvents.END_SERVER_TICK.register(Battle::tickServer);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> Battle.allowDamageToPlayer(entity, source));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Battle.onServerStopping());
		ServerLifecycleEvents.SERVER_STARTED.register(ArenaProtection::load);
		ServerTickEvents.END_SERVER_TICK.register(ArenaProtection::tick);
		// Arenen im Überlebensmodus nicht abbaubar (im Kreativmodus darfst du weiterbauen)
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> player.isCreative() || !ArenaProtection.isProtected(world, pos));

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("botarmee")
						.then(Commands.literal("clear").executes(ctx -> {
							Battle.clear();
							ctx.getSource().sendSystemMessage(Component.literal("§6[Bot-Armee] §fAlle Bots entfernt."));
							return 1;
						}))
						.then(Commands.literal("schutzaus").executes(ctx -> {
							int n = ArenaProtection.clearAll();
							ctx.getSource().sendSystemMessage(Component.literal("§6[Bot-Armee] §fSchutz für " + n + " Arena(en) entfernt."));
							return 1;
						}))
						.then(Commands.literal("pause").executes(ctx -> {
							Battle.togglePause();
							return 1;
						}))
		));
	}
}
