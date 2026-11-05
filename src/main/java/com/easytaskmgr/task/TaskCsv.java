package com.easytaskmgr.task;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务与 CSV 文本的互转（issue #18：任务的导入 / 导出）。
 *
 * <p>列固定为：
 * {@code text, type, target, count, x, y, z, radius, timerMode, intervalMinutes,
 * gameHour, gameMinute, gameDayInterval, priority, done, remindText}。
 *
 * <p>写文件时字段按需加引号（含逗号、引号、换行时），解析时也支持带引号的字段，
 * 所以任务标题里带逗号或英文引号都不会把表格拆坏。
 * 这个类只做纯文本转换，不碰文件与游戏状态，方便单独测试。
 */
public final class TaskCsv {
	public static final String HEADER = "text,type,target,count,x,y,z,radius,timerMode,"
			+ "intervalMinutes,gameHour,gameMinute,gameDayInterval,priority,done,remindText";

	/** 导入结果：解析出的任务 + 跳过的行数。 */
	public record Result(List<Task> tasks, int skipped) {
	}

	private TaskCsv() {
	}

	public static String toCsv(List<Task> tasks) {
		StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
		for (Task task : tasks) {
			sb.append(row(
					task.text,
					task.type.name(),
					task.target,
					Integer.toString(task.targetCount),
					fmt(task.x),
					fmt(task.y),
					fmt(task.z),
					Integer.toString(task.radius),
					task.timerMode.name(),
					Integer.toString(task.intervalMinutes),
					Integer.toString(task.gameHour),
					Integer.toString(task.gameMinute),
					Integer.toString(task.gameDayInterval),
					Integer.toString(task.priority),
					Boolean.toString(task.done),
					task.remindText)).append("\r\n");
		}
		return sb.toString();
	}

	public static Result fromCsv(String text) {
		List<Task> tasks = new ArrayList<>();
		int skipped = 0;
		List<List<String>> rows = parse(text);
		boolean first = true;
		for (List<String> row : rows) {
			// 跳过表头（也兼容用户自己删掉表头的文件）
			if (first) {
				first = false;
				if (!row.isEmpty() && row.get(0).trim().equalsIgnoreCase("text")) {
					continue;
				}
			}
			Task task = toTask(row);
			if (task == null) {
				if (!row.isEmpty() && !row.get(0).isBlank()) {
					skipped++;
				}
				continue;
			}
			tasks.add(task);
		}
		return new Result(tasks, skipped);
	}

	private static Task toTask(List<String> row) {
		if (row.isEmpty()) {
			return null;
		}
		Task task = new Task();
		task.text = field(row, 0).trim();
		if (task.text.isEmpty()) {
			return null;
		}
		task.type = TaskType.byName(field(row, 1).trim());
		task.target = field(row, 2).trim();
		task.targetCount = Math.max(1, intOr(field(row, 3), 1));
		task.x = doubleOr(field(row, 4), 0);
		task.y = doubleOr(field(row, 5), 0);
		task.z = doubleOr(field(row, 6), 0);
		task.radius = Math.max(1, intOr(field(row, 7), 8));
		task.timerMode = TimerMode.byName(field(row, 8).trim());
		task.intervalMinutes = Math.max(1, intOr(field(row, 9), 30));
		task.gameHour = intOr(field(row, 10), 6);
		task.gameMinute = intOr(field(row, 11), 0);
		task.gameDayInterval = Math.max(1, intOr(field(row, 12), 1));
		task.priority = priority(field(row, 13));
		task.done = "true".equalsIgnoreCase(field(row, 14).trim());
		task.remindText = field(row, 15).trim();
		// 收集 / 坐标 / 成就任务缺关键字段时视为坏行，避免导入一堆空任务
		switch (task.type) {
			case COLLECT, ADVANCEMENT -> {
				if (task.target.isEmpty()) {
					return null;
				}
			}
			default -> {
			}
		}
		task.normalize();
		return task;
	}

	private static int priority(String raw) {
		String value = raw.trim();
		if (value.isEmpty()) {
			return Priority.NORMAL.weight();
		}
		try {
			return Priority.byWeight(Integer.parseInt(value)).weight();
		} catch (NumberFormatException ignored) {
			return Priority.byName(value).weight();
		}
	}

	private static String field(List<String> row, int index) {
		return index < row.size() ? row.get(index) : "";
	}

	private static String row(String... values) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(escape(values[i]));
		}
		return sb.toString();
	}

	private static String escape(String value) {
		String v = value == null ? "" : value;
		if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf('\r') < 0) {
			return v;
		}
		return '"' + v.replace("\"", "\"\"") + '"';
	}

	/** 逐字符解析，支持引号内的逗号、换行与转义引号。 */
	private static List<List<String>> parse(String text) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		boolean any = false;
		String src = text == null ? "" : text;
		// 去掉 Excel 常加的 UTF-8 BOM
		if (!src.isEmpty() && src.charAt(0) == '\uFEFF') {
			src = src.substring(1);
		}
		for (int i = 0; i < src.length(); i++) {
			char c = src.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < src.length() && src.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					cell.append(c);
				}
				any = true;
				continue;
			}
			switch (c) {
				case '"' -> {
					quoted = true;
					any = true;
				}
				case ',' -> {
					row.add(cell.toString());
					cell.setLength(0);
					any = true;
				}
				case '\r' -> {
				}
				case '\n' -> {
					if (any || cell.length() > 0 || !row.isEmpty()) {
						row.add(cell.toString());
						cell.setLength(0);
						rows.add(row);
						row = new ArrayList<>();
						any = false;
					}
				}
				default -> {
					cell.append(c);
					any = true;
				}
			}
		}
		if (any || cell.length() > 0 || !row.isEmpty()) {
			row.add(cell.toString());
			rows.add(row);
		}
		return rows;
	}

	private static String fmt(double value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	private static int intOr(String raw, int fallback) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private static double doubleOr(String raw, double fallback) {
		try {
			return Double.parseDouble(raw.trim());
		} catch (Exception e) {
			return fallback;
		}
	}
}
