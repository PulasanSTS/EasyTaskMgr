package com.easytaskmgr;

import com.easytaskmgr.advancement.AdvancementDescriber;
import com.easytaskmgr.net.ShareActionC2S;
import com.easytaskmgr.net.ShareProgressC2S;
import com.easytaskmgr.net.ShareSnapshotS2C;
import com.easytaskmgr.share.ShareStore;
import com.easytaskmgr.share.SharedTasks;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.net.AdvancementDataS2C;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.net.AdvancementQueryC2S;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 公共入口：注册自定义网络包，并在服务端做「队友待办」的中转。
 *
 * <p>中转逻辑很薄：服务端只缓存每位玩家最后一次上报的共享列表，收到更新就广播给其他人；
 * 玩家进服时把已缓存的列表补发给他。这样联机时队友的待办能在 HUD 上看到，
 * 而不需要服务端装额外的数据库或权限插件。
 */
public class EasyTaskMgr implements ModInitializer {
	public static final String MOD_ID = "easytaskmgr";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(ShareActionC2S.TYPE, ShareActionC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShareProgressC2S.TYPE, ShareProgressC2S.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShareSnapshotS2C.TYPE, ShareSnapshotS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AdvancementQueryC2S.TYPE, AdvancementQueryC2S.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AdvancementDataS2C.TYPE, AdvancementDataS2C.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShareActionC2S.TYPE, (payload, context) -> {
			var player = context.player();
			var server = context.server();
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
					default -> {
					}
				}
				SharedTasks.saveIfDirty(server);
				SharedTasks.broadcast(server);
			});
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> ShareStore.load(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SharedTasks.flushPublishBatches(server);
			ShareStore.save(server);
		});
		// 发布提示的批处理（issue #21 第 2 点）：一次导入很多共享任务时合并成一条
		ServerTickEvents.END_SERVER_TICK.register(SharedTasks::tickPublishBatches);

		// 共享收集任务「共同完成」：客户端上报背包里见过的数量，服务端累加判定
		ServerPlayNetworking.registerGlobalReceiver(ShareProgressC2S.TYPE, (payload, context) -> {
			var player = context.player();
			var server = context.server();
			server.execute(() -> {
				SharedTasks.progress(server, player, payload.id(), payload.count());
				SharedTasks.saveIfDirty(server);
				SharedTasks.broadcast(server);
			});
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, ShareSnapshotS2C.TYPE)) {
				ServerPlayNetworking.send(handler.player, new ShareSnapshotS2C(SharedTasks.snapshot(handler.player)));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(AdvancementQueryC2S.TYPE, (payload, context) -> {
			var manager = context.server().getAdvancements();
			I18n.Lang lang = I18n.Lang.byCode(payload.lang());
			Map<String, Object> root = new LinkedHashMap<>();
			if (payload.id().isEmpty()) {
				List<Map<String, String>> entries = new ArrayList<>();
				for (var holder : manager.getAllAdvancements()) {
					if (!AdvancementDescriber.isVisible(holder)) {
						continue;
					}
					Map<String, String> entry = new LinkedHashMap<>();
					entry.put("id", holder.id().toString());
					entry.put("title", AdvancementDescriber.title(holder));
					entry.put("titleKey", AdvancementDescriber.titleKey(holder));
					entries.add(entry);
				}
				root.put("entries", entries);
			} else {
				Identifier id = Identifier.tryParse(payload.id());
				var holder = id == null ? null : manager.get(id);
				root.put("id", payload.id());
				if (holder == null) {
					root.put("title", payload.id());
					root.put("reqs", List.of(I18n.t(lang, "adv.notFound")));
				} else {
					root.put("id", holder.id().toString());
					root.put("title", AdvancementDescriber.title(holder));
					root.put("titleKey", AdvancementDescriber.titleKey(holder));
					root.put("desc", AdvancementDescriber.description(holder));
					root.put("descKey", AdvancementDescriber.descKey(holder));
					root.put("parentId", AdvancementDescriber.parentId(holder));
					root.put("reqs", AdvancementDescriber.requirements(holder, lang));
				}
			}
			ServerPlayNetworking.send(context.player(), new AdvancementDataS2C(GSON.toJson(root)));
		});

		LOGGER.info("EasyTaskMgr 公共入口已加载（Minecraft 26.3 / Fabric）");
	}
}
