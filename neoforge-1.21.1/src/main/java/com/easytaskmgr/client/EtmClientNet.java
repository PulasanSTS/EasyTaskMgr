package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgrNeo;
import com.easytaskmgr.net.AdvancementDataS2C;
import com.easytaskmgr.net.AdvancementEarnedS2C;
import com.easytaskmgr.net.AdvancementQueryC2S;
import com.easytaskmgr.net.ShareSnapshotS2C;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 客户端方向的网络包注册（只在客户端加载）。
 *
 * <p>服务端 → 客户端：共享任务快照、成就达成通知、成就数据；
 * 这些处理器直接改客户端状态，用 {@code enqueueWork} 保证在主线程执行。
 */
@EventBusSubscriber(modid = EasyTaskMgrNeo.MODID, bus = EventBusSubscriber.Bus.MOD,
		value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class EtmClientNet {
	private EtmClientNet() {
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToClient(ShareSnapshotS2C.TYPE, ShareSnapshotS2C.CODEC,
				(payload, context) -> context.enqueueWork(() -> ClientShared.handleSnapshot(payload.json())));
		registrar.playToClient(AdvancementEarnedS2C.TYPE, AdvancementEarnedS2C.CODEC,
				(payload, context) -> context.enqueueWork(() -> ClientTasks.completeAdvancement(payload.id())));
		registrar.playToClient(AdvancementDataS2C.TYPE, AdvancementDataS2C.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					// 成就候选列表（编辑器里的自动补全）暂未移植，先记录日志避免丢包
					EasyTaskMgrNeo.LOGGER.debug("收到成就数据：{} 字符", payload.json().length());
				}));
	}
}
