package com.easytaskmgr.task;

import com.easytaskmgr.i18n.I18n;

import java.util.UUID;

/**
 * 一条待办任务。
 *
 * <p>字段全部是 Gson 友好的基本类型 / 枚举 / 字符串，方便直接读写 JSON。
 */
public class Task {
	public String id = UUID.randomUUID().toString();
	public String text = "";

	/** 见 {@link Priority} 的 weight。 */
	public int priority = Priority.NORMAL.weight();

	public boolean done;
	public long createdAt = System.currentTimeMillis();
	public long completedAt;

	public TaskType type = TaskType.NORMAL;

	/** COLLECT: 物品 id；ADVANCEMENT: 成就 id。 */
	public String target = "";
	public int targetCount = 1;

	/** COORDINATE 目标点。 */
	public double x;
	public double y;
	public double z;
	/** COORDINATE 判定半径（格）。 */
	public int radius = 8;

	/** TIMER 配置。 */
	public TimerMode timerMode = TimerMode.ON_JOIN;
	public int intervalMinutes = 30;
	/** 游戏内时间提醒：小时（0-23）、分钟（0-59）、每 N 个游戏日。 */
	public int gameHour = 6;
	public int gameMinute = 0;
	public int gameDayInterval = 1;
	/** 上一次触发游戏时间提醒的游戏日，避免同一天重复触发。 */
	public long lastGameDay = -1L;
	/** 上一次检查时的世界时钟 tick（用于跨点检测，避免 rate 过快时跳过触发窗口）。 */
	public long lastClockTime = -1L;

	/** 触发时额外提示的文案，可为空。 */
	public String remindText = "";

	/** 运行期进度：已收集数量 / 已合成数量。 */
	public int progress;
	/** 一次性提醒（上线提醒、坐标到达）是否已经触发过。 */
	public boolean triggered;
	/** 下一次间隔提醒的时间戳。 */
	public long nextTriggerAt;

	/** 收集任务：是否把打开过的容器（箱子 / 末影箱 / 潜影盒）里的物品也算进去。 */
	/** 收集任务：已扫描到的容器内数量（取见过的最大值）。 */

	/** 成就任务：是否在编辑界面显示前置条件与达成方式。 */

	/** 多人共享：任务拥有者名字（自己为空）。 */
	public String owner = "";
	/** 0 = 仅个人，1 = 共享，2 = 特殊共享（需邀请）。 */
	public int visibility;
	/**
	 * 共享任务完成条件：0 任一认领者 / 1 所有认领者 / 2 任一（含发布者）/ 3 所有（含发布者）/
	 * 4 共同完成（收集任务，数量相加）。-1 表示「老存档没这个字段」，由 {@link #normalize()} 迁移。
	 */
	public int shareMode = -1;
	/** 老版本（rc2 及以前）的完成条件开关，只用于迁移到 {@link #shareMode}。 */
	public boolean shareAll;
	/** 客户端展示用：共享任务的服务端信息（不写入存档）。 */
	public transient String shareOwnerName = "";
	public transient boolean shareClaimed;
	public transient boolean shareCanClaim;
	public transient boolean shareMine;
	public transient boolean shareDone;
	/** 是否参与多人共享。 */
	public boolean shared = true;
	/** 是否为别人同步过来的只读任务。 */
	public boolean remote;

	public Priority priority() {
		return Priority.byWeight(priority);
	}

	public void setPriority(Priority p) {
		this.priority = p.weight();
	}

	public boolean isAdvanced() {
		return type.isAdvanced();
	}

	public boolean isRemote() {
		return remote;
	}

	/** HUD / 列表上显示的标题。 */
	public String displayText() {
		String base = text == null || text.isBlank() ? type.label() : text;
		return base;
	}

	/** 进度描述，例如 {@code 32/64}。没有进度的类型返回 null。 */
	public String progressText() {
		return switch (type) {
			case COLLECT -> Math.min(progress, targetCount) + "/" + targetCount;
			case ADVANCEMENT, COORDINATE -> triggered ? I18n.t("task.progress.reached") : null;
			case TIMER -> switch (timerMode) {
				case ON_JOIN -> I18n.t("task.progress.join");
				case EVERY_JOIN -> I18n.t("task.progress.everyJoin");
				case INTERVAL -> I18n.t("task.progress.interval", intervalMinutes);
				case GAME_TIME -> gameDayInterval <= 1
						? I18n.t("task.progress.gameTimeDaily", String.format("%02d:%02d", gameHour, gameMinute))
						: I18n.t("task.progress.gameTime", gameDayInterval, String.format("%02d:%02d", gameHour, gameMinute));
			};
			case NORMAL -> null;
		};
	}

	/** 兜底：JSON / 网络数据里出现 null 或越界值时，恢复到安全默认值。 */
	public void normalize() {
		if (text == null) {
			text = "";
		}
		if (type == null) {
			type = TaskType.NORMAL;
		}
		if (timerMode == null) {
			timerMode = TimerMode.ON_JOIN;
		}
		if (target == null) {
			target = "";
		}
		if (remindText == null) {
			remindText = "";
		}
		if (owner == null) {
			owner = "";
		}
		if (visibility < 0 || visibility > 2) {
			visibility = 0;
		}
		// 老存档只有 shareAll 布尔值：true → 所有（含发布者），false → 任一（含发布者），
		// 保持 rc2 的行为不变；新任务由编辑器写入 0..4 的显式值。
		if (shareMode < 0 || shareMode > 4) {
			shareMode = shareAll ? 3 : 2;
		}
		// 「共同完成」只对收集任务有意义：其它类型拿到 4 会让任务永远完不成，这里统一收回到默认值
		if (type != TaskType.COLLECT && shareMode == 4) {
			shareMode = 2;
		}
		if (targetCount < 1) {
			targetCount = 1;
		}
		if (intervalMinutes < 1) {
			intervalMinutes = 30;
		}
		if (radius < 1) {
			radius = 8;
		}
		gameHour = Math.max(0, Math.min(23, gameHour));
		gameMinute = Math.max(0, Math.min(59, gameMinute));
		gameDayInterval = Math.max(1, gameDayInterval);
	}
}
