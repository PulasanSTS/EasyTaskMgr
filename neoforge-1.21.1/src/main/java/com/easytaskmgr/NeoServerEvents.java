package com.easytaskmgr;

import com.easytaskmgr.command.EtmCommand;
import com.easytaskmgr.share.ShareStore;
import com.easytaskmgr.share.SharedTasks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务端事件：共享任务存档的读写、发布提示批处理、玩家退出清理、命令注册。
 */
@EventBusSubscriber(modid = EasyTaskMgrNeo.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NeoServerEvents {
	private NeoServerEvents() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		ShareStore.load(event.getServer());
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		SharedTasks.flushPublishBatches(event.getServer());
		ShareStore.save(event.getServer());
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		SharedTasks.tickPublishBatches(event.getServer());
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			SharedTasks.unmarkModded(player);
		}
	}

	/** 成就达成：只有装了模组的客户端才会收到这条通知（1.21.1 客户端自己拿不到成就进度）。 */
	@SubscribeEvent
	public static void onAdvancementEarned(net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && SharedTasks.canSend(player)) {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
					new com.easytaskmgr.net.AdvancementEarnedS2C(event.getAdvancement().id().toString()));
		}
	}
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		EtmCommand.register(event.getDispatcher());
	}
}
