package de.botarmee;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Nachrichten zwischen Menü (Client) und Schlacht (Server). */
public final class Net {
	private Net() {}

	/** Client → Server: eine Aktion ("setup", "start", "pause", "clear", "arena") plus Einstellungen als Text. */
	public record ActionPayload(String action, String data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BotArmee.MOD_ID, "action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), ActionPayload::action,
				ByteBufCodecs.stringUtf8(200000), ActionPayload::data,
				ActionPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server → Client: Zustand der Schlacht und wie viele pro Team noch leben. */
	public record StatusPayload(String data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<StatusPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BotArmee.MOD_ID, "status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StatusPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(8000), StatusPayload::data,
				StatusPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
