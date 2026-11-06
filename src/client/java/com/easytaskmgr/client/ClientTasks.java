package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskCsv;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import com.easytaskmgr.task.TimerMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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
		if (count != task.progress) {
			task.progress = count;
			TaskStore.get().markDirty();
		}
		if (count >= Math.max(1, task.targetCount)) {
			complete(task, task.progressText());
		}
	}

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
			// 用 nonEmptyItemCopyStream 而不是 copyInto：copyInto 需要目标列表已经按容量建好，
			// 传空列表进去会什么都拷不出来（issue #19 的另一个原因）
			for (ItemStack in : contents.nonEmptyItemCopyStream().toList()) {
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
		int period = Math.max(2000, com.easytaskmgr.config.ModConfig.get().ticksPerDay);
		long day = total / period;
		long dayTime = total % period;
		long previous = task.lastClockTime;
		task.lastClockTime = total;
		if (previous < 0 || total < previous) {
			return;
		}
		long prevDayTime = previous % period;
		if (task.lastGameDay >= day) {
			return;
		}
		if (day % Math.max(1, task.gameDayInterval) != 0) {
			return;
		}
		long target = ((long) ((task.gameHour - 6 + 24) % 24)) * period / 24L
				+ (long) task.gameMinute * period / 1440L;
		// 跨点检测：本次检查跨过 target 就触发，rate 再快也不会跳过整个窗口
		boolean crossed = dayTime >= prevDayTime
				? (prevDayTime < target && target <= dayTime)
				: (target > prevDayTime || target <= dayTime);
		if (crossed) {
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

	/** 物品 id → 玩家语言下的物品名（issue #3：HUD 上要显示「要收集什么」）。 */
	public static String itemName(String itemId) {
		Identifier id = itemId == null ? null : Identifier.tryParse(itemId);
		if (id == null) {
			return itemId == null ? "" : itemId;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR) {
			return id.getPath();
		}
		try {
			return new ItemStack(item).getHoverName().getString();
		} catch (Exception e) {
			return id.getPath();
		}
	}

	/**
	 * 成就 id → 玩家语言下的成就名（issue #5：HUD 不能显示注册名）。
	 *
	 * <p>优先用客户端自己的成就注册表（联机时服务端只同步有进度的成就，可能查不到），
	 * 查不到再退回服务端下发的成就目录。
	 */
	public static String advancementName(Minecraft mc, String advancementId) {
		if (advancementId == null || advancementId.isBlank()) {
			return "";
		}
		Identifier id = Identifier.tryParse(AdvancementCatalog.normalize(advancementId));
		if (id != null && mc.getConnection() != null) {
			try {
				var holder = mc.getConnection().getAdvancements().get(id);
				if (holder != null) {
					return net.minecraft.advancements.Advancement.name(holder).getString();
				}
			} catch (Exception ignored) {
				// 回退到目录
			}
		}
		return AdvancementCatalog.titleOf(advancementId);
	}

	/**
	 * 统计背包里某物品的总数：背包 + 副手 + **背包里潜影盒的内容**。
	 *
	 * <p>潜影盒内容这条以前只写了 {@link #countInShulkers} 却没接上，
	 * 导致「潜影盒里的物品不算数」（issue #19）；现在统一在这里加回来，
	 * 本地任务和共享任务（共同完成上报）用的是同一份统计。
	 */
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
		// 背包里潜影盒的内容也算（issue #19）
		total += countInShulkers(mc, itemId);
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

	/** 供 HUD / 列表点击完成时调用：统一走这里，附加提醒才会一起输出。 */
	public static void finish(Task task) {
		complete(task, null);
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

	/** CSV 导入 / 导出用的固定文件位置：{@code config/easytaskmgr-tasks.csv}。 */
	public static Path csvFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("easytaskmgr-tasks.csv");
	}

	/** 把当前全部任务（含已完成）导出成 CSV（issue #18）。 */
	public static String exportCsv() {
		Path path = csvFile();
		try {
			Files.createDirectories(path.getParent());
			List<Task> all = TaskStore.get().all();
			// 带 BOM，Excel 打开中文才不会乱码
			Files.writeString(path, "\uFEFF" + TaskCsv.toCsv(all), StandardCharsets.UTF_8);
			String msg = I18n.t("csv.exported", all.size(), path.getFileName().toString());
			Notifier.info(msg);
			EasyTaskMgr.LOGGER.info("导出 {} 条任务到 {}", all.size(), path);
			return msg;
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.error("导出 CSV 失败：{}", path, e);
			String msg = I18n.t("csv.failed");
			Notifier.warn(msg);
			return msg;
		}
	}

	/** 从 CSV 追加任务；已存在的任务不动，坏行会跳过并计数。 */
	/**
	 * 从 CSV 追加任务；坏行跳过。
	 *
	 * <p>带 `visibility` 列且 > 0 的行会走共享发布（issue #21 第 2 点）：
	 * 服务端装了同版本模组时直接发布；没装、或者类型是成就 / 时间提醒时降级成本地任务并计数。
	 * 一次导入的共享任务超过 3 条时，服务端会把发布提示合并成一条（见 {@code SharedTasks} 的批处理）。
	 */
	public static String importCsv() {
		Path path = csvFile();
		if (!Files.isRegularFile(path)) {
			String msg = I18n.t("csv.missing", path.getFileName().toString());
			Notifier.warn(msg);
			return msg;
		}
		try {
			String text = Files.readString(path, StandardCharsets.UTF_8);
			TaskCsv.Result result = TaskCsv.fromCsv(text);
			int local = 0;
			int shared = 0;
			int downgraded = 0;
			for (Task task : result.tasks()) {
				// 重新生成 id，重复导入同一份文件不会互相覆盖
				task.id = UUID.randomUUID().toString();
				task.normalize();
				boolean canShare = task.type != TaskType.ADVANCEMENT && task.type != TaskType.TIMER;
				if (task.visibility > 0 && canShare && ClientShared.available()) {
					ClientShared.publish(task);
					shared++;
					continue;
				}
				if (task.visibility > 0) {
					// 服务端没装模组 / 该类型不支持共享：降级为本地任务
					downgraded++;
				}
				task.visibility = 0;
				TaskStore.get().add(task);
				local++;
			}
			TaskStore.get().save();
			ClientNet.markDirty();
			String msg = I18n.t("csv.importedMixed", local + shared, shared, local);
			if (downgraded > 0) {
				msg += I18n.t("csv.downgradedSuffix", downgraded);
			}
			if (result.skipped() > 0) {
				msg += I18n.t("csv.skippedSuffix", result.skipped());
			}
			Notifier.info(msg);
			EasyTaskMgr.LOGGER.info("从 {} 导入 {} 条任务（共享 {}、本地 {}、降级 {}、跳过 {} 行）",
					path, local + shared, shared, local, downgraded, result.skipped());
			return msg;
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.error("导入 CSV 失败：{}", path, e);
			String msg = I18n.t("csv.failed");
			Notifier.warn(msg);
			return msg;
		}
	}
}

