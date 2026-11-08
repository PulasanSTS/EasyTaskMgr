package com.easytaskmgr;

import com.easytaskmgr.net.AdvancementDataS2C;
import com.easytaskmgr.net.AdvancementEarnedS2C;
import com.easytaskmgr.net.AdvancementQueryC2S;
import com.easytaskmgr.net.ShareActionC2S;
import com.easytaskmgr.net.ShareProgressC2S;
import com.easytaskmgr.net.ShareSnapshotS2C;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册（NeoForge 1.21.1）。
 *
 * <p>注意：**客户端方向的通道也必须在公共代码里声明**。NeoForge 在握手时会校验
 * 「客户端要求的通道服务端是否认识」，只在客户端注册（{@code Dist.CLIENT} 的类里）
 * 会导致连接被踢：{@code This channel is missing on the server side}。
 *
 * <p>处理器里引用的 {@code ClientShared}/{@code ClientTasks} 只在客户端执行，服务端不会走到这些分支。
 */
@EventBusSubscriber(modid = EasyTaskMgrNeo.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoPayloads {
	private NeoPayloads() {
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");

		// 客户端 → 服务端
		registrar.playToServer(ShareActionC2S.TYPE, ShareActionC2S.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> EasyTaskMgrNeo.handleShareAction((ServerPlayer) context.player(), payload)));
		registrar.playToServer(ShareProgressC2S.TYPE, ShareProgressC2S.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> EasyTaskMgrNeo.handleProgress((ServerPlayer) context.player(), payload)));
		registrar.playToServer(AdvancementQueryC2S.TYPE, AdvancementQueryC2S.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> EasyTaskMgrNeo.handleAdvancementQuery((ServerPlayer) context.player(), payload)));

		// 服务端 → 客户端（通道两端都要声明；处理器只会在客户端执行）
		registrar.playToClient(ShareSnapshotS2C.TYPE, ShareSnapshotS2C.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> com.easytaskmgr.client.ClientShared.handleSnapshot(payload.json())));
		registrar.playToClient(AdvancementEarnedS2C.TYPE, AdvancementEarnedS2C.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> com.easytaskmgr.client.ClientTasks.completeAdvancement(payload.id())));
		registrar.playToClient(AdvancementDataS2C.TYPE, AdvancementDataS2C.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					// 成就候选改成读客户端成就树，这里保留通道避免旧请求报错
				}));
	}
}
