package com.easytaskmgr.client;

import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.net.ShareTasksC2S;
import com.easytaskmgr.net.SharedTasksS2C;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多人共享 / 服务端事件接收。
 */
public final class ClientNet {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Map<String, List<Task>> REMOTE = new LinkedHashMap<>();
	private static String lastSent = "";
	private static boolean dirty = true;
	private static Boolean serverHasMod;

	private ClientNet() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SharedTasksS2C.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			mc.execute(() -> receive(payload));
		});
	}

	/** 服务端是否装了本模组（装了就以服务端合成事件为准）。 */
	public static boolean serverHasMod() {
		if (serverHasMod == null) {
			try {
				serverHasMod = ClientPlayNetworking.canSend(SharedTasksS2C.TYPE);
			} catch (Exception e) {
				serverHasMod = false;
			}
		}
		return serverHasMod;
	}

	public static void reset() {
		REMOTE.clear();
		lastSent = "";
		dirty = true;
		serverHasMod = null;
	}

	public static void markDirty() {
		dirty = true;
	}

	/** 自己的任务有变化时才发送。 */
	public static void syncIfNeeded() {
		ModConfig cfg = ModConfig.get();
		if (!cfg.shareEnabled || Minecraft.getInstance().player == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(ShareTasksC2S.TYPE)) {
			return;
		}
		String json = GSON.toJson(TaskStore.get().shared());
		if (!dirty && json.equals(lastSent)) {
			return;
		}
		dirty = false;
		lastSent = json;
		ClientPlayNetworking.send(new ShareTasksC2S(json));
	}

	private static void receive(SharedTasksS2C payload) {
		if (!ModConfig.get().shareEnabled) {
			return;
		}
		String owner = payload.owner();
		if (owner == null || owner.isBlank()) {
			return;
		}
		Task[] tasks = GSON.fromJson(payload.json(), Task[].class);
		if (tasks == null || tasks.length == 0) {
			REMOTE.remove(owner);
			return;
		}
		List<Task> list = new ArrayList<>(Arrays.asList(tasks));
		for (Task task : list) {
			task.normalize();
			task.remote = true;
			task.owner = owner;
		}
		REMOTE.put(owner, list);
		com.easytaskmgr.EasyTaskMgr.LOGGER.info("收到队友 {} 的 {} 条共享待办", owner, list.size());
	}

	public static List<Task> remoteAll() {
		List<Task> all = new ArrayList<>();
		for (List<Task> list : REMOTE.values()) {
			all.addAll(list);
		}
		return all;
	}

	public static Map<String, List<Task>> remoteByOwner() {
		return REMOTE;
	}

	public static int remoteCount() {
		int n = 0;
		for (List<Task> list : REMOTE.values()) {
			n += list.size();
		}
		return n;
	}
}
