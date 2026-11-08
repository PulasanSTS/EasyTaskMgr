package com.easytaskmgr.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：某个玩家刚刚达成了某个成就。
 *
 * <p>1.21.1 的 {@code ClientAdvancements} 不对外暴露进度，客户端没法自己判断成就是否完成，
 * 所以由服务端在 {@code AdvancementEvent.AdvancementEarnEvent} 里把 ids 发给对应玩家，
 * 客户端再把自己的成就类待办标记完成。
 */
public record AdvancementEarnedS2C(String id) implements CustomPacketPayload {
	public static final Type<AdvancementEarnedS2C> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("easytaskmgr", "adv_earned"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementEarnedS2C> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeUtf(value.id(), 256),
			buf -> new AdvancementEarnedS2C(buf.readUtf(256)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
