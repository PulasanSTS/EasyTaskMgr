package com.easytaskmgr.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：请求成就数据。
 *
 * <p>{@code id} 为空表示「要全部成就列表」，否则表示「要这个成就的详情（前置 + 条件）」。
 */
public record AdvancementQueryC2S(String id, String lang) implements CustomPacketPayload {
	public static final Type<AdvancementQueryC2S> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("easytaskmgr", "adv_query"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementQueryC2S> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.id(), 256);
				buf.writeUtf(value.lang() == null ? "" : value.lang(), 16);
			},
			buf -> new AdvancementQueryC2S(buf.readUtf(256), buf.readUtf(16)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
