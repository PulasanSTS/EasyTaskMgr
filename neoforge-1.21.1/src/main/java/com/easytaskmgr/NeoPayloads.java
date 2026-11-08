package com.easytaskmgr;

import com.easytaskmgr.net.ShareActionC2S;
import com.easytaskmgr.net.ShareProgressC2S;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册（NeoForge 1.21.1）。
 *
 * <p>服务端方向的包在这里注册；客户端方向（快照、成就数据）在 {@code NeoClientPayloads} 里注册，
 * 这样专用服务器不会加载任何客户端类。
 */
@EventBusSubscriber(modid = EasyTaskMgrNeo.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoPayloads {
	private NeoPayloads() {
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToServer(ShareActionC2S.TYPE, ShareActionC2S.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> EasyTaskMgrNeo.handleShareAction((net.minecraft.server.level.ServerPlayer) context.player(), payload)));
		registrar.playToServer(com.easytaskmgr.net.AdvancementQueryC2S.TYPE, com.easytaskmgr.net.AdvancementQueryC2S.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> EasyTaskMgrNeo.handleAdvancementQuery((net.minecraft.server.level.ServerPlayer) context.player(), payload)));		registrar.playToServer(ShareProgressC2S.TYPE, ShareProgressC2S.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> EasyTaskMgrNeo.handleProgress((net.minecraft.server.level.ServerPlayer) context.player(), payload)));
	}
}
