package com.easytaskmgr.client.ui;

import com.easytaskmgr.client.AdvancementInfo;
import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.Suggestions;
import com.easytaskmgr.client.TextWrap;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务编辑器。
 *
 * <p>布局：标签在上、输入框在下；只有当前类型需要的字段才会创建。
 * 候选列表是可滚动的浮层（滚轮翻页、点击选中），最多同时显示 6 条。
 */
public class TaskEditScreen extends Screen {
	private static final int CONTENT_WIDTH = 300;
	private static final int MAX_VISIBLE = 6;
	private static final int ROW_H = 11;
	private static final int SEARCH_LIMIT = 40;

	private final Task task;
	private final boolean creating;
	private final Screen parent;

	private EditBox textBox;
	private EditBox targetBox;
	private EditBox countBox;
	private EditBox xBox;
	private EditBox yBox;
	private EditBox zBox;
	private EditBox intervalBox;
	private EditBox remindBox;

	private final List<Suggestions.Entry> suggestions = new ArrayList<>();
	private int scrollOffset;
	private boolean suppressRefresh;
	private int suggestionX;
	private int suggestionY;
	private int suggestionWidth;

	private int rowType;
	private int rowTarget;
	private int rowSuggest;
	private int rowExtra;
	private int rowRemind;
	private int rowButtons;

	public TaskEditScreen(Task task, boolean creating, Screen parent) {
		super(Component.literal(creating ? "新建待办" : "编辑待办"));
		this.task = task;
		this.creating = creating;
		this.parent = parent;
	}

	@Override
	protected void init() {
		task.normalize();
		suggestions.clear();
		scrollOffset = 0;
		layout();
		int x0 = width / 2 - CONTENT_WIDTH / 2;

		textBox = addRenderableWidget(new EditBox(font, x0, rowType - 18, CONTENT_WIDTH, 18, Component.literal("内容")));
		textBox.setMaxLength(120);
		textBox.setValue(task.text);

		addRenderableWidget(Button.builder(Component.literal("类型：" + task.type.label()), b -> {
			captureFields();
			task.type = task.type.next();
			rebuildWidgets();
		}).bounds(x0, rowType, 140, 18).build());

		addRenderableWidget(Button.builder(Component.literal("优先级：" + task.priority().label()), b -> {
			task.setPriority(task.priority().next());
			b.setMessage(Component.literal("优先级：" + task.priority().label()));
		}).bounds(x0 + 148, rowType, 140, 18).build());

		switch (task.type) {
			case COLLECT, CRAFT -> {
				targetBox = addRenderableWidget(new EditBox(font, x0, rowTarget, CONTENT_WIDTH - 70, 18, Component.literal("物品名")));
				targetBox.setMaxLength(120);
				targetBox.setValue(task.target.isEmpty() ? "" : Suggestions.nameOfItem(task.target));
				targetBox.setHint(Component.literal("物品名，如 钻石"));
				targetBox.setResponder(s -> refreshSuggestions(s, false));

				countBox = addRenderableWidget(new EditBox(font, x0 + CONTENT_WIDTH - 62, rowTarget, 62, 18, Component.literal("数量")));
				countBox.setMaxLength(6);
				countBox.setValue(Integer.toString(task.targetCount));
				countBox.setHint(Component.literal("数量"));

				addRenderableWidget(Button.builder(containerLabel(), b -> {
					task.includeContainers = !task.includeContainers;
					b.setMessage(containerLabel());
				}).bounds(x0, rowExtra, 170, 18).build());
			}
			case ADVANCEMENT -> {
				targetBox = addRenderableWidget(new EditBox(font, x0, rowTarget, CONTENT_WIDTH, 18, Component.literal("成就名")));
				targetBox.setMaxLength(160);
				targetBox.setValue(task.target.isEmpty() ? "" : Suggestions.nameOfAdvancement(minecraft, task.target));
				targetBox.setHint(Component.literal("成就名，如 来硬的"));
				targetBox.setResponder(s -> refreshSuggestions(s, true));

				if (com.easytaskmgr.config.ModConfig.get().advancementRequirements) {
					addRenderableWidget(Button.builder(requirementsLabel(), b -> {
						captureFields();
						task.showRequirements = !task.showRequirements;
						rebuildWidgets();
					}).bounds(x0, rowExtra, 170, 18).build());
				}
			}
			case COORDINATE -> {
				// x/y/z 各 70 宽，半径单独放在最右侧 68 宽，避免 z 输入框盖住半径
				int bw = 70;
				xBox = addRenderableWidget(new EditBox(font, x0, rowTarget, bw, 18, Component.literal("x")));
				xBox.setValue(fmt(task.x));
				xBox.setHint(Component.literal("x"));
				yBox = addRenderableWidget(new EditBox(font, x0 + bw + 6, rowTarget, bw, 18, Component.literal("y")));
				yBox.setValue(fmt(task.y));
				yBox.setHint(Component.literal("y"));
				zBox = addRenderableWidget(new EditBox(font, x0 + (bw + 6) * 2, rowTarget, bw, 18, Component.literal("z")));
				zBox.setValue(fmt(task.z));
				zBox.setHint(Component.literal("z"));

				countBox = addRenderableWidget(new EditBox(font, x0 + CONTENT_WIDTH - 68, rowTarget, 68, 18, Component.literal("半径")));
				countBox.setValue(Integer.toString(task.radius));
				countBox.setHint(Component.literal("半径"));
			}
			case TIMER -> {
				addRenderableWidget(Button.builder(Component.literal("提醒：" + task.timerMode.label()), b -> {
					captureFields();
					task.timerMode = task.timerMode.next();
					b.setMessage(Component.literal("提醒：" + task.timerMode.label()));
				}).bounds(x0, rowTarget, 170, 18).build());

				intervalBox = addRenderableWidget(new EditBox(font, x0 + 178, rowTarget, 62, 18, Component.literal("分钟")));
				intervalBox.setValue(Integer.toString(task.intervalMinutes));
				intervalBox.setHint(Component.literal("分钟"));
			}
			case NORMAL -> {
			}
		}

		remindBox = addRenderableWidget(new EditBox(font, x0, rowRemind, CONTENT_WIDTH, 18, Component.literal("附加提醒")));
		remindBox.setMaxLength(120);
		remindBox.setValue(task.remindText);
		remindBox.setHint(Component.literal("完成 / 触发时的额外提示，可留空"));
		if (com.easytaskmgr.config.ModConfig.get().advancementRequirements
				&& task.type == TaskType.ADVANCEMENT && task.showRequirements) {
			remindBox.visible = false;
		}

		addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
				.bounds(width / 2 - 100, rowButtons, 96, 20).build());
		addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
				.bounds(width / 2 + 4, rowButtons, 96, 20).build());
	}

	/**
	 * 固定分区，保证标签（上一行）和输入框（下一行）互不重叠，总高压进 240px：
	 * 内容 44-72 / 类型·优先级 72-90 / 目标 92-122 / 开关 128-146 / 附加提醒 148-178 / 按钮 190-210。
	 */
	private void layout() {
		rowType = 72;
		rowTarget = 104;
		rowSuggest = rowTarget + 22;
		rowExtra = 128;
		rowRemind = 160;
		rowButtons = Math.min(height - 26, 190);
	}

	private static String fmt(double v) {
		return String.format("%.1f", v);
	}

	private Component containerLabel() {
		return Component.literal("容器计数：" + (task.includeContainers ? "开" : "关"));
	}

	private Component requirementsLabel() {
		return Component.literal("显示前置条件：" + (task.showRequirements ? "开" : "关"));
	}

	private void refreshSuggestions(String text, boolean advancement) {
		if (suppressRefresh) {
			return;
		}
		suggestions.clear();
		scrollOffset = 0;
		if (text != null && !text.isBlank() && !text.contains(":")) {
			List<Suggestions.Entry> source = advancement ? Suggestions.advancements(minecraft) : Suggestions.items();
			suggestions.addAll(Suggestions.search(source, text, SEARCH_LIMIT));
		}
	}

	/** 把控件里的值写回 task（类型切换 / 刷新候选前调用，避免丢输入）。 */
	private void captureFields() {
		if (textBox != null) {
			task.text = textBox.getValue().trim();
		}
		if (remindBox != null) {
			task.remindText = remindBox.getValue().trim();
		}
		if (countBox != null) {
			int v = parseInt(countBox.getValue(), 0);
			if (v > 0) {
				if (task.type == TaskType.COORDINATE) {
					task.radius = v;
				} else {
					task.targetCount = v;
				}
			}
		}
		if (xBox != null) {
			task.x = parseDouble(xBox.getValue(), task.x);
		}
		if (yBox != null) {
			task.y = parseDouble(yBox.getValue(), task.y);
		}
		if (zBox != null) {
			task.z = parseDouble(zBox.getValue(), task.z);
		}
		if (intervalBox != null) {
			task.intervalMinutes = Math.max(1, parseInt(intervalBox.getValue(), task.intervalMinutes));
		}
	}

	private void save() {
		captureFields();
		String text = task.text == null ? "" : task.text.trim();
		if (text.isEmpty()) {
			ClientTasks.toast("内容不能为空");
			return;
		}
		task.text = text;

		switch (task.type) {
			case COLLECT, CRAFT -> {
				String resolved = resolveTarget(false);
				if (resolved.isEmpty()) {
					ClientTasks.toast("请填写物品名");
					return;
				}
				task.target = resolved;
				if (task.type == TaskType.CRAFT) {
					task.progress = 0;
				}
				task.triggered = false;
			}
			case ADVANCEMENT -> {
				String resolved = resolveTarget(true);
				if (resolved.isEmpty()) {
					ClientTasks.toast("请填写成就名");
					return;
				}
				task.target = resolved;
				AdvancementInfo.clearCache();
			}
			case COORDINATE -> {
				if (countBox != null) {
					task.radius = Math.max(1, parseInt(countBox.getValue(), task.radius));
				}
			}
			case TIMER -> {
				task.nextTriggerAt = 0;
				task.triggered = false;
			}
			case NORMAL -> {
			}
		}

		if (creating) {
			TaskStore.get().add(task);
		}
		TaskStore.get().save();
		ClientNet.markDirty();
		ClientTasks.toast(creating ? "已新建待办" : "已保存修改");
		onClose();
	}

	/** 输入框文本 → 规范 id；解析不出来但输入是 id 时直接用，否则保留原来的 id。 */
	private String resolveTarget(boolean advancement) {
		String text = targetBox == null ? "" : targetBox.getValue().trim();
		if (text.isEmpty()) {
			return task.target == null ? "" : task.target;
		}
		String resolved = advancement ? Suggestions.resolveAdvancement(minecraft, text) : Suggestions.resolveItem(text);
		if (resolved.equals(text) && !text.contains(":")) {
			return task.target == null ? text : task.target;
		}
		return resolved;
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private static double parseDouble(String value, double fallback) {
		try {
			return Double.parseDouble(value.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private int visibleRows() {
		return Math.min(MAX_VISIBLE, suggestions.size());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		// 26.3 里左键是 1（不是 GLFW 的 0），这里用 <=1 兼容两种约定
		if (!suggestions.isEmpty() && event.button() <= 1) {
			int rows = visibleRows();
			int end = Math.min(suggestions.size(), scrollOffset + rows);
			for (int i = scrollOffset; i < end; i++) {
				int y = suggestionY + (i - scrollOffset) * ROW_H;
				if (event.x() >= suggestionX && event.x() <= suggestionX + suggestionWidth
						&& event.y() >= y && event.y() < y + ROW_H - 1) {
					applySuggestion(suggestions.get(i));
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private void applySuggestion(Suggestions.Entry entry) {
		task.target = entry.id();
		suppressRefresh = true;
		try {
			if (targetBox != null) {
				targetBox.setValue(entry.name());
				targetBox.setCursorPosition(entry.name().length());
			}
		} finally {
			suppressRefresh = false;
		}
		suggestions.clear();
		scrollOffset = 0;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (!suggestions.isEmpty() && suggestions.size() > MAX_VISIBLE
				&& mouseX >= suggestionX && mouseX <= suggestionX + suggestionWidth
				&& mouseY >= suggestionY && mouseY <= suggestionY + MAX_VISIBLE * ROW_H) {
			int max = suggestions.size() - MAX_VISIBLE;
			scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(vertical)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101014);
		layout();
		int x0 = width / 2 - CONTENT_WIDTH / 2;

		graphics.centeredText(font, title, width / 2, 22, 0xFFFFD54F);
		graphics.text(font, "内容", x0, rowType - 28, 0xFFB0BEC5);

		switch (task.type) {
			case COLLECT, CRAFT -> {
				graphics.text(font, "物品名（支持中文，输入后下面出候选，滚轮翻页）", x0, rowTarget - 12, 0xFFB0BEC5);
				suggestionX = x0;
				suggestionY = rowSuggest;
				suggestionWidth = CONTENT_WIDTH - 70;
			}
			case ADVANCEMENT -> {
				graphics.text(font, "成就名（支持中文，输入后下面出候选，滚轮翻页）", x0, rowTarget - 12, 0xFFB0BEC5);
				suggestionX = x0;
				suggestionY = rowSuggest;
				suggestionWidth = CONTENT_WIDTH;
			}
			case COORDINATE -> graphics.text(font, "坐标 x / y / z 与到达半径", x0, rowTarget - 12, 0xFFB0BEC5);
			case TIMER -> graphics.text(font, "提醒方式与间隔（分钟）", x0, rowTarget - 12, 0xFFB0BEC5);
			case NORMAL -> graphics.text(font, "只记录事情，完成后在列表里手动勾选。", x0, rowTarget - 12, 0xFF80CBC4);
		}

		boolean showRequirements = com.easytaskmgr.config.ModConfig.get().advancementRequirements
				&& task.type == TaskType.ADVANCEMENT && task.showRequirements;
		if (!showRequirements) {
			graphics.text(font, "附加提醒", x0, rowRemind - 12, 0xFFB0BEC5);
		} else {
			drawRequirements(graphics, x0);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		// 候选列表画在控件之后，避免被下面的按钮盖住
		if (!suggestions.isEmpty()) {
			drawSuggestions(graphics, mouseX, mouseY);
		}
	}

	private void drawSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int rows = visibleRows();
		int top = suggestionY - 2;
		int bottom = suggestionY + rows * ROW_H + 2;
		graphics.fill(suggestionX - 2, top, suggestionX + suggestionWidth + 2, bottom, 0xF0101014);
		com.easytaskmgr.client.TaskHud.frame(graphics, suggestionX - 2, top,
				suggestionX + suggestionWidth + 2, bottom, 0x55FFFFFF);

		int end = Math.min(suggestions.size(), scrollOffset + rows);
		for (int i = scrollOffset; i < end; i++) {
			Suggestions.Entry entry = suggestions.get(i);
			int y = suggestionY + (i - scrollOffset) * ROW_H;
			boolean hover = mouseX >= suggestionX && mouseX <= suggestionX + suggestionWidth
					&& mouseY >= y && mouseY < y + ROW_H - 1;
			if (hover) {
				graphics.fill(suggestionX, y, suggestionX + suggestionWidth, y + ROW_H - 1, 0x55FFFFFF);
			}
			String line = entry.name() + "  " + entry.id();
			graphics.text(font, TextWrap.clip(font, line, suggestionWidth - 4), suggestionX + 2, y + 1,
					hover ? 0xFFFFFF80 : 0xFFB0BEC5);
		}

		if (suggestions.size() > MAX_VISIBLE) {
			String counter = (scrollOffset + 1) + "-" + end + " / " + suggestions.size();
			graphics.text(font, "▲", suggestionX + suggestionWidth - 10, suggestionY - 12, 0xFF90A4AE);
			graphics.text(font, counter, suggestionX + suggestionWidth - 60, suggestionY - 12, 0xFF90A4AE);
			graphics.text(font, "▼", suggestionX + suggestionWidth - 10, bottom + 2, 0xFF90A4AE);
		}
	}

	private void drawRequirements(GuiGraphicsExtractor graphics, int x0) {
		List<String> lines = AdvancementInfo.describe(minecraft, task.target);
		int y = 148;
		int maxLines = Math.max(2, (rowButtons - y) / 10);
		graphics.text(font, "前置条件 / 达成方式", x0, y, 0xFFFFD54F);
		y += 10;
		int shown = 0;
		for (String raw : lines) {
			for (String line : TextWrap.wrap(font, raw, CONTENT_WIDTH)) {
				if (shown >= maxLines - 1) {
					return;
				}
				graphics.text(font, line, x0, y, 0xFF9FB3C8);
				y += 10;
				shown++;
			}
		}
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
