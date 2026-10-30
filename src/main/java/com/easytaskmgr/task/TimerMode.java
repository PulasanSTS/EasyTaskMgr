package com.easytaskmgr.task;

/**
 * 时间提醒的两种模式。
 */
public enum TimerMode {
	ON_JOIN("上线后提醒"),
	INTERVAL("每隔一段时间提醒");

	private final String label;

	TimerMode(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public TimerMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static TimerMode byName(String name) {
		for (TimerMode m : values()) {
			if (m.name().equalsIgnoreCase(name)) {
				return m;
			}
		}
		return ON_JOIN;
	}
}
