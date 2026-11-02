package io.github.lookyman.chestmate.net;

import io.github.lookyman.chestmate.Chestmate;
import io.github.lookyman.chestmate.core.Operation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks the server to run an operation on the open menu. The {@code _v1} in the channel name is the
 * versioning: an incompatible change gets a new channel.
 */
public record OperationRequest(int containerId, Operation operation) implements CustomPacketPayload {
	public static final Type<OperationRequest> TYPE = new Type<>(Chestmate.id("operation_v1"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OperationRequest> STREAM_CODEC = StreamCodec.of(
		(buf, request) -> {
			ByteBufCodecs.VAR_INT.encode(buf, request.containerId);
			ByteBufCodecs.VAR_INT.encode(buf, request.operation.ordinal());
		},
		buf -> {
			int containerId = ByteBufCodecs.VAR_INT.decode(buf);
			int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
			Operation[] all = Operation.values();
			if (ordinal < 0 || ordinal >= all.length) {
				throw new IllegalArgumentException("Unknown Chestmate operation " + ordinal);
			}
			return new OperationRequest(containerId, all[ordinal]);
		});

	@Override
	public Type<OperationRequest> type() {
		return TYPE;
	}
}
