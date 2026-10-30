package com.easytaskmgr.task;

import com.easytaskmgr.EasyTaskMgr;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 任务仓库：内存列表 + 本地 JSON 持久化。
 *
 * <p>文件位置：{@code <游戏目录>/config/easytaskmgr-tasks.json}。
 */
public final class TaskStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final TaskStore INSTANCE = new TaskStore();

	private final List<Task> tasks = new ArrayList<>();
	private boolean dirty;
	private boolean loaded;

	private TaskStore() {
	}

	public static TaskStore get() {
		return INSTANCE;
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("easytaskmgr-tasks.json");
	}

	public synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = file();
		if (!Files.exists(path)) {
			EasyTaskMgr.LOGGER.info("没有找到任务文件，使用空列表: {}", path);
			return;
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			Task[] arr = GSON.fromJson(json, Task[].class);
			tasks.clear();
			if (arr != null) {
				for (Task t : arr) {
					if (t != null) {
						t.normalize();
						tasks.add(t);
					}
				}
			}
			EasyTaskMgr.LOGGER.info("已载入 {} 条任务: {}", tasks.size(), path);
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.error("读取任务文件失败: {}", path, e);
		}
	}

	public synchronized void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(tasks), StandardCharsets.UTF_8);
			dirty = false;
		} catch (IOException e) {
			EasyTaskMgr.LOGGER.error("写入任务文件失败: {}", path, e);
		}
	}

	/** 有改动就写盘，客户端每 5 秒调用一次，避免每 tick 都写文件。 */
	public synchronized void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	public void markDirty() {
		dirty = true;
	}

	public synchronized List<Task> all() {
		return tasks;
	}

	public synchronized void add(Task task) {
		tasks.add(task);
		markDirty();
	}

	public synchronized boolean remove(Task task) {
		boolean removed = tasks.remove(task);
		if (removed) {
			markDirty();
		}
		return removed;
	}

	public synchronized Optional<Task> byId(String id) {
		return tasks.stream().filter(t -> t.id.equals(id)).findFirst();
	}

	public synchronized void toggleDone(Task task) {
		task.done = !task.done;
		task.completedAt = task.done ? System.currentTimeMillis() : 0L;
		if (!task.done) {
			task.triggered = false;
		}
		markDirty();
	}

	public synchronized void complete(Task task) {
		if (!task.done) {
			task.done = true;
			task.completedAt = System.currentTimeMillis();
			markDirty();
		}
	}

	/** 未完成的任务：优先级高的在前，同优先级按创建时间。 */
	public synchronized List<Task> active() {
		List<Task> list = new ArrayList<>();
		for (Task t : tasks) {
			if (!t.done) {
				list.add(t);
			}
		}
		list.sort(Comparator.comparingInt((Task t) -> -t.priority).thenComparingLong(t -> t.createdAt));
		return list;
	}

	public synchronized List<Task> completed() {
		List<Task> list = new ArrayList<>();
		for (Task t : tasks) {
			if (t.done) {
				list.add(t);
			}
		}
		list.sort(Comparator.comparingLong((Task t) -> -t.completedAt));
		return list;
	}

	/** 需要共享给队友的任务（未完成且开启了共享）。 */
	public synchronized List<Task> shared() {
		List<Task> list = new ArrayList<>();
		for (Task t : tasks) {
			if (!t.done && t.shared) {
				list.add(t.copyForShare());
			}
		}
		return list;
	}

	public synchronized void clearCompleted() {
		if (tasks.removeIf(t -> t.done)) {
			markDirty();
		}
	}
}
