package com.easytaskmgr.net;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：请求成就数据。
 *
 * <p>{@code id} 为空表示「要全部成就列表」，否则表示「要这个成就的详情（前置 + 条件）」。
 * 26.3 的服务端只把玩家有进度的成就同步给客户端，所以候选列表和前置条件必须向服务端要。
 */
public record AdvancementQueryC2S(String id) implements CustomPacketPayload {
	public static final Type<AdvancementQueryC2S> TYPE = new Type<>(EasyTaskMgr.id("adv_query"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementQueryC2S> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeUtf(value.id(), 256),
			buf -> new AdvancementQueryC2S(buf.readUtf(256)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
