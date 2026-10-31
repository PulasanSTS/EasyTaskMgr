package com.easytaskmgr.task;

import com.easytaskmgr.i18n.I18n;

import java.util.Locale;

/**
 * 任务优先级。数值越大越靠前，颜色用于 HUD / 列表显示。
 */
public enum Priority {
	LOW(0, 0xFF9E9E9E),
	NORMAL(1, 0xFFFFFFFF),
	HIGH(2, 0xFFFFC107),
	URGENT(3, 0xFFFF5252);

	private final int weight;
	private final int color;

	Priority(int weight, int color) {
		this.weight = weight;
		this.color = color;
	}

	public int weight() {
		return weight;
	}

	public String label() {
		return I18n.t("prio." + name().toLowerCase(Locale.ROOT));
	}

	public int color() {
		return color;
	}

	public Priority next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static Priority byWeight(int weight) {
		for (Priority p : values()) {
			if (p.weight == weight) {
				return p;
			}
		}
		return NORMAL;
	}

	public static Priority byName(String name) {
		for (Priority p : values()) {
			if (p.name().equalsIgnoreCase(name)) {
				return p;
			}
		}
		return NORMAL;
	}
}
