package com.easytaskmgr.share;

import com.easytaskmgr.EasyTaskMgr;
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
 * <p>不用 {@code SavedData} 的原因：共享任务表是「id → 条目」的松散结构，
 * 手写 JSON 既能保持与网络快照一致的字段命名，也方便玩家自己备份 / 排查。
 */
public final class ShareStore {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final String FILE_NAME = "easytaskmgr_share.json";
	/** 存档格式版本，将来改结构时用来做迁移。 */
	private static final int VERSION = 1;

	private ShareStore() {
	}

	public static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.DATA).resolve(FILE_NAME);
	}

	/** 启动时调用：读回条目，把「已完成」的任务也一并恢复，避免重启后列表闪一下。 */
	public static void load(MinecraftServer server) {
		Path path = file(server);
		if (!Files.isRegularFile(path)) {
			EasyTaskMgr.LOGGER.info("共享任务存档不存在，跳过恢复：{}", path);
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
				SharedTasks.Entry entry = new SharedTasks.Entry();
				entry.id = string(o, "id");
				if (entry.id.isBlank()) {
					continue;
				}
				entry.creator = UUID.fromString(string(o, "creator"));
				entry.creatorName = string(o, "creatorName");
				entry.json = string(o, "json");
				if (entry.json.isBlank()) {
					continue;
				}
				entry.visibility = o.has("visibility") ? o.get("visibility").getAsInt() : 1;
				entry.title = string(o, "title");
				entry.labelKey = string(o, "labelKey");
				entry.all = bool(o, "all");
				entry.done = bool(o, "done");
				for (JsonElement name : array(o, "invited")) {
					entry.invited.add(name.getAsString());
				}
				for (JsonElement uuid : array(o, "claimed")) {
					entry.claimed.add(UUID.fromString(uuid.getAsString()));
				}
				for (JsonElement uuid : array(o, "completedBy")) {
					entry.completedBy.add(UUID.fromString(uuid.getAsString()));
				}
				SharedTasks.BY_ID.put(entry.id, entry);
				count++;
			}
			EasyTaskMgr.LOGGER.info("已从存档恢复 {} 条共享任务（格式版本 {}）", count, version);
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.error("读取共享任务存档失败（{}），本次按空表启动", path, e);
		}
	}

	/** 立即把当前共享任务表写盘（先写临时文件再替换，避免写一半崩溃留下坏档）。 */
	public static void save(MinecraftServer server) {
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
			o.addProperty("all", entry.all);
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
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.error("写入共享任务存档失败（{}）", path, e);
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
