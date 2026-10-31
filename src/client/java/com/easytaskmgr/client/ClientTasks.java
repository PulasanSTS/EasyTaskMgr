package com.easytaskmgr.client;

import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import com.easytaskmgr.task.TimerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/**
 * 客户端任务逻辑：条件检测、完成判定、提醒文案。
 */
public final class ClientTasks {
	private static String flashText = "";
	private static long flashUntil;

	private ClientTasks() {
	}

	public static void tick(Minecraft mc) {
		List<Task> active = TaskStore.get().active();
		for (Task task : active) {
			if (task.remote) {
				continue;
			}
			switch (task.type) {
				case COLLECT -> tickCollect(mc, task);
				case ADVANCEMENT -> tickAdvancement(mc, task);
				case COORDINATE -> tickCoordinate(mc, task);
				case TIMER -> tickTimer(mc, task);
				case NORMAL -> {
				}
			}
		}
	}

	private static void tickCollect(Minecraft mc, Task task) {
		if (mc.player == null) {
			return;
		}
		int count = countItem(mc, task.target);
		if (task.includeContainers) {
			scanOpenContainer(mc, task);
			count += countInShulkers(mc, task.target);
			count += Math.max(0, task.containerCount);
		}
		if (count != task.progress) {
			task.progress = count;
			TaskStore.get().markDirty();
		}
		if (count >= Math.max(1, task.targetCount)) {
			complete(task, task.progressText());
		}
	}

	/** 记下打开过的容器里目标物品的数量（取见过的最大值）。 */
	private static void scanOpenContainer(Minecraft mc, Task task) {
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || mc.player == null) {
			return;
		}
		var menu = screen.getMenu();
		if (menu == mc.player.inventoryMenu) {
			return;
		}
		int found = countInMenu(menu, task.target);
		if (found > task.containerCount) {
			task.containerCount = found;
			TaskStore.get().markDirty();
		}
	}

	private static int countInMenu(net.minecraft.world.inventory.AbstractContainerMenu menu, String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) {
			return 0;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR) {
			return 0;
		}
		int total = 0;
		for (var slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** 背包里潜影盒内部的物品：读 {@code DataComponents.CONTAINER} 组件。 */
	private static int countInShulkers(Minecraft mc, String itemId) {
		if (mc.player == null) {
			return 0;
		}
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) {
			return 0;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR) {
			return 0;
		}
		int total = 0;
		for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
			if (contents == null) {
				continue;
			}
			NonNullList<ItemStack> inner = NonNullList.create();
			contents.copyInto(inner);
			for (ItemStack in : inner) {
				if (!in.isEmpty() && in.is(item)) {
					total += in.getCount();
				}
			}
		}
		return total;
	}

	private static void tickAdvancement(Minecraft mc, Task task) {
		if (mc.getConnection() == null) {
			return;
		}
		Identifier id = Identifier.tryParse(task.target);
		if (id == null) {
			return;
		}
		ClientAdvancements advancements = mc.getConnection().getAdvancements();
		var holder = advancements.get(id);
		if (holder == null) {
			return;
		}
		var progress = advancements.progress().get(holder);
		if (progress != null && progress.isDone()) {
			complete(task, I18n.t("hud.advancement", id));
		}
	}

	private static void tickCoordinate(Minecraft mc, Task task) {
		if (mc.player == null) {
			return;
		}
		double dist = horizontalDistance(mc, task);
		if (dist <= Math.max(1, task.radius)) {
			complete(task, I18n.t("notify.distance", dist));
		}
	}

	private static void tickTimer(Minecraft mc, Task task) {
		switch (task.timerMode) {
			case INTERVAL -> tickInterval(task);
			case GAME_TIME -> tickGameClock(mc, task);
			default -> {
				// ON_JOIN / EVERY_JOIN 在进入世界时处理
			}
		}
	}

	private static void tickInterval(Task task) {
		long now = System.currentTimeMillis();
		long interval = Math.max(1, task.intervalMinutes) * 60_000L;
		if (task.nextTriggerAt <= 0) {
			task.nextTriggerAt = now + interval;
			TaskStore.get().markDirty();
			return;
		}
		if (now >= task.nextTriggerAt) {
			task.nextTriggerAt = now + interval;
			TaskStore.get().markDirty();
			String notice = I18n.t("notify.reminder", task.displayText());
			Notifier.remind(notice);
			toast(notice);
		}
	}

	/**
	 * 游戏内时间提醒：按世界时钟的「日 + 当日 tick」判断是否到点。
	 *
	 * <p>MC 的 0 tick 对应 06:00，所以目标 tick = ((hour - 6 + 24) % 24) * 1000 + minute * 1000 / 60；
	 * 判定窗口 40 tick（约 2 秒），每 N 个游戏日才触发一次，并用 {@code lastGameDay} 去重。
	 */
	private static void tickGameClock(Minecraft mc, Task task) {
		if (mc.level == null) {
			return;
		}
		long total = mc.level.getOverworldClockTime();
		long day = total / 24000L;
		long dayTime = total % 24000L;
		if (task.lastGameDay >= day) {
			return;
		}
		if (day % Math.max(1, task.gameDayInterval) != 0) {
			return;
		}
		long target = ((long) ((task.gameHour - 6 + 24) % 24)) * 1000L + (long) task.gameMinute * 1000L / 60L;
		if (dayTime >= target && dayTime < target + 40) {
			task.lastGameDay = day;
			TaskStore.get().markDirty();
			String notice = I18n.t("notify.reminder", task.displayText());
			Notifier.remind(notice);
			toast(notice);
		}
	}

	public static void onJoinWorld(Minecraft mc) {
		for (Task task : TaskStore.get().active()) {
			if (task.type != TaskType.TIMER) {
				continue;
			}
			if (task.timerMode == TimerMode.ON_JOIN && !task.triggered) {
				task.triggered = true;
				String notice = I18n.t("notify.reminder", task.displayText());
				Notifier.remind(notice);
				toast(notice);
				TaskStore.get().complete(task);
			} else if (task.timerMode == TimerMode.EVERY_JOIN) {
				String notice = I18n.t("notify.reminder", task.displayText());
				Notifier.remind(notice);
				toast(notice);
			}
		}
	}

	public static int countItem(Minecraft mc, String itemId) {
		if (mc.player == null) {
			return 0;
		}
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) {
			return 0;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR) {
			return 0;
		}
		int total = 0;
		for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && stack.is(item)) {
				total += stack.getCount();
			}
		}
		ItemStack offhand = mc.player.getOffhandItem();
		if (!offhand.isEmpty() && offhand.is(item)) {
			total += offhand.getCount();
		}
		return total;
	}

	public static double horizontalDistance(Minecraft mc, Task task) {
		if (mc.player == null) {
			return Double.MAX_VALUE;
		}
		double dx = task.x - mc.player.getX();
		double dz = task.z - mc.player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static String reminderText(Task task) {
		if (task.remindText != null && !task.remindText.isBlank()) {
			return task.displayText() + " —— " + task.remindText;
		}
		return task.displayText();
	}

	private static void complete(Task task, String detail) {
		if (task.done) {
			return;
		}
		TaskStore.get().complete(task);
		String msg = (detail == null || detail.isBlank())
				? I18n.t("notify.completed", task.displayText())
				: I18n.t("notify.completed.detail", task.displayText(), detail);
		Notifier.success(msg);
		toast("✓ " + msg);
		// 附加提醒单独一行，方便看清是哪个任务的提醒
		if (task.remindText != null && !task.remindText.isBlank()) {
			Notifier.info(I18n.t("notify.extra", task.displayText(), task.remindText));
		}
	}

	public static void toast(String text) {
		flashText = text;
		flashUntil = System.currentTimeMillis() + 4500;
	}

	public static String flashText() {
		return System.currentTimeMillis() < flashUntil ? flashText : null;
	}

	public static void addQuick(String text, Priority priority) {
		Task task = new Task();
		task.text = text;
		task.setPriority(priority);
		TaskStore.get().add(task);
		Notifier.info(I18n.t("notify.added", text, priority.label()));
		toast("+ " + text);
		ClientNet.markDirty();
	}
}
