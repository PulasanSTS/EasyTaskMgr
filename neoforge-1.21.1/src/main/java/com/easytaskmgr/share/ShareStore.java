package com.easytaskmgr.share;

import com.easytaskmgr.EasyTaskMgrNeo;
import com.easytaskmgr.task.TaskType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 共享任务的存档（服务端）。
 *
 * <p>存到世界存档目录的 {@code data/easytaskmgr_share.json}，服务端启动时读回、停机时再强存一次；
 * 平时每次增删改都立即落盘（数据量很小，几十条任务只有几 KB）。
 *
 * <p>手写 JSON 而不是 {@code SavedData}：共享任务表是「id → 条目」的松散结构，
 * 这样能保持与网络快照一致的字段命名，也方便玩家自己备份 / 排查。
 */
public final class ShareStore {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final String FILE_NAME = "easytaskmgr_share.json";
	/** 存档格式版本 2：完成条件改成 {@code mode}（0..4），新增 {@code targetCount} / {@code maxCounts} / {@code type}。 */
	private static final int VERSION = 2;

	private ShareStore() {
	}

	public static Path file(MinecraftServer server) {
		// 1.21.1 没有 LevelResource.DATA，用世界根目录下的 data/
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
	}

	/** 存档读取失败过：本次运行不再覆盖磁盘上的旧文件（review #6）。 */
	private static boolean loadFailed;

	public static boolean loadFailed() {
		return loadFailed;
	}

	/** 启动时调用：读回条目，把「已完成」的任务也一并恢复，避免重启后列表闪一下。 */
	public static void load(MinecraftServer server) {
		// 切世界时先清空静态表，避免上一个世界的任务串到新世界（review #5）
		SharedTasks.BY_ID.clear();
		loadFailed = false;
		Path path = file(server);
		if (!Files.isRegularFile(path)) {
			EasyTaskMgrNeo.LOGGER.info("共享任务存档不存在，跳过恢复：{}", path);
			return;
		}
		try {
			String text = Files.readString(path, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			int version = root.has("version") ? root.get("version").getAsInt() : 0;
			int count = 0;
			for (JsonElement element : root.getAsJsonArray("tasks")) {
				if (!(element instanceof JsonObject o)) {
					continue;
				}
				// 单行坏掉只跳过这一行，不放弃整个文件（review #6）
				try {
					SharedTasks.Entry entry = toEntry(o);
					if (entry != null) {
						SharedTasks.BY_ID.put(entry.id, entry);
						count++;
					}
				} catch (Exception rowError) {
					EasyTaskMgrNeo.LOGGER.warn("跳过一条损坏的共享任务存档：{}", o, rowError);
				}
			}
			EasyTaskMgrNeo.LOGGER.info("已从存档恢复 {} 条共享任务（格式版本 {}）", count, version);
		} catch (Exception e) {
			loadFailed = true;
			EasyTaskMgrNeo.LOGGER.error("读取共享任务存档失败（{}），本次按空表启动，且不会覆盖原文件", path, e);
		}
	}

	/** 解析一条存档；字段非法返回 null（由调用方计数跳过）。 */
	private static SharedTasks.Entry toEntry(JsonObject o) {
		SharedTasks.Entry entry = new SharedTasks.Entry();
		entry.id = string(o, "id");
		if (entry.id.isBlank()) {
			return null;
		}
		entry.creator = UUID.fromString(string(o, "creator"));
		entry.creatorName = string(o, "creatorName");
		entry.json = string(o, "json");
		if (entry.json.isBlank()) {
			return null;
		}
		entry.visibility = o.has("visibility") ? o.get("visibility").getAsInt() : 1;
		entry.title = string(o, "title");
		entry.labelKey = string(o, "labelKey");
		entry.done = bool(o, "done");
		// 版本 1 只有 all 布尔值，迁移到新的 0..4 完成条件
		entry.mode = o.has("mode")
				? SharedTasks.normalizeMode(o.get("mode").getAsInt())
				: (bool(o, "all") ? SharedTasks.MODE_ALL_CREATOR : SharedTasks.MODE_ANY_CREATOR);
		entry.targetCount = o.has("targetCount") ? Math.max(1, o.get("targetCount").getAsInt()) : 1;
		entry.type = TaskType.byName(string(o, "type"));
		entry.target = string(o, "target");
		for (JsonElement name : array(o, "invited")) {
			entry.invited.add(name.getAsString());
		}
		for (JsonElement uuid : array(o, "claimed")) {
			entry.claimed.add(UUID.fromString(uuid.getAsString()));
		}
		for (JsonElement uuid : array(o, "completedBy")) {
			entry.completedBy.add(UUID.fromString(uuid.getAsString()));
		}
		if (o.has("maxCounts") && o.get("maxCounts").isJsonObject()) {
			for (var e : o.getAsJsonObject("maxCounts").entrySet()) {
				entry.maxCounts.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
			}
		}
		return entry;
	}

	/**
	 * 立即把当前共享任务表写盘（先写临时文件再替换，避免写一半崩溃留下坏档）。
	 *
	 * @return false 表示没写成功，调用方应保留 dirty 以便重试
	 */
	public static boolean save(MinecraftServer server) {
		if (loadFailed) {
			// 读档失败时不要用空表覆盖原文件（review #6）
			EasyTaskMgrNeo.LOGGER.warn("共享任务存档上次读取失败，跳过本次写盘");
			return false;
		}
		Path path = file(server);
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonArray tasks = new JsonArray();
		for (SharedTasks.Entry entry : SharedTasks.BY_ID.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", entry.id);
			o.addProperty("creator", entry.creator.toString());
			o.addProperty("creatorName", entry.creatorName);
			o.addProperty("json", entry.json);
			o.addProperty("visibility", entry.visibility);
			o.addProperty("title", entry.title);
			o.addProperty("labelKey", entry.labelKey);
			o.addProperty("mode", entry.mode);
			o.addProperty("targetCount", entry.targetCount);
			o.addProperty("type", entry.type.name());
			o.addProperty("target", entry.target);
			o.addProperty("done", entry.done);
			JsonArray invited = new JsonArray();
			entry.invited.forEach(invited::add);
			o.add("invited", invited);
			JsonArray claimed = new JsonArray();
			entry.claimed.forEach(uuid -> claimed.add(uuid.toString()));
			o.add("claimed", claimed);
			JsonArray completedBy = new JsonArray();
			entry.completedBy.forEach(uuid -> completedBy.add(uuid.toString()));
			o.add("completedBy", completedBy);
			JsonObject maxCounts = new JsonObject();
			entry.maxCounts.forEach((uuid, value) -> maxCounts.addProperty(uuid.toString(), value));
			o.add("maxCounts", maxCounts);
			tasks.add(o);
		}
		root.add("tasks", tasks);

		try {
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path temp = path.resolveSibling(FILE_NAME + ".tmp");
			Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (Exception e) {
			EasyTaskMgrNeo.LOGGER.error("写入共享任务存档失败（{}）", path, e);
			return false;
		}
	}

	private static String string(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private static boolean bool(JsonObject o, String key) {
		return o.has(key) && o.get(key).getAsBoolean();
	}

	private static JsonArray array(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}
}
