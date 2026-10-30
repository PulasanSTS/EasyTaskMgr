package com.easytaskmgr.net;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：通知「刚刚合成出了 N 个某物品」。
 *
 * <p>为什么需要它：shift 快速合成时，客户端本地菜单不一定会执行
 * {@code ResultSlot#onTake}，只靠客户端钩子会漏计。改成服务端权威计数，
 * 再推给客户端累加，普通合成和 shift 合成都能覆盖。
 */
public record CraftEventS2C(String itemId, int count) implements CustomPacketPayload {
	public static final Type<CraftEventS2C> TYPE = new Type<>(EasyTaskMgr.id("craft_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CraftEventS2C> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.itemId(), 256);
				buf.writeVarInt(value.count());
			},
			buf -> new CraftEventS2C(buf.readUtf(256), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
