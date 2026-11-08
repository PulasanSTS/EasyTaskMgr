package com.easytaskmgr.task;

import com.easytaskmgr.i18n.I18n;

/**
 * 时间提醒的几种模式。
 */
public enum TimerMode {
	/** 上线后提醒一次（提醒完自动完成）。 */
	ON_JOIN,
	/** 每次上线都提醒（不自动完成）。 */
	EVERY_JOIN,
	/** 每隔 N 分钟提醒一次。 */
	INTERVAL,
	/** 游戏内时间提醒（每天 HH:MM，可设置每 N 个游戏日）。 */
	GAME_TIME;

	public String label() {
		return switch (this) {
			case ON_JOIN -> I18n.t("timer.join");
			case EVERY_JOIN -> I18n.t("timer.everyJoin");
			case INTERVAL -> I18n.t("timer.interval");
			case GAME_TIME -> I18n.t("timer.gameTime");
		};
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
