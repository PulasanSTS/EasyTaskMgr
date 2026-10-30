package com.easytaskmgr.net;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：上报自己「勾选了共享」的待办。
 *
 * <p>内容用一段 JSON 字符串承载（任务列表），这样就不用为每个字段手写编解码，
 * 也方便以后加字段。
 */
public record ShareTasksC2S(String json) implements CustomPacketPayload {
	public static final Type<ShareTasksC2S> TYPE = new Type<>(EasyTaskMgr.id("share_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShareTasksC2S> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeUtf(value.json(), 32767),
			buf -> new ShareTasksC2S(buf.readUtf(32767)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
