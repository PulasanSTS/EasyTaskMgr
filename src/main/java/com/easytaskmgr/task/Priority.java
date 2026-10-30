package com.easytaskmgr.task;

/**
 * 任务优先级。数值越大越靠前，颜色用于 HUD / 列表显示。
 */
public enum Priority {
	LOW(0, "低", 0xFF9E9E9E),
	NORMAL(1, "普通", 0xFFFFFFFF),
	HIGH(2, "高", 0xFFFFC107),
	URGENT(3, "紧急", 0xFFFF5252);

	private final int weight;
	private final String label;
	private final int color;

	Priority(int weight, String label, int color) {
		this.weight = weight;
		this.label = label;
		this.color = color;
	}

	public int weight() {
		return weight;
	}

	public String label() {
		return label;
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
