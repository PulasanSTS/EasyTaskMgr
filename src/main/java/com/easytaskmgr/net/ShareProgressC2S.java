package com.easytaskmgr.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：上报「我背包里这个物品当前有多少个」。
 *
 * <p>只在共享收集任务的「共同完成」模式下使用：服务端为每个认领者记住见过的最大数量，
 * 把所有认领者的最大值相加，达到目标数量就整条完成。
 *
 * @param id    共享任务 id
 * @param count 本次看到的数量
 */
public record ShareProgressC2S(String id, int count) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShareProgressC2S> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("easytaskmgr", "share_progress"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShareProgressC2S> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.id() == null ? "" : value.id(), 64);
				buf.writeVarInt(Math.max(0, value.count()));
			},
			buf -> new ShareProgressC2S(buf.readUtf(64), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
