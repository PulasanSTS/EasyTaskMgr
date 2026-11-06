package com.easytaskmgr.i18n;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 极简 i18n：内置中英两套文案，按「配置开关 + MC 语言」选择。
 *
 * <p>规则：配置为 {@code auto} 时读取 Minecraft 的语言设置，只有 {@code zh_cn} 走中文，
 * 其余语言（含 en_us 与未翻译语言）一律回退英文；也可以在设置里强制指定语言。
 */
public final class I18n {
	public enum Lang {
		ZH_CN, EN_US;

		public static Lang byCode(String code) {
			if (code == null) {
				return EN_US;
			}
			return code.toLowerCase(Locale.ROOT).startsWith("zh") ? ZH_CN : EN_US;
		}

		public String code() {
			return this == ZH_CN ? "zh_cn" : "en_us";
		}

		public Lang next() {
			return this == ZH_CN ? EN_US : ZH_CN;
		}
	}

	private static final Map<String, String[]> STRINGS = new HashMap<>();
	private static Lang current = Lang.ZH_CN;

	private I18n() {
	}

	public static Lang lang() {
		return current;
	}

	public static void set(Lang lang) {
		current = lang == null ? Lang.EN_US : lang;
	}

	public static String t(String key) {
		String[] pair = STRINGS.get(key);
		if (pair == null) {
			return key;
		}
		return current == Lang.ZH_CN ? pair[0] : pair[1];
	}

	public static String t(String key, Object... args) {
		return String.format(t(key), args);
	}

	/** 指定语言取文案（服务端给不同语言的玩家生成内容时用）。 */
	public static String t(Lang lang, String key, Object... args) {
		String[] pair = STRINGS.get(key);
		if (pair == null) {
			return key;
		}
		String text = lang == Lang.ZH_CN ? pair[0] : pair[1];
		return args == null || args.length == 0 ? text : String.format(text, args);
	}

	private static void put(String key, String zh, String en) {
		STRINGS.put(key, new String[]{zh, en});
	}

	static {
		// 优先级
		put("prio.low", "低", "Low");
		put("prio.normal", "普通", "Normal");
		put("prio.high", "高", "High");
		put("prio.urgent", "紧急", "Urgent");

		// 任务类型
		put("type.normal", "普通", "Normal");
		put("type.collect", "收集物品", "Collect items");
		
		put("type.advancement", "完成成就", "Advancement");
		put("type.coordinate", "前往坐标", "Go to coordinates");
		put("type.timer", "时间提醒", "Timed reminder");

		// 计时模式
		put("timer.join", "上线后提醒", "Remind me when I join");
		put("timer.interval", "每隔一段时间提醒", "Remind me every so often");
		put("timer.everyJoin", "每次上线时提醒", "Every time I join");
		put("timer.gameTime", "游戏内时间", "In-game time");

		// 任务进度文案
		put("task.progress.interval", "每 %d 分钟", "every %d min");
		put("task.progress.join", "上线时", "on join");
		put("task.progress.everyJoin", "每次上线", "every join");
		put("task.progress.gameTime", "每 %d 天 %s", "every %d days at %s");
		put("task.progress.gameTimeDaily", "每天 %s", "daily at %s");
		put("task.progress.reached", "已到达", "Reached");

		// 通知
		put("notify.added", "已添加：%s（%s）", "Added: %s (%s)");
		put("notify.completed", "任务完成：%s", "Task completed: %s");
		put("notify.completed.detail", "任务完成：%s（%s）", "Task completed: %s (%s)");
		put("notify.extra", "完成「%s」任务的附加提醒：%s", "Note for \"%s\": %s");
		put("notify.reminder", "任务提醒：%s", "Reminder: %s");
		put("notify.done", "已完成：%s", "Completed: %s");
		put("notify.hud.hidden", "HUD 已隐藏", "HUD hidden");
		put("notify.hud.shown", "HUD 已显示", "HUD shown");
		put("notify.interact.on", "HUD 交互：点击普通任务完成，按 V 退出",
				"HUD mode: click a task to complete, press V to exit");
		put("notify.interact.off", "已退出 HUD 交互", "HUD mode off");

		// HUD
		put("hud.title", "待办清单 %d", "Todos %d");
		put("hud.hint", "点击任务即可完成 / 认领 · 按 %s 退出", "Click a task to complete or claim · %s to exit");
		put("hud.more", "…还有 %d 条", "…and %d more");
		put("hud.advancement", "成就 %s", "Advancement %s");
		put("dir.front", "正前", "ahead");
		put("dir.frontRight", "右前", "front-right");
		put("dir.frontLeft", "左前", "front-left");
		put("dir.right", "右侧", "right");
		put("dir.left", "左侧", "left");
		put("dir.backRight", "右后", "back-right");
		put("dir.backLeft", "左后", "back-left");
		put("dir.back", "正后", "behind");
		put("notify.distance", "距离 %.0f 格", "%.0f blocks away");

		// 通用按钮 / 开关
		put("ui.on", "开", "On");
		put("ui.off", "关", "Off");
		put("ui.cancel", "取消", "Cancel");
		put("ui.save", "保存", "Save");
		put("ui.close", "关闭", "Close");
		put("ui.settings", "设置", "Settings");
		put("ui.new", "新增", "New");
		put("ui.edit", "改", "Edit");
		put("ui.delete", "×", "×");
		put("ui.showDone", "显示已完成", "Show done");
		put("ui.hideDone", "隐藏已完成", "Hide done");
		put("ui.clearDone", "清空已完成", "Clear done");
		put("ui.priority", "优先级：%s", "Priority: %s");
		put("ui.type", "类型：%s", "Type: %s");
		put("ui.timer", "提醒：%s", "Reminder: %s");

		// 快速添加
		put("quick.title", "快速添加待办", "Quick add");
		put("quick.hint", "输入内容后按 Enter 直接添加；Tab 切换按钮",
				"Type and press Enter to add; Tab moves focus");
		put("quick.add", "添加", "Add");
		put("quick.more", "更多选项…", "More options…");
		put("quick.placeholder", "要做的事…", "What to do…");

		// 列表
		put("list.title", "待办列表", "Task list");
		put("list.range", "%d-%d / 共 %d 条", "%d-%d of %d");
		put("list.scrollHint", "滚轮滚动", "scroll with wheel");
		put("list.empty", "还没有待办", "No tasks yet");
		put("list.sharedCount", " · 共享 %d", " · shared %d");
		put("share.tagMine", "[我的共享]", "[mine]");
		put("share.tagClaimed", "[已认领]", "[claimed]");
		put("share.tagClaim", "[可认领]", "[claimable]");
		put("share.tagLocked", "[未受邀]", "[locked]");
		put("share.claimedToast", "已认领：%s", "Claimed: %s");
		put("edit.visibility", "可见性：%s", "Visibility: %s");
		put("vis.personal", "仅个人", "Personal");
		put("vis.shared", "共享", "Shared");
		put("vis.special", "特殊共享", "Special");
		put("share.published", "已发布共享任务", "Shared task published");
		put("edit.mode", "完成条件：%s", "Completion: %s");
		put("share.mode.anyClaimant", "任一认领者", "any claimant");
		put("share.mode.allClaimants", "所有认领者", "all claimants");
		put("share.mode.anyWithCreator", "任一（含发布者）", "any (incl. creator)");
		put("share.mode.allWithCreator", "所有（含发布者）", "all (incl. creator)");
		put("share.mode.combined", "共同完成（数量相加）", "combined count (only for collect tasks)");
		put("share.progress", "%d/%d 已完成", "%d/%d done");
		put("share.waitOthers", "你已完成，等其他人完成", "You're done — waiting for others");
		put("share.partialToast", "已记录你的完成（%d/%d）", "Your part is recorded (%d/%d)");
		put("share.tagWaiting", "[待他人完成]", "[waiting others]");
		put("share.invitedCount", "已邀请 %s 认领 %d 个特殊共享任务", "Invited %s to %d special task(s)");
		put("share.inviteNone", "你没有特殊共享任务可邀请", "You have no special shared tasks to invite");
		put("share.noServer", "服务端未安装本模组，共享功能不可用", "Server does not have the mod — sharing unavailable");
		put("share.revoke", "×", "×");
		put("share.revoked", "已撤回共享任务", "Shared task revoked");
		put("share.outdatedServer", "服务端的模组版本较旧（低于 rc4），共享任务的完成条件可能不生效，请更新服务端",
				"The server's mod is older than rc4 — shared completion rules may not apply, please update the server");
		put("edit.noShare", "该类型（成就 / 时间提醒）不支持共享任务",
				"This task type (advancement / timer) cannot be shared");

		// 任务 CSV 导入 / 导出（issue #18）
		put("csv.export", "导出 CSV", "Export");
		put("csv.import", "导入 CSV", "Import");
		put("csv.exported", "已导出 %d 条任务到 %s", "Exported %d task(s) to %s");
		put("csv.missing", "找不到 %s，请先导出一份", "%s not found — export one first");
		put("csv.failed", "CSV 操作失败，详见日志", "CSV operation failed — see the log");
		put("csv.importedMixed", "导入 %d 条任务：共享 %d 条、本地 %d 条", "Imported %d task(s): %d shared, %d local");
		put("csv.downgradedSuffix", "（%d 条因服务端未装模组或类型不支持，已降级为本地任务）",
				" (%d downgraded to local: no server mod, or the type cannot be shared)");
		put("csv.skippedSuffix", "（跳过 %d 行）", " (%d row(s) skipped)");
		put("list.donePrefix", "[已完成] ", "[Done] ");
		put("list.footer", "%s 快速添加 · %s 列表 · %s 设置 · %s 隐藏 HUD",
				"%s quick add · %s list · %s settings · %s hide HUD");

		// 编辑
		put("edit.new", "新建待办", "New task");
		put("edit.edit", "编辑待办", "Edit task");
		put("edit.content", "内容", "Content");
		put("edit.itemHint", "物品名（支持中文，输入后下面出候选，滚轮翻页）",
				"Item name (suggestions below, scroll to page)");
		put("edit.advHint", "成就名（支持中文，输入后下面出候选，滚轮翻页）",
				"Advancement name (suggestions below, scroll to page)");
		put("edit.coordHint", "坐标 x / y / z 与到达半径", "Coordinates x / y / z and radius");
		put("edit.timerHint", "提醒方式与间隔（分钟）", "Reminder mode and interval (minutes)");
		put("edit.normalHint", "只记录事情，完成后在列表里手动勾选。",
				"Just a note; tick it off in the list when done.");
		put("edit.remind", "附加提醒", "Extra note");
		put("edit.remindHint", "完成 / 触发时的额外提示，可留空", "Shown on completion, optional");
		put("edit.itemPlaceholder", "物品名，如 钻石", "Item name, e.g. diamond");
		put("edit.advPlaceholder", "成就名，如 来硬的", "Advancement, e.g. Diamonds!");
		put("edit.count", "数量", "Count");
		put("edit.radius", "半径", "Radius");
		put("edit.minutes", "分钟", "Minutes");
		put("edit.intervalHint", "（至少 1 分钟）", " (min 1)");
		put("edit.hour", "时", "HH");
		put("edit.minute", "分", "MM");
		put("edit.days", "天", "days");
		put("edit.presetDay", "白天 06:00", "Day 06:00");
		put("edit.presetNight", "黑夜 19:00", "Night 19:00");
		put("edit.gameTimeHint", "游戏内时间：小时 / 分钟 / 每 N 天", "In-game time: hour / minute / every N days");
		put("edit.empty", "内容不能为空", "Content cannot be empty");
		put("edit.needItem", "请填写物品名", "Please enter an item name");
		put("edit.needAdv", "请填写成就名", "Please enter an advancement name");
		put("edit.created", "已新建待办", "Task created");
		put("edit.saved", "已保存修改", "Saved");

		// 设置
		put("cfg.title", "EasyTaskMgr 设置", "EasyTaskMgr Settings");
		put("cfg.hint", "按住右侧 HUD 预览框拖动即可移动位置",
				"Drag the HUD preview on the right to move it");
		put("cfg.posX", "位置 X", "Position X");
		put("cfg.posY", "位置 Y", "Position Y");
		put("cfg.width", "HUD 宽度", "HUD width");
		put("cfg.limit", "最多显示", "Max lines");
		put("cfg.px", " px", " px");
		put("cfg.items", "%d 条", "%d");
		put("cfg.bg", "背景", "Background");
		put("cfg.dist", "距离", "Distance");
		put("cfg.speed", "速度", "Speed");
		put("cfg.team", "队友", "Teammates");
		put("cfg.sound", "音效", "Sound");
		put("cfg.reset", "重置位置", "Reset position");
		put("cfg.done", "完成", "Done");
		put("cfg.language", "语言", "Language");
		put("cfg.lang.auto", "自动", "Auto");
		put("cfg.lang.zh", "简体中文", "简体中文");
		put("cfg.lang.en", "English", "English");
		put("cfg.sample", "%d. 示例待办", "%d. Sample task");

		// 成就条件（服务端描述器）
		put("adv.obtainItem", "获得物品", "Obtain items");
		put("adv.obtain", "获得 %s", "Obtain %s");
		put("adv.consume", "吃掉 %s", "Consume %s");
		put("adv.consumeAny", "吃掉物品", "Consume an item");
		put("adv.enter", "进入 %s", "Enter %s");
		put("adv.enterAny", "进入特定方块", "Enter a specific block");
		put("adv.craft.ingredients", "按配方合成（材料：%s 等）", "Craft a recipe (ingredients: %s …)");
		put("adv.craft", "按配方合成指定物品", "Craft a specific item");
		put("adv.trade", "与村民交易 %s", "Trade with a villager for %s");
		put("adv.tradeAny", "与村民交易", "Trade with a villager");
		put("adv.enchant", "附魔 %s", "Enchant %s");
		put("adv.enchantAny", "附魔物品", "Enchant an item");
		put("adv.kill", "击杀生物", "Kill a mob");
		put("adv.dimension", "前往其他维度", "Travel to another dimension");
		put("adv.fish", "钓鱼", "Fish");
		put("adv.breed", "繁殖动物", "Breed animals");
		put("adv.cure", "治愈僵尸村民", "Cure a zombie villager");
		put("adv.beacon", "搭建信标", "Build a beacon");
		put("adv.levitate", "飘浮", "Levitate");
		put("adv.eye", "使用末影之眼", "Use an eye of ender");
		put("adv.crossbow", "用弩射击", "Shoot a crossbow");
		put("adv.recipeUnlock", "解锁配方", "Unlock a recipe");
		put("adv.player", "满足特定条件", "Meet a condition");
		put("adv.location", "到达指定位置", "Reach a location");
		put("adv.item", "物品", "items");
		put("adv.count", "%d 个 %s", "%d × %s");
		put("adv.or", " 或 ", " or ");
		put("adv.unknown", "完成条件由数据包定义", "Condition defined by a data pack");
		put("adv.notFound", "未找到该成就", "Advancement not found");

		// 成就信息面板
		put("info.title", "标题：%s", "Title: %s");
		put("info.desc", "描述：%s", "Description: %s");
		put("info.parent", "前置成就：%s", "Parent: %s");
		put("info.noParent", "无（根成就）", "None (root)");
		put("info.how", "达成方式：", "How to earn:");
		put("info.querying", "正在向服务端查询这个成就…（如果一直没有结果，说明服务端没装模组或该成就不存在）",
				"Asking the server for this advancement… (if it never resolves, the server may not have the mod)");
	}
}