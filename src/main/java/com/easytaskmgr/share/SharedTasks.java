package com.easytaskmgr.share;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.net.ShareSnapshotS2C;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端共享任务表。
 *
 * <p>三种可见性：个人（不上传）、共享（所有人可认领）、特殊共享（仅被邀请者可认领）。
 * 任务完成由「创建者或任一认领者」上报，服务端标记 done 后广播。
 *
 * <p>每次发布 / 认领 / 完成都会给「能看见这条任务」的玩家发一条聊天提示，
 * 文案用 {@link Component#translatable} 下发给客户端，由客户端按自己的语言解析。
 */
public final class SharedTasks {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static final class Entry {
		public String id;
		public UUID creator;
		public String creatorName;
		public String json;
		public int visibility;
		/** 任务标题（用户填写的文本），用于聊天提示。 */
		public String title = "";
		/** 标题为空时用于拼接提示的类型翻译键（如 {@code easytaskmgr.type.collect}），可为空。 */
		public String labelKey = "";
		public final Set<String> invited = new LinkedHashSet<>();
		public final Set<UUID> claimed = new LinkedHashSet<>();
		/** 全部认领者完成后才算完成（false = 任一认领者完成即可）。 */
		public boolean all;
		/** 已上报「自己这部分完成」的玩家。 */
		public final Set<UUID> completedBy = new LinkedHashSet<>();
		public boolean done;

		boolean invited(String name) {
			for (String value : invited) {
				if (value.equalsIgnoreCase(name)) {
					return true;
				}
			}
			return false;
		}

		boolean visibleTo(ServerPlayer player) {
			String name = player.getName().getString();
			return visibility == 1
					|| creator.equals(player.getUUID())
					|| invited(name)
					|| claimed.contains(player.getUUID());
		}

		/** 已完成自己那部分的认领者数量（「全部认领者」模式的进度）。 */
		int claimsDone() {
			int done = 0;
			for (UUID uuid : claimed) {
				if (completedBy.contains(uuid)) {
					done++;
				}
			}
			return done;
		}
	}

	static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

	/** 有未落盘的改动（见 {@link #saveIfDirty}）。 */
	private static boolean dirty;

	private SharedTasks() {
	}

	/** 发布任务；内容有变化时给相关玩家发提示。 */
	public static void publish(MinecraftServer server, ServerPlayer player, Task task) {
		if (task == null || task.visibility == 0) {
			return;
		}
		Entry entry = new Entry();
		entry.id = task.id;
		entry.creator = player.getUUID();
		entry.creatorName = player.getName().getString();
		entry.title = task.text == null ? "" : task.text.trim();
		entry.labelKey = entry.title.isBlank() ? typeKey(task.type) : "";
		task.done = false;
		entry.json = GSON.toJson(task);
		entry.visibility = task.visibility == 2 ? 2 : 1;
		entry.all = task.shareAll;
		Entry old = BY_ID.get(entry.id);
		if (old != null) {
			entry.invited.addAll(old.invited);
			entry.claimed.addAll(old.claimed);
			// 完成条件没变就保留已上报的进度，变了就清零重来
			if (old.all == entry.all) {
				entry.completedBy.addAll(old.completedBy);
			}
		}
		BY_ID.put(entry.id, entry);
		dirty = true;
		EasyTaskMgr.LOGGER.info("{} 发布共享任务 {}（{}，完成条件={}）", entry.creatorName, entry.id, entry.title,
				entry.all ? "全部认领者" : "任一认领者");
		if (old == null || old.visibility != entry.visibility || old.all != entry.all
				|| !Objects.equals(old.json, entry.json)) {
			announcePublish(server, entry);
		}
	}

	/** 认领任务；成功时给相关玩家发提示。 */
	public static void claim(MinecraftServer server, ServerPlayer player, String id) {
		Entry entry = BY_ID.get(id);
		if (entry == null || entry.done || entry.claimed.contains(player.getUUID())) {
			return;
		}
		String name = player.getName().getString();
		if (entry.visibility == 2 && !entry.invited(name) && !entry.creator.equals(player.getUUID())) {
			return;
		}
		entry.claimed.add(player.getUUID());
		dirty = true;
		EasyTaskMgr.LOGGER.info("{} 认领了 {} 发布的共享任务 {}", name, entry.creatorName, entry.id);
		announceClaim(server, entry, name);
	}

	public static void invite(ServerPlayer player, String id, String target) {
		Entry entry = BY_ID.get(id);
		if (entry == null || target == null || target.isBlank() || !entry.creator.equals(player.getUUID())) {
			return;
		}
		entry.invited.add(target);
		dirty = true;
	}

	/**
	 * 完成任务。
	 *
	 * <p>「任一认领者」模式：第一个人完成就整条完成；「全部认领者」模式：
	 * 每个人只上报自己那部分，所有人都完成后才整条完成。创建者亲自完成视为整条完成。
	 */
	public static void complete(MinecraftServer server, ServerPlayer player, String id) {
		Entry entry = BY_ID.get(id);
		if (entry == null || entry.done) {
			return;
		}
		String name = player.getName().getString();
		UUID uuid = player.getUUID();
		if (!entry.creator.equals(uuid) && !entry.claimed.contains(uuid)) {
			return;
		}
		if (entry.completedBy.contains(uuid)) {
			return;
		}
		entry.completedBy.add(uuid);
		dirty = true;
		boolean finished = entry.creator.equals(uuid)
				|| !entry.all
				|| entry.claimed.isEmpty()
				|| entry.completedBy.containsAll(entry.claimed);
		EasyTaskMgr.LOGGER.info("{} 完成共享任务 {}（{}/{}，{}）", name, entry.id,
				entry.claimsDone(), entry.claimed.size(), finished ? "整条完成" : "等待其他认领者");
		if (finished) {
			entry.done = true;
			announceComplete(server, entry, name);
		} else {
			announcePartial(server, entry, name);
		}
	}

	public static void revoke(ServerPlayer player, String id) {
		Entry entry = BY_ID.get(id);
		if (entry != null && entry.creator.equals(player.getUUID())) {
			BY_ID.remove(id);
			dirty = true;
		}
	}

	/** 有改动就立刻落盘（存档在 {@link ShareStore}）。 */
	public static void saveIfDirty(MinecraftServer server) {
		if (!dirty) {
			return;
		}
		dirty = false;
		ShareStore.save(server);
	}

	/** 发布提示：{@code xxx 发布了共享任务「yyy」}。 */
	private static void announcePublish(MinecraftServer server, Entry entry) {
		announce(server, entry, "easytaskmgr.share.publish", entry.creatorName, titleArg(entry));
	}

	/** 认领提示：{@code xxx 认领了 yyy 发布的「zzz」}。 */
	private static void announceClaim(MinecraftServer server, Entry entry, String playerName) {
		announce(server, entry, "easytaskmgr.share.claim", playerName, entry.creatorName, titleArg(entry));
	}

	/** 完成提示：{@code xxx 完成了 yyy 发布的「zzz」}。 */
	private static void announceComplete(MinecraftServer server, Entry entry, String playerName) {
		announce(server, entry, "easytaskmgr.share.complete", playerName, entry.creatorName, titleArg(entry));
	}

	/** 「全部认领者」模式下有人先完成自己的部分：{@code xxx 完成了 yyy 发布的「zzz」（1/2）}。 */
	private static void announcePartial(MinecraftServer server, Entry entry, String playerName) {
		announce(server, entry, "easytaskmgr.share.partial", playerName, entry.creatorName, titleArg(entry),
				entry.claimsDone(), entry.claimed.size());
	}

	private static void announce(MinecraftServer server, Entry entry, String key, Object... args) {
		MutableComponent message = Component.translatable(key, args);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!entry.visibleTo(player)) {
				continue;
			}
			if (!ServerPlayNetworking.canSend(player, ShareSnapshotS2C.TYPE)) {
				continue;
			}
			player.sendSystemMessage(message);
		}
	}

	/** 标题参数：用户写了文本就用文本，否则用类型翻译键交给客户端本地化。 */
	private static Object titleArg(Entry entry) {
		if (entry.labelKey == null || entry.labelKey.isBlank()) {
			return entry.title == null ? "" : entry.title;
		}
		return Component.translatable(entry.labelKey);
	}

	private static String typeKey(TaskType type) {
		return switch (type) {
			case COLLECT -> "easytaskmgr.type.collect";
			case ADVANCEMENT -> "easytaskmgr.type.advancement";
			case COORDINATE -> "easytaskmgr.type.coordinate";
			case TIMER -> "easytaskmgr.type.timer";
			case NORMAL -> "";
		};
	}

	public static String snapshot(ServerPlayer player) {
		JsonObject root = new JsonObject();
		JsonArray arr = new JsonArray();
		for (Entry entry : BY_ID.values()) {
			if (!entry.visibleTo(player)) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("id", entry.id);
			o.addProperty("owner", entry.creatorName);
			o.addProperty("json", entry.json);
			o.addProperty("done", entry.done);
			o.addProperty("visibility", entry.visibility);
			o.addProperty("claimed", entry.claimed.contains(player.getUUID()));
			o.addProperty("claims", entry.claimed.size());
			o.addProperty("all", entry.all);
			o.addProperty("doneByMe", entry.completedBy.contains(player.getUUID()));
			o.addProperty("claimsDone", entry.claimsDone());
			o.addProperty("mine", entry.creator.equals(player.getUUID()));
			o.addProperty("canClaim", !entry.claimed.contains(player.getUUID())
					&& !entry.creator.equals(player.getUUID())
					&& (entry.visibility == 1 || entry.invited(player.getName().getString())));
			arr.add(o);
		}
		root.add("tasks", arr);
		return GSON.toJson(root);
	}

	public static void broadcast(MinecraftServer server) {
		EasyTaskMgr.LOGGER.debug("广播共享任务：在线 {} 人", server.getPlayerList().getPlayers().size());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, ShareSnapshotS2C.TYPE)) {
				ServerPlayNetworking.send(player, new ShareSnapshotS2C(snapshot(player)));
			}
		}
	}
}
