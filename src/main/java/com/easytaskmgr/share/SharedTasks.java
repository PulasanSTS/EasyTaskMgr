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
import net.minecraft.ChatFormatting;
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
 *
 * <p>完成条件（{@code MODE_*}）：
 * <ul>
 *   <li>{@link #MODE_ANY}：任一认领者完成即整条完成，发布者不算参与者；</li>
 *   <li>{@link #MODE_ALL}：所有认领者都要完成，发布者不算参与者；</li>
 *   <li>{@link #MODE_ANY_CREATOR}：任一认领者或发布者完成即整条完成；</li>
 *   <li>{@link #MODE_ALL_CREATOR}：发布者 + 所有认领者都要完成；</li>
 *   <li>{@link #MODE_COMBINED}：收集任务专用——把每个认领者「背包里见过的最大数量」相加，
 *       达到目标数量就整条完成。</li>
 * </ul>
 *
 * <p>每次发布 / 认领 / 完成 / 数量上报都会给「能看见这条任务」的玩家发一条带
 * {@code [EasyTaskMgr]} 前缀的绿色聊天提示，文案用 {@link Component#translatable} 下发，
 * 由客户端按自己的语言解析。
 */
public final class SharedTasks {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	/** 任一认领者（发布者不算）。 */
	public static final int MODE_ANY = 0;
	/** 所有认领者（发布者不算）。 */
	public static final int MODE_ALL = 1;
	/** 任一认领者或发布者。 */
	public static final int MODE_ANY_CREATOR = 2;
	/** 发布者 + 所有认领者。 */
	public static final int MODE_ALL_CREATOR = 3;
	/** 收集任务：数量相加。 */
	public static final int MODE_COMBINED = 4;

	public static boolean includesCreator(int mode) {
		return mode == MODE_ANY_CREATOR || mode == MODE_ALL_CREATOR;
	}

	public static boolean requiresAll(int mode) {
		return mode == MODE_ALL || mode == MODE_ALL_CREATOR;
	}

	public static final class Entry {
		public String id;
		public UUID creator;
		public String creatorName;
		public String json;
		public int visibility;
		/** 任务标题（用户填写的文本），用于聊天提示。 */
		public String title = "";
		/** 标题为空时用于拼接提示的类型翻译键，可为空。 */
		public String labelKey = "";
		/** 完成条件，见 {@code MODE_*}。 */
		public int mode = MODE_ANY_CREATOR;
		/** 收集任务的目标数量（{@link #MODE_COMBINED} 用）。 */
		public int targetCount = 1;
		/** 任务类型（成就任务不再支持共享，快照里会被跳过）。 */
		public TaskType type = TaskType.NORMAL;
		public final Set<String> invited = new LinkedHashSet<>();
		public final Set<UUID> claimed = new LinkedHashSet<>();
		/** 已上报完成（或「自己这部分完成」）的参与者。 */
		public final Set<UUID> completedBy = new LinkedHashSet<>();
		/** 收集任务：每个认领者上报过的最大数量。 */
		public final Map<UUID, Integer> maxCounts = new LinkedHashMap<>();
		public boolean done;

		boolean invited(String name) {
			for (String value : invited) {
				if (value.equalsIgnoreCase(name)) {
					return true;
				}
			}
			return false;
		}

		/** 参与者集合：认领者，加上（视完成条件而定）发布者。 */
		Set<UUID> participants() {
			Set<UUID> set = new LinkedHashSet<>(claimed);
			if (includesCreator(mode)) {
				set.add(creator);
			}
			return set;
		}

		boolean isParticipant(UUID uuid) {
			return claimed.contains(uuid) || (includesCreator(mode) && creator.equals(uuid));
		}

		/** 已完成自己那部分的参与者数量。 */
		int doneCount() {
			int done = 0;
			for (UUID uuid : participants()) {
				if (completedBy.contains(uuid)) {
					done++;
				}
			}
			return done;
		}

		/** 收集任务：所有认领者最大数量的和（发布者不计入相加）。 */
		int sumCounts() {
			int sum = 0;
			for (UUID uuid : claimed) {
				sum += maxCounts.getOrDefault(uuid, 0);
			}
			return sum;
		}

		boolean visibleTo(ServerPlayer player) {
			String name = player.getName().getString();
			return visibility == 1
					|| creator.equals(player.getUUID())
					|| invited(name)
					|| claimed.contains(player.getUUID());
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
		// 成就任务的多人支持已移除（issue #4）：服务端直接忽略
		if (task.type == TaskType.ADVANCEMENT) {
			EasyTaskMgr.LOGGER.info("忽略共享的成就任务 {}（已移除成就的多人支持）", task.id);
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
		entry.mode = normalizeMode(task.shareMode);
		entry.targetCount = Math.max(1, task.targetCount);
		entry.type = task.type;
		Entry old = BY_ID.get(entry.id);
		if (old != null) {
			entry.invited.addAll(old.invited);
			entry.claimed.addAll(old.claimed);
			// 完成条件没变就保留已上报的进度与已统计的数量，变了就清零重来
			if (old.mode == entry.mode) {
				entry.completedBy.addAll(old.completedBy);
				entry.maxCounts.putAll(old.maxCounts);
			}
		}
		BY_ID.put(entry.id, entry);
		dirty = true;
		EasyTaskMgr.LOGGER.info("{} 发布共享任务 {}（{}，完成条件={}）", entry.creatorName, entry.id, entry.title,
				modeName(entry.mode));
		if (old == null || old.visibility != entry.visibility || old.mode != entry.mode
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
		// 发布者通过「含发布者」的完成条件参与，不需要认领
		if (entry.creator.equals(player.getUUID())) {
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
	 * 上报「我完成了」。
	 *
	 * <p>「全部完成」家族要求所有参与者都上报；「任一」家族第一个人上报就整条完成。
	 * 发布者只有在完成条件含发布者时才算参与者。
	 */
	public static void complete(MinecraftServer server, ServerPlayer player, String id) {
		Entry entry = BY_ID.get(id);
		if (entry == null || entry.done) {
			return;
		}
		UUID uuid = player.getUUID();
		if (entry.mode == MODE_COMBINED || !entry.isParticipant(uuid)) {
			return;
		}
		if (entry.completedBy.contains(uuid)) {
			return;
		}
		entry.completedBy.add(uuid);
		dirty = true;
		String name = player.getName().getString();
		boolean finished = !requiresAll(entry.mode) || entry.completedBy.containsAll(entry.participants());
		EasyTaskMgr.LOGGER.info("{} 完成共享任务 {}（{}/{}，{}）", name, entry.id,
				entry.doneCount(), entry.participants().size(), finished ? "整条完成" : "等待其他参与者");
		if (finished) {
			entry.done = true;
			announceComplete(server, entry, name);
		} else {
			announcePartial(server, entry, name);
		}
	}

	/** 收集任务「共同完成」：记录每个认领者见过的最大数量，累加达到目标就整条完成。 */
	public static void progress(MinecraftServer server, ServerPlayer player, String id, int count) {
		Entry entry = BY_ID.get(id);
		if (entry == null || entry.done || entry.mode != MODE_COMBINED) {
			return;
		}
		UUID uuid = player.getUUID();
		if (!entry.claimed.contains(uuid)) {
			return;
		}
		int previous = entry.maxCounts.getOrDefault(uuid, 0);
		if (count <= previous) {
			return;
		}
		entry.maxCounts.put(uuid, count);
		dirty = true;
		int sum = entry.sumCounts();
		if (sum >= entry.targetCount) {
			entry.done = true;
			EasyTaskMgr.LOGGER.info("共享收集任务 {} 达成共同完成（{}/{}）", entry.id, sum, entry.targetCount);
			announceComplete(server, entry, player.getName().getString());
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

	/** 「全部」模式有人先完成自己的部分：{@code xxx 完成了 yyy 发布的「zzz」（1/2）}。 */
	private static void announcePartial(MinecraftServer server, Entry entry, String playerName) {
		announce(server, entry, "easytaskmgr.share.partial", playerName, entry.creatorName, titleArg(entry),
				entry.doneCount(), entry.participants().size());
	}

	/**
	 * 发送一条共享任务提示。
	 *
	 * <p>格式与客户端单人提示保持一致：`[EasyTaskMgr]` 青色前缀 + 绿色正文（issue #6）。
	 */
	private static void announce(MinecraftServer server, Entry entry, String key, Object... args) {
		MutableComponent message = Component.literal("[EasyTaskMgr] ").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable(key, args).withStyle(ChatFormatting.GREEN));
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

	/** 把客户端传来的完成条件收敛到合法范围。 */
	public static int normalizeMode(int mode) {
		return mode >= MODE_ANY && mode <= MODE_COMBINED ? mode : MODE_ANY_CREATOR;
	}

	public static String modeName(int mode) {
		return switch (mode) {
			case MODE_ANY -> "任一认领者";
			case MODE_ALL -> "所有认领者";
			case MODE_ANY_CREATOR -> "任一（含发布者）";
			case MODE_ALL_CREATOR -> "所有（含发布者）";
			case MODE_COMBINED -> "共同完成（数量相加）";
			default -> "未知";
		};
	}

	public static String snapshot(ServerPlayer player) {
		JsonObject root = new JsonObject();
		JsonArray arr = new JsonArray();
		for (Entry entry : BY_ID.values()) {
			if (!entry.visibleTo(player)) {
				continue;
			}
			// 成就任务的多人支持已移除，历史条目不再下发
			if (entry.type == TaskType.ADVANCEMENT) {
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
					&& (entry.visibility == 1 || entry.invited(player.getName().getString())));
			o.addProperty("mode", entry.mode);
			o.addProperty("doneByMe", entry.completedBy.contains(player.getUUID()));
			o.addProperty("claimsDone", entry.doneCount());
			o.addProperty("participants", entry.participants().size());
			o.addProperty("sumCount", entry.sumCounts());
			o.addProperty("targetCount", entry.targetCount);
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
