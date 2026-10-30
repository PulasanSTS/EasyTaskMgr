package com.easytaskmgr.task;

/**
 * 任务类型：普通任务，以及四种「游戏内触发」的高级任务 + 时间提醒。
 */
public enum TaskType {
	NORMAL("普通"),
	COLLECT("收集物品"),
	CRAFT("合成物品"),
	ADVANCEMENT("完成成就"),
	COORDINATE("前往坐标"),
	TIMER("时间提醒");

	private final String label;

	TaskType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public boolean isAdvanced() {
		return this != NORMAL;
	}

	public TaskType next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static TaskType byName(String name) {
		for (TaskType t : values()) {
			if (t.name().equalsIgnoreCase(name)) {
				return t;
			}
		}
		return NORMAL;
	}
}
