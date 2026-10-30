package com.easytaskmgr.net;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：转发某位队友共享出来的待办。
 *
 * @param owner 队友名字（服务端填充，客户端不可伪造）
 * @param json  该队友的共享任务列表 JSON
 */
public record SharedTasksS2C(String owner, String json) implements CustomPacketPayload {
	public static final Type<SharedTasksS2C> TYPE = new Type<>(EasyTaskMgr.id("shared_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SharedTasksS2C> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.owner(), 64);
				buf.writeUtf(value.json(), 32767);
			},
			buf -> new SharedTasksS2C(buf.readUtf(64), buf.readUtf(32767)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
