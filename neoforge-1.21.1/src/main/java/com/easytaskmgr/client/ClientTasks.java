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
					// 只算水平距离：挖到脚下也能算到达（review #16）
					double dx = mc.player.getX() - task.x;
					double dz = mc.player.getZ() - task.z;
					if (dx * dx + dz * dz <= (double) task.radius * task.radius) {
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
				// Minecraft 的一天从 06:00 开始，且整合包可能改过昼夜长度（review #14）
				long dayLength = Math.max(2000, com.easytaskmgr.config.ModConfig.get().ticksPerDay);
				long dayTime = mc.level.getDayTime() % dayLength;
				long day = mc.level.getDayTime() / dayLength;
				long target = Math.floorMod((task.gameHour - 6) * 1000L + task.gameMinute * 1000L / 60L, dayLength);
				long interval = Math.max(1, task.gameDayInterval);
				// 新的一天重置触发标记，否则只会响一次（review #15）
				if (task.lastGameDay != day) {
					task.triggered = false;
					task.lastGameDay = day;
				}
				if (!task.triggered && day % interval == 0 && Math.abs(dayTime - target) <= 20) {
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
	/** 进入世界时处理「上线后提醒 / 每次上线提醒」，并重置游戏内时间的触发标记。 */
	public static void onLogin(Minecraft mc) {
		for (Task task : active()) {
			if (task.type != TaskType.TIMER) {
				continue;
			}
			switch (task.timerMode) {
				case ON_JOIN -> {
					if (!task.triggered) {
						task.triggered = true;
						TaskStore.get().save();
						complete(task);
					}
				}
				case EVERY_JOIN -> ClientUtil.remind(ClientUtil.t("notify.reminder", title(task)));
				case GAME_TIME -> {
					task.triggered = false;
					task.lastGameDay = -1L;
					TaskStore.get().save();
				}
				default -> {
				}
			}
		}
	}

	/** 登录后把所有成就类待办都问一遍服务端，补上「创建前就已经达成」的情况（review #22）。 */
	public static void queryAllAdvancements() {
		for (Task task : active()) {
			if (task.type == TaskType.ADVANCEMENT) {
				queryAdvancement(task.target);
			}
		}
	}
	/** 成就类待办保存后，向服务端查询这个成就是否已经达成（review #22）。 */
	public static void queryAdvancement(String advancementId) {
		if (advancementId == null || advancementId.isBlank()) {
			return;
		}
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToServer(
					new com.easytaskmgr.net.AdvancementQueryC2S(advancementId, ClientUtil.lang().code()));
		} catch (Exception ignored) {
			// 服务端没装模组时忽略
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
