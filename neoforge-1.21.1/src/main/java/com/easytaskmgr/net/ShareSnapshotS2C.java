package com.easytaskmgr.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端的共享任务快照。 */
public record ShareSnapshotS2C(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShareSnapshotS2C> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("easytaskmgr", "share_snapshot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShareSnapshotS2C> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeUtf(value.json() == null ? "" : value.json(), 262144),
			buf -> new ShareSnapshotS2C(buf.readUtf(262144)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
