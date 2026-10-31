package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.net.ShareActionC2S;
import com.easytaskmgr.net.ShareSnapshotS2C;
import com.easytaskmgr.task.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端共享任务缓存：保存服务端下发的快照，并提供发布 / 认领 / 完成操作。
 */
public final class ClientShared {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public record Entry(String id, String owner, Task task, boolean done, int visibility,
			boolean claimed, int claims, boolean mine, boolean canClaim) {
	}

	private static List<Entry> entries = List.of();

	private ClientShared() {
	}

	public static List<Entry> entries() {
		return entries;
	}

	public static Entry find(String id) {
		for (Entry entry : entries) {
			if (entry.id().equals(id)) {
				return entry;
			}
		}
		return null;
	}

	public static boolean available() {
		return ClientPlayNetworking.canSend(ShareActionC2S.TYPE);
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ShareSnapshotS2C.TYPE, (payload, context) ->
				context.client().execute(() -> receive(payload.json())));
	}

	private static void receive(String json) {
		List<Entry> parsed = new ArrayList<>();
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			for (var element : root.getAsJsonArray("tasks")) {
				JsonObject o = element.getAsJsonObject();
				Task task = GSON.fromJson(o.get("json").getAsString(), Task.class);
				if (task == null) {
					continue;
				}
				task.normalize();
				task.remote = true;
				boolean done = o.get("done").getAsBoolean();
				task.done = done;
				parsed.add(new Entry(o.get("id").getAsString(), o.get("owner").getAsString(), task, done,
						o.get("visibility").getAsInt(), o.get("claimed").getAsBoolean(),
						o.get("claims").getAsInt(), o.get("mine").getAsBoolean(), o.get("canClaim").getAsBoolean()));
			}
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.warn("解析共享任务快照失败", e);
		}
		entries = parsed;
		EasyTaskMgr.LOGGER.info("收到共享任务快照：{} 条", parsed.size());
	}

	private static void send(String action, String id, String json, String target) {
		if (ClientPlayNetworking.canSend(ShareActionC2S.TYPE)) {
			ClientPlayNetworking.send(new ShareActionC2S(action, id, json, target));
		}
	}

	public static void publish(Task task) {
		EasyTaskMgr.LOGGER.info("发布共享任务 {}（visibility={}）", task.id, task.visibility);
		send("publish", task.id, GSON.toJson(task), "");
	}

	public static void claim(String id) {
		send("claim", id, "", "");
	}

	public static void complete(String id) {
		send("complete", id, "", "");
	}

	public static void invite(String id, String playerName) {
		send("invite", id, "", playerName);
	}

	public static void revoke(String id) {
		send("revoke", id, "", "");
	}
}
