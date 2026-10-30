package com.easytaskmgr;

import com.easytaskmgr.advancement.AdvancementDescriber;
import com.easytaskmgr.net.AdvancementDataS2C;
import com.easytaskmgr.net.AdvancementQueryC2S;
import com.easytaskmgr.net.CraftEventS2C;
import com.easytaskmgr.net.ShareTasksC2S;
import com.easytaskmgr.net.SharedTasksS2C;
import net.fabricmc.api.ModInitializer;
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

	/** 服务端缓存：玩家 UUID -> 该玩家共享的任务包。 */
	private static final Map<UUID, SharedTasksS2C> SHARED_CACHE = new ConcurrentHashMap<>();
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	/** 合成去重：玩家 -> 上一次发送的「物品 x 数量 @ tick」。 */
	private static final Map<UUID, String> LAST_CRAFT = new ConcurrentHashMap<>();

	/**
	 * 记录一次合成：同一 tick 内同一物品+数量只发一次，避免 onTake 和 quickMoveStack 双触发重复计数。
	 */
	public static void notifyCraft(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String key = itemId + "x" + stack.getCount() + "@" + player.level().getGameTime();
		String previous = LAST_CRAFT.put(player.getUUID(), key);
		if (key.equals(previous)) {
			return;
		}
		ServerPlayNetworking.send(player, new CraftEventS2C(itemId, stack.getCount()));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(ShareTasksC2S.TYPE, ShareTasksC2S.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SharedTasksS2C.TYPE, SharedTasksS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CraftEventS2C.TYPE, CraftEventS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AdvancementQueryC2S.TYPE, AdvancementQueryC2S.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AdvancementDataS2C.TYPE, AdvancementDataS2C.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShareTasksC2S.TYPE, (payload, context) -> {
			var player = context.player();
			String owner = player.getName().getString();
			SharedTasksS2C out = new SharedTasksS2C(owner, payload.json());
			SHARED_CACHE.put(player.getUUID(), out);
			LOGGER.info("收到 {} 的共享待办（{} 字节），转发给 {} 名队友", owner, payload.json().length(), context.server().getPlayerList().getPlayers().size() - 1);

			for (var other : context.server().getPlayerList().getPlayers()) {
				if (!other.getUUID().equals(player.getUUID())) {
					ServerPlayNetworking.send(other, out);
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			UUID joiner = handler.player.getUUID();
			for (Map.Entry<UUID, SharedTasksS2C> entry : SHARED_CACHE.entrySet()) {
				if (!entry.getKey().equals(joiner)) {
					ServerPlayNetworking.send(handler.player, entry.getValue());
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// 断线后广播一份空列表，让队友的 HUD 立刻清掉这个人的待办。
			UUID left = handler.player.getUUID();
			SharedTasksS2C cached = SHARED_CACHE.remove(left);
			if (cached == null) {
				return;
			}
			SharedTasksS2C empty = new SharedTasksS2C(cached.owner(), "[]");
			for (var other : server.getPlayerList().getPlayers()) {
				ServerPlayNetworking.send(other, empty);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(AdvancementQueryC2S.TYPE, (payload, context) -> {
			var manager = context.server().getAdvancements();
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
					root.put("reqs", List.of("未找到该成就"));
				} else {
					root.put("id", holder.id().toString());
					root.put("title", AdvancementDescriber.title(holder));
					root.put("titleKey", AdvancementDescriber.titleKey(holder));
					root.put("desc", AdvancementDescriber.description(holder));
					root.put("descKey", AdvancementDescriber.descKey(holder));
					root.put("parentId", AdvancementDescriber.parentId(holder));
					root.put("reqs", AdvancementDescriber.requirements(holder));
				}
			}
			ServerPlayNetworking.send(context.player(), new AdvancementDataS2C(GSON.toJson(root)));
		});

		LOGGER.info("EasyTaskMgr 公共入口已加载（Minecraft 26.3 / Fabric）");
	}
}
