package com.easytaskmgr.task;

import com.easytaskmgr.i18n.I18n;

import java.util.Locale;

/**
 * 任务类型：普通任务，以及四种「游戏内触发」的高级任务 + 时间提醒。
 */
public enum TaskType {
	NORMAL,
	COLLECT,
	ADVANCEMENT,
	COORDINATE,
	TIMER;

	public String label() {
		return I18n.t("type." + name().toLowerCase(Locale.ROOT));
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
