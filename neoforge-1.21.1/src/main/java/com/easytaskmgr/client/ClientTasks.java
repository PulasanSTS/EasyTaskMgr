package com.easytaskmgr.client;

import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import com.easytaskmgr.task.TimerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 客户端任务逻辑：列表读写、自动检测（收集 / 坐标 / 时间提醒）、HUD 进度文本。
 *
 * <p>成就任务的完成由服务端通过 {@code AdvancementEarnedS2C} 告知（1.21.1 客户端拿不到成就进度）。
 */
public final class ClientTasks {
	/** HUD 顶部的闪烁计时（0 表示不闪）。 */
	static int flashTicks;

	private ClientTasks() {
	}

	public static List<Task> active() {
		List<Task> list = new ArrayList<>();
		for (Task task : TaskStore.get().all()) {
			if (!task.done) {
				list.add(task);
			}
		}
		list.sort(Comparator.comparingInt((Task t) -> -t.priority).thenComparingLong(t -> t.createdAt));
		return list;
	}

	public static List<Task> completed() {
		List<Task> list = new ArrayList<>();
		for (Task task : TaskStore.get().all()) {
			if (task.done) {
				list.add(task);
			}
		}
		list.sort(Comparator.comparingLong((Task t) -> -t.completedAt));
		return list;
	}

	public static void add(Task task) {
		task.createdAt = System.currentTimeMillis();
		task.normalize();
		TaskStore.get().add(task);
		TaskStore.get().save();
	}

	public static void remove(Task task) {
		TaskStore.get().remove(task);
		TaskStore.get().save();
	}

	/** 标记完成：写盘 + 聊天提示 + 音效。共享任务走服务端，不在这里。 */
	public static void complete(Task task) {
		if (task.done) {
			return;
		}
		task.done = true;
		task.completedAt = System.currentTimeMillis();
		TaskStore.get().save();
		String suffix = task.remindText == null || task.remindText.isBlank()
				? ""
				: " — " + task.remindText;
		ClientUtil.remind(ClientUtil.t("notify.completed", title(task)) + suffix);
	}

	public static String title(Task task) {
		if (task.text != null && !task.text.isBlank()) {
			return task.text;
		}
		return ClientUtil.t("type." + task.type.name().toLowerCase());
	}

	/** 每秒执行一次：自动检测收集 / 坐标任务，并处理时间提醒。 */
	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Task task : active()) {
			switch (task.type) {
				case COLLECT -> {
					Item item = ClientUtil.item(task.target);
					if (item != null && ClientUtil.countItem(mc, item) >= Math.max(1, task.targetCount)) {
						complete(task);
					}
				}
				case COORDINATE -> {
					double dx = mc.player.getX() - task.x;
					double dy = mc.player.getY() - task.y;
					double dz = mc.player.getZ() - task.z;
					if (dx * dx + dy * dy + dz * dz <= (double) task.radius * task.radius) {
						complete(task);
					}
				}
				case TIMER -> tickTimer(mc, task, now);
				default -> {
				}
			}
		}
	}

	private static void tickTimer(Minecraft mc, Task task, long now) {
		switch (task.timerMode) {
			case INTERVAL -> {
				long interval = Math.max(1, task.intervalMinutes) * 60_000L;
				if (now - task.createdAt >= interval) {
					task.createdAt = now;
					TaskStore.get().save();
					ClientUtil.remind(ClientUtil.t("notify.reminder", title(task)));
				}
			}
			case GAME_TIME -> {
				long dayTime = mc.level.getDayTime() % 24000L;
				long ticks = task.gameHour * 1000L + task.gameMinute * 1000L / 60L;
				long day = mc.level.getDayTime() / 24000L;
				long interval = Math.max(1, task.gameDayInterval);
				if (Math.abs(dayTime - ticks) <= 20 && day % interval == 0 && !task.triggered) {
					task.triggered = true;
					TaskStore.get().save();
					ClientUtil.remind(ClientUtil.t("notify.reminder", title(task)));
				}
			}
			default -> {
			}
		}
	}

	/** 服务端通知某个成就达成：把对应的成就类待办标记完成。 */
	public static void completeAdvancement(String advancementId) {
		if (advancementId == null || advancementId.isBlank()) {
			return;
		}
		for (Task task : active()) {
			if (task.type == TaskType.ADVANCEMENT && advancementId.equals(task.target)) {
				complete(task);
			}
		}
	}
	/** HUD / 列表里显示的一行进度说明（没有就返回空串）。 */
	public static String progressText(Minecraft mc, Task task) {
		if (task.type == TaskType.COLLECT && task.target != null && !task.target.isBlank()) {
			Item item = ClientUtil.item(task.target);
			if (item != null) {
				int have = ClientUtil.countItem(mc, item);
				return ClientUtil.itemName(task.target) + " " + have + "/" + Math.max(1, task.targetCount);
			}
		}
		if (task.type == TaskType.ADVANCEMENT) {
			return ClientUtil.advancementName(task.target);
		}
		if (task.type == TaskType.COORDINATE) {
			return (int) task.x + ", " + (int) task.y + ", " + (int) task.z + " · " + task.radius + "m";
		}
		if (task.type == TaskType.TIMER) {
			return switch (task.timerMode) {
				case ON_JOIN -> ClientUtil.t("timer.join");
				case EVERY_JOIN -> ClientUtil.t("timer.everyJoin");
				case INTERVAL -> ClientUtil.t("timer.interval") + " (" + task.intervalMinutes + "m)";
				case GAME_TIME -> ClientUtil.t("timer.gameTime") + " " + String.format("%02d:%02d", task.gameHour, task.gameMinute);
			};
		}
		return "";
	}

	public static String priorityTag(Task task) {
		Priority priority = Priority.byWeight(task.priority);
		return "[" + priority.label() + "]";
	}
}
