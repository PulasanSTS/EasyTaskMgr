package com.easytaskmgr;

import com.easytaskmgr.net.ShareActionC2S;
import com.easytaskmgr.net.ShareProgressC2S;
import com.easytaskmgr.net.ShareSnapshotS2C;
import com.easytaskmgr.share.SharedTasks;
import com.easytaskmgr.task.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 入口（Minecraft 1.21.1）。
 *
 * <p>Fabric 版把网络、生命周期、tick 事件都写在这个类里；NeoForge 版改用事件订阅类
 * （{@link NeoPayloads}、{@link NeoServerEvents}），这里只保留常量与包处理函数。
 */
@Mod(EasyTaskMgrNeo.MODID)
public class EasyTaskMgrNeo {
	public static final String MODID = "easytaskmgr";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public EasyTaskMgrNeo(IEventBus modBus, ModContainer container) {
		LOGGER.info("EasyTaskMgr 已加载（Minecraft 1.21.1 / NeoForge）");
	}

	/** 处理客户端发来的共享任务操作。 */
	public static void handleShareAction(ServerPlayer player, ShareActionC2S payload) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		SharedTasks.markModded(player);
		server.execute(() -> {
			switch (payload.action()) {
				case "publish" -> {
					Task task = null;
					try {
						task = GSON.fromJson(payload.json(), Task.class);
					} catch (Exception e) {
						LOGGER.warn("解析共享任务失败", e);
					}
					if (task != null) {
						task.normalize();
						SharedTasks.publish(server, player, task);
					}
				}
				case "claim" -> SharedTasks.claim(server, player, payload.id());
				case "invite" -> SharedTasks.invite(player, payload.id(), payload.target());
				case "complete" -> SharedTasks.complete(server, player, payload.id());
				case "revoke" -> SharedTasks.revoke(player, payload.id());
				case "sync" -> PacketDistributor.sendToPlayer(player, new ShareSnapshotS2C(SharedTasks.snapshot(player)));
				default -> {
				}
			}
			SharedTasks.saveIfDirty(server);
			SharedTasks.broadcast(server);
		});
	}

	/** 共享收集任务「共同完成」：客户端上报背包里见过的数量。 */
	public static void handleProgress(ServerPlayer player, ShareProgressC2S payload) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		SharedTasks.markModded(player);
		server.execute(() -> {
			SharedTasks.progress(server, player, payload.id(), payload.count());
			SharedTasks.saveIfDirty(server);
			SharedTasks.broadcast(server);
		});
	}
}
