package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.net.ShareActionC2S;
import com.easytaskmgr.net.ShareProgressC2S;
import com.easytaskmgr.net.ShareSnapshotS2C;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端共享任务缓存：保存服务端下发的快照，并提供发布 / 认领 / 完成 / 数量上报。
 *
 * <p>完成条件 {@code mode}：0 任一认领者 / 1 所有认领者 / 2 任一（含发布者）/
 * 3 所有（含发布者）/ 4 共同完成（收集任务数量相加）。
 *
 * <p>{@link #tick} 负责共享任务的自动判定：收集任务数背包、坐标任务看距离，
 * 「共同完成」模式则把本地数量上报给服务端累加。
 */
public final class ClientShared {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static final int MODE_ANY = 0;
	public static final int MODE_ALL = 1;
	public static final int MODE_ANY_CREATOR = 2;
	public static final int MODE_ALL_CREATOR = 3;
	public static final int MODE_COMBINED = 4;

	public record Entry(String id, String owner, Task task, boolean done, int visibility,
			boolean claimed, int claims, boolean mine, boolean canClaim,
			int mode, boolean doneByMe, int claimsDone, int participants, int sumCount, int targetCount) {

		public boolean includesCreator() {
			return mode == MODE_ANY_CREATOR || mode == MODE_ALL_CREATOR;
		}

		public boolean requiresAll() {
			return mode == MODE_ALL || mode == MODE_ALL_CREATOR;
		}

		public boolean combined() {
			return mode == MODE_COMBINED;
		}

		/** 自己是不是参与者：认领者，或（含发布者时）发布者。 */
		public boolean iAmParticipant() {
			return claimed || (includesCreator() && mine);
		}

		/** 「全部」模式：自己已完成，还在等别人。 */
		public boolean waitingOthers() {
			return requiresAll() && doneByMe && !done;
		}

		/** 进度文案：共同完成显示「总数/目标」，其余显示「已完成人数/参与人数」。 */
		public String progressText() {
			if (combined()) {
				return I18n.t("share.progress", sumCount, Math.max(1, targetCount));
			}
			if (participants <= 0) {
				return null;
			}
			return I18n.t("share.progress", claimsDone, participants);
		}
	}

	private static List<Entry> entries = List.of();
	private static final Map<String, Integer> REPORTED = new HashMap<>();
	/** 刚上报过完成的任务，避免快照回来之前每秒重复发送。 */
	private static final Map<String, Long> SENT_COMPLETE = new HashMap<>();
	private static boolean serverVersionWarned;

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

	public static void reset() {
		entries = List.of();
		REPORTED.clear();
		SENT_COMPLETE.clear();
		serverVersionWarned = false;
	}

	private static void receive(String json) {
		List<Entry> parsed = new ArrayList<>();
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			checkServerVersion(integer(root, "proto"));
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
				int mode = o.has("mode") ? o.get("mode").getAsInt()
						: (bool(o, "all") ? MODE_ALL_CREATOR : MODE_ANY_CREATOR);
				parsed.add(new Entry(o.get("id").getAsString(), o.get("owner").getAsString(), task, done,
						o.get("visibility").getAsInt(), o.get("claimed").getAsBoolean(),
						o.get("claims").getAsInt(), o.get("mine").getAsBoolean(), o.get("canClaim").getAsBoolean(),
						mode, bool(o, "doneByMe"), integer(o, "claimsDone"), integer(o, "participants"),
						integer(o, "sumCount"), integer(o, "targetCount")));
			}
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.warn("解析共享任务快照失败", e);
		}
		entries = parsed;
		SENT_COMPLETE.keySet().removeIf(id -> find(id) == null || find(id).done() || find(id).doneByMe());
		EasyTaskMgr.LOGGER.info("收到共享任务快照：{} 条", parsed.size());
	}

	/** 老版本服务端不会发新字段，缺字段时用默认值兜底。 */
	private static boolean bool(JsonObject o, String key) {
		return o.has(key) && o.get(key).getAsBoolean();
	}

	/**
	 * 服务端协议版本检查（issues #6 / #9 的排查结论）。
	 *
	 * <p>rc3 之前的服务端不认识 {@code shareMode}，发布出去的「所有（含发布者）」会被当成
	 * 「任一」，表现为「一个人完成就整条完成」，聊天提示也不会带前缀和绿色。
	 * 这里检测到旧服务端就提示一次，避免把版本差异误当成功能 bug。
	 */
	private static void checkServerVersion(int proto) {
		if (proto >= com.easytaskmgr.share.SharedTasks.PROTO) {
			return;
		}
		if (serverVersionWarned) {
			return;
		}
		serverVersionWarned = true;
		EasyTaskMgr.LOGGER.warn("服务端共享协议版本 {}，低于客户端 {}", proto, com.easytaskmgr.share.SharedTasks.PROTO);
		Notifier.warn(I18n.t("share.outdatedServer"));
	}

	private static int integer(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsInt() : 0;
	}

	private static void send(String action, String id, String json, String target) {
		if (ClientPlayNetworking.canSend(ShareActionC2S.TYPE)) {
			ClientPlayNetworking.send(new ShareActionC2S(action, id, json, target));
		}
	}

	public static void publish(Task task) {
		EasyTaskMgr.LOGGER.info("发布共享任务 {}（visibility={}，mode={}）", task.id, task.visibility, task.shareMode);
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

	/** 上报收集数量（共同完成模式）。 */
	public static void reportProgress(String id, int count) {
		if (!ClientPlayNetworking.canSend(ShareProgressC2S.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new ShareProgressC2S(id, count));
	}

	/**
	 * 共享任务的自动判定（约每秒调用一次）。
	 *
	 * <p>只为「自己参与」的任务判定：收集任务数背包、坐标任务看距离；
	 * 共同完成模式则把本地数量上报，由服务端把所有认领者的最大值相加。
	 */
	public static void tick(Minecraft mc) {
		if (mc.player == null || entries.isEmpty()) {
			return;
		}
		for (Entry entry : entries) {
			// 「共同完成」时发布者也参与计数（issue #11），其余模式只处理参与者
			if (entry.done() || !(entry.iAmParticipant() || (entry.combined() && entry.mine()))) {
				continue;
			}
			Task task = entry.task();
			switch (task.type) {
				case COLLECT -> {
					if (entry.combined()) {
						// 发布者也计入共同完成的数量（issue #11）
						if (!entry.claimed() && !entry.mine()) {
							continue;
						}
						int count = ClientTasks.countItem(mc, task.target);
						int sent = REPORTED.getOrDefault(entry.id(), -1);
						if (count > sent) {
							REPORTED.put(entry.id(), count);
							reportProgress(entry.id(), count);
						}
					} else if (!entry.doneByMe() && !recentlyCompleted(entry.id())
							&& ClientTasks.countItem(mc, task.target) >= Math.max(1, task.targetCount)) {
						markCompleted(entry.id());
						complete(entry.id());
						notifyProgress(entry, willWaitOthers(entry));
					}
				}
				case COORDINATE -> {
					if (!entry.doneByMe() && !recentlyCompleted(entry.id())
							&& ClientTasks.horizontalDistance(mc, task) <= Math.max(1, task.radius)) {
						markCompleted(entry.id());
						complete(entry.id());
						notifyProgress(entry, willWaitOthers(entry));
					}
				}
				default -> {
					// 普通 / 时间提醒任务由玩家手动点完成
				}
			}
		}
	}

	/** 共享任务完成时的提示：绿色 + 音效 + 附加提醒（issue #8）。 */
	public static void notifyProgress(Entry entry, boolean partial) {
		Task task = entry.task();
		if (partial) {
			String msg = I18n.t("share.partialToast", entry.claimsDone() + 1, Math.max(1, entry.participants()));
			Notifier.info(msg);
			ClientTasks.toast(msg);
			return;
		}
		String msg = I18n.t("notify.completed", task.displayText());
		Notifier.success(msg);
		ClientTasks.toast("✓ " + msg);
		if (task.remindText != null && !task.remindText.isBlank()) {
			Notifier.info(I18n.t("notify.extra", task.displayText(), task.remindText));
		}
	}

	/** 判断点击「完成」后是整条完成还是只完成了自己那部分。 */
	public static boolean willWaitOthers(Entry entry) {
		return entry.requiresAll() && entry.claimsDone() + 1 < Math.max(1, entry.participants());
	}

	private static boolean recentlyCompleted(String id) {
		Long at = SENT_COMPLETE.get(id);
		return at != null && System.currentTimeMillis() - at < 5000L;
	}

	private static void markCompleted(String id) {
		SENT_COMPLETE.put(id, System.currentTimeMillis());
	}
}
