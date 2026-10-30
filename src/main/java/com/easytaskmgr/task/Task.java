package com.easytaskmgr.task;

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

	/** COLLECT/CRAFT: 物品 id；ADVANCEMENT: 成就 id。 */
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

	/** 触发时额外提示的文案，可为空。 */
	public String remindText = "";

	/** 运行期进度：已收集数量 / 已合成数量。 */
	public int progress;
	/** 一次性提醒（上线提醒、坐标到达）是否已经触发过。 */
	public boolean triggered;
	/** 下一次间隔提醒的时间戳。 */
	public long nextTriggerAt;

	/** 收集任务：是否把打开过的容器（箱子 / 末影箱 / 潜影盒）里的物品也算进去。 */
	public boolean includeContainers;
	/** 收集任务：已扫描到的容器内数量（取见过的最大值）。 */
	public int containerCount;

	/** 成就任务：是否在编辑界面显示前置条件与达成方式。 */
	public boolean showRequirements;

	/** 多人共享：任务拥有者名字（自己为空）。 */
	public String owner = "";
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
			case COLLECT, CRAFT -> Math.min(progress, targetCount) + "/" + targetCount;
			case ADVANCEMENT, COORDINATE -> triggered ? "已到达" : null;
			case TIMER -> timerMode == TimerMode.INTERVAL ? "每 " + intervalMinutes + " 分钟" : "上线时";
			case NORMAL -> null;
		};
	}

	/** 复制一条任务的展示信息，用于共享给队友。 */
	public Task copyForShare() {
		Task t = new Task();
		t.id = id;
		t.text = text;
		t.priority = priority;
		t.done = done;
		t.type = type;
		t.target = target;
		t.targetCount = targetCount;
		t.x = x;
		t.y = y;
		t.z = z;
		t.radius = radius;
		t.timerMode = timerMode;
		t.intervalMinutes = intervalMinutes;
		t.remindText = remindText;
		t.progress = progress;
		t.triggered = triggered;
		t.createdAt = createdAt;
		t.completedAt = completedAt;
		t.includeContainers = includeContainers;
		return t;
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
		if (targetCount < 1) {
			targetCount = 1;
		}
		if (intervalMinutes < 1) {
			intervalMinutes = 30;
		}
		if (radius < 1) {
			radius = 8;
		}
		if (containerCount < 0) {
			containerCount = 0;
		}
	}
}
