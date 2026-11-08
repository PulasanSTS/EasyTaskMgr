package com.easytaskmgr.client;

import com.easytaskmgr.net.ShareActionC2S;
import com.easytaskmgr.net.ShareProgressC2S;
import com.easytaskmgr.share.SharedTasks;
import com.easytaskmgr.task.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端保存的服务端共享任务快照。
 *
 * <p>服务端每次变化都会推一份快照过来（{@code share_snapshot}），这里解析成可渲染的条目；
 * 本地操作（认领 / 完成 / 撤回 / 邀请）只是把请求发给服务端，等下一份快照回来再更新 UI。
 */
public final class ClientShared {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	/** 服务端共享协议版本，-1 表示还没收到过快照（服务端没装模组）。 */
	private static int proto = -1;

	public static final class Entry {
		public String id = "";
		public String owner = "";
		public Task task = new Task();
		public boolean done;
		public int visibility = 1;
		public boolean claimed;
		public int claims;
		public boolean mine;
		public boolean canClaim;
		public int mode = SharedTasks.MODE_ANY_CREATOR;
		public boolean doneByMe;
		public int claimsDone;
		public int participants;
		public int sumCount;
		public int targetCount = 1;

		public boolean includesCreator() {
			return SharedTasks.includesCreator(mode);
		}

		public boolean requiresAll() {
			return SharedTasks.requiresAll(mode);
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

	private ClientShared() {
	}

	public static List<Entry> all() {
		return new ArrayList<>(ENTRIES.values());
	}

	public static Entry byId(String id) {
		return ENTRIES.get(id);
	}

	/** 服务端装了同版本模组才允许发布 / 认领。 */
	public static boolean available() {
		return proto >= SharedTasks.PROTO;
	}

	public static int proto() {
		return proto;
	}

	public static void reset() {
		ENTRIES.clear();
		proto = -1;
	}

	public static void handleSnapshot(String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			proto = root.has("proto") ? root.get("proto").getAsInt() : -1;
			ENTRIES.clear();
			for (JsonElement element : root.getAsJsonArray("tasks")) {
				if (!(element instanceof JsonObject o)) {
					continue;
				}
				Entry entry = new Entry();
				entry.id = o.get("id").getAsString();
				entry.owner = o.get("owner").getAsString();
				entry.task = GSON.fromJson(o.get("json").getAsString(), Task.class);
				if (entry.task == null) {
					continue;
				}
				entry.task.normalize();
				entry.done = o.get("done").getAsBoolean();
				entry.visibility = o.get("visibility").getAsInt();
				entry.claimed = o.get("claimed").getAsBoolean();
				entry.claims = o.get("claims").getAsInt();
				entry.mine = o.get("mine").getAsBoolean();
				entry.canClaim = o.get("canClaim").getAsBoolean();
				entry.mode = o.get("mode").getAsInt();
				entry.doneByMe = o.get("doneByMe").getAsBoolean();
				entry.claimsDone = o.get("claimsDone").getAsInt();
				entry.participants = o.get("participants").getAsInt();
				entry.sumCount = o.get("sumCount").getAsInt();
				entry.targetCount = o.get("targetCount").getAsInt();
				ENTRIES.put(entry.id, entry);
			}
		} catch (Exception e) {
			com.easytaskmgr.EasyTaskMgrNeo.LOGGER.warn("解析共享任务快照失败", e);
		}
	}

	/** 进服后主动要一份快照（同时也让服务端知道我们装了模组）。 */
	public static void sync() {
		send(new ShareActionC2S("sync", "", "", ""));
	}

	public static void publish(Task task) {
		send(new ShareActionC2S("publish", task.id, GSON.toJson(task), ""));
	}

	public static void claim(String id) {
		send(new ShareActionC2S("claim", id, "", ""));
	}

	public static void complete(String id) {
		send(new ShareActionC2S("complete", id, "", ""));
	}

	public static void revoke(String id) {
		send(new ShareActionC2S("revoke", id, "", ""));
	}

	public static void invite(String id, String target) {
		send(new ShareActionC2S("invite", id, "", target));
	}

	private static void send(ShareActionC2S payload) {
		try {
			PacketDistributor.sendToServer(payload);
		} catch (Exception e) {
			com.easytaskmgr.EasyTaskMgrNeo.LOGGER.warn("发送共享任务请求失败", e);
		}
	}

	/** 每秒（20 tick）上报一次「共同完成」任务的背包数量。 */
	public static void tick(Minecraft mc) {
		if (mc.player == null || !available()) {
			return;
		}
		for (Entry entry : ENTRIES.values()) {
			if (entry.done || entry.mode != SharedTasks.MODE_COMBINED) {
				continue;
			}
			if (!entry.claimed && !entry.mine) {
				continue;
			}
			if (entry.task.type != com.easytaskmgr.task.TaskType.COLLECT) {
				continue;
			}
			Item item = ClientUtil.item(entry.task.target);
			if (item == null) {
				continue;
			}
			int count = ClientUtil.countItem(mc, item);
			if (count > 0) {
				try {
					PacketDistributor.sendToServer(new ShareProgressC2S(entry.id, count));
				} catch (Exception ignored) {
					// 服务端没装模组时忽略
				}
			}
		}
	}

	/** 点击共享条目：未认领则认领，已认领则上报完成。 */
	public static void click(Entry entry) {
		if (entry.done) {
			return;
		}
		if (!entry.claimed && entry.canClaim) {
			claim(entry.id);
		} else if (entry.claimed && !entry.doneByMe) {
			complete(entry.id);
		}
	}
}
