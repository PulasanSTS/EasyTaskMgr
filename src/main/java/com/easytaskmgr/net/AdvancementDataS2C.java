package com.easytaskmgr.net;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：成就数据（JSON）。
 *
 * <p>列表响应：{@code {"entries":[{"id":"...","title":"..."}]}}
 * <p>详情响应：{@code {"id":"...","title":"...","parent":"...","desc":"...","reqs":["..."]}}
 */
public record AdvancementDataS2C(String json) implements CustomPacketPayload {
	public static final Type<AdvancementDataS2C> TYPE = new Type<>(EasyTaskMgr.id("adv_data"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementDataS2C> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeUtf(value.json(), 1_048_576),
			buf -> new AdvancementDataS2C(buf.readUtf(1_048_576)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
