package com.easytaskmgr.share;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.net.ShareSnapshotS2C;
import com.easytaskmgr.task.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端共享任务表（内存态）。
 *
 * <p>三种可见性：个人（不上传）、共享（所有人可认领）、特殊共享（仅被邀请者可认领）。
 * 任务完成由「创建者或任一认领者」上报，服务端标记 done 后广播。
 */
public final class SharedTasks {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static final class Entry {
		public String id;
		public UUID creator;
		public String creatorName;
		public String json;
		public int visibility;
		public final Set<String> invited = new LinkedHashSet<>();
		public final Set<UUID> claimed = new LinkedHashSet<>();
		public boolean done;

		boolean visibleTo(ServerPlayer player) {
			String name = player.getName().getString();
			return visibility == 1
					|| creator.equals(player.getUUID())
					|| invited.contains(name)
					|| claimed.contains(player.getUUID());
		}
	}

	private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

	private SharedTasks() {
	}

	public static void publish(ServerPlayer player, Task task) {
		if (task == null || task.visibility == 0) {
			return;
		}
		Entry entry = new Entry();
		entry.id = task.id;
		entry.creator = player.getUUID();
		entry.creatorName = player.getName().getString();
		task.done = false;
		entry.json = GSON.toJson(task);
		entry.visibility = task.visibility == 2 ? 2 : 1;
		Entry old = BY_ID.get(entry.id);
		if (old != null) {
			entry.invited.addAll(old.invited);
			entry.claimed.addAll(old.claimed);
		}
		BY_ID.put(entry.id, entry);
		EasyTaskMgr.LOGGER.info("{} 发布共享任务 {}", entry.creatorName, entry.id);
	}

	public static void claim(ServerPlayer player, String id) {
		Entry entry = BY_ID.get(id);
		if (entry == null || entry.done || entry.claimed.contains(player.getUUID())) {
			return;
		}
		String name = player.getName().getString();
		if (entry.visibility == 2 && !entry.invited.contains(name) && !entry.creator.equals(player.getUUID())) {
			return;
		}
		entry.claimed.add(player.getUUID());
	}

	public static void invite(ServerPlayer player, String id, String target) {
		Entry entry = BY_ID.get(id);
		if (entry == null || target == null || target.isBlank() || !entry.creator.equals(player.getUUID())) {
			return;
		}
		entry.invited.add(target);
	}

	public static void complete(ServerPlayer player, String id) {
		Entry entry = BY_ID.get(id);
		if (entry == null || entry.done) {
			return;
		}
		if (!entry.creator.equals(player.getUUID()) && !entry.claimed.contains(player.getUUID())) {
			return;
		}
		entry.done = true;
	}

	public static void revoke(ServerPlayer player, String id) {
		Entry entry = BY_ID.get(id);
		if (entry != null && entry.creator.equals(player.getUUID())) {
			BY_ID.remove(id);
		}
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
			o.addProperty("mine", entry.creator.equals(player.getUUID()));
			o.addProperty("canClaim", !entry.claimed.contains(player.getUUID())
					&& !entry.creator.equals(player.getUUID())
					&& (entry.visibility == 1 || entry.invited.contains(player.getName().getString())));
			arr.add(o);
		}
		root.add("tasks", arr);
		return GSON.toJson(root);
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, ShareSnapshotS2C.TYPE)) {
				ServerPlayNetworking.send(player, new ShareSnapshotS2C(snapshot(player)));
			}
		}
	}
}
