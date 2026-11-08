package com.easytaskmgr.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端的共享任务操作。
 *
 * @param action publish / claim / complete / invite / revoke / sync
 * @param id     任务 id
 * @param json   任务 JSON（publish 时使用）
 * @param target 被邀请的玩家名（invite 时使用）
 */
public record ShareActionC2S(String action, String id, String json, String target) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShareActionC2S> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("easytaskmgr", "share_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShareActionC2S> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.action(), 16);
				buf.writeUtf(value.id() == null ? "" : value.id(), 64);
				buf.writeUtf(value.json() == null ? "" : value.json(), 32767);
				buf.writeUtf(value.target() == null ? "" : value.target(), 32);
			},
			buf -> new ShareActionC2S(buf.readUtf(16), buf.readUtf(64), buf.readUtf(32767), buf.readUtf(32)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
