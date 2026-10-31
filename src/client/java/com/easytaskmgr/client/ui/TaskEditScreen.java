package com.easytaskmgr.client.ui;

import com.easytaskmgr.client.AdvancementInfo;
import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.client.ClientShared;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.Suggestions;
import com.easytaskmgr.client.TextWrap;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import com.easytaskmgr.task.TimerMode;
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
	private EditBox hourBox;
	private EditBox minuteBox;
	private EditBox daysBox;
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
	private int rowShare;
	private int rowButtons;

	public TaskEditScreen(Task task, boolean creating, Screen parent) {
		super(Component.literal(creating ? I18n.t("edit.new") : I18n.t("edit.edit")));
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

		textBox = addRenderableWidget(new EditBox(font, x0, rowType - 18, CONTENT_WIDTH, 18, Component.literal(I18n.t("edit.content"))));
		textBox.setMaxLength(120);
		textBox.setValue(task.text);

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.type", task.type.label())), b -> {
			captureFields();
			task.type = task.type.next();
			rebuildWidgets();
		}).bounds(x0, rowType, 140, 18).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.priority", task.priority().label())), b -> {
			task.setPriority(task.priority().next());
			b.setMessage(Component.literal(I18n.t("ui.priority", task.priority().label())));
		}).bounds(x0 + 148, rowType, 140, 18).build());

		switch (task.type) {
			case COLLECT -> {
				targetBox = addRenderableWidget(new EditBox(font, x0, rowTarget, CONTENT_WIDTH - 70, 18, Component.literal(I18n.t("edit.itemPlaceholder"))));
				targetBox.setMaxLength(120);
				targetBox.setValue(task.target.isEmpty() ? "" : Suggestions.nameOfItem(task.target));
				targetBox.setHint(Component.literal(I18n.t("edit.itemPlaceholder")));
				targetBox.setResponder(s -> refreshSuggestions(s, false));

				countBox = addRenderableWidget(new EditBox(font, x0 + CONTENT_WIDTH - 62, rowTarget, 62, 18, Component.literal(I18n.t("edit.count"))));
				countBox.setMaxLength(6);
				countBox.setValue(Integer.toString(task.targetCount));
				countBox.setHint(Component.literal(I18n.t("edit.count")));

			}
			case ADVANCEMENT -> {
				targetBox = addRenderableWidget(new EditBox(font, x0, rowTarget, CONTENT_WIDTH, 18, Component.literal(I18n.t("edit.advPlaceholder"))));
				targetBox.setMaxLength(160);
				targetBox.setValue(task.target.isEmpty() ? "" : Suggestions.nameOfAdvancement(minecraft, task.target));
				targetBox.setHint(Component.literal(I18n.t("edit.advPlaceholder")));
				targetBox.setResponder(s -> refreshSuggestions(s, true));

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

				countBox = addRenderableWidget(new EditBox(font, x0 + CONTENT_WIDTH - 68, rowTarget, 68, 18, Component.literal(I18n.t("edit.radius"))));
				countBox.setValue(Integer.toString(task.radius));
				countBox.setHint(Component.literal(I18n.t("edit.radius")));
			}
			case TIMER -> {
				addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.timer", task.timerMode.label())), b -> {
					captureFields();
					task.timerMode = task.timerMode.next();
					rebuildWidgets();
				}).bounds(x0, rowTarget, 170, 18).build());

				if (task.timerMode == TimerMode.INTERVAL) {
					intervalBox = addRenderableWidget(new EditBox(font, x0 + 178, rowTarget, 62, 18, Component.literal(I18n.t("edit.minutes"))));
					intervalBox.setValue(Integer.toString(task.intervalMinutes));
					intervalBox.setHint(Component.literal(I18n.t("edit.minutes")));
				} else if (task.timerMode == TimerMode.GAME_TIME) {
					hourBox = addRenderableWidget(new EditBox(font, x0, rowExtra, 44, 18, Component.literal(I18n.t("edit.hour"))));
					hourBox.setValue(Integer.toString(task.gameHour));
					hourBox.setHint(Component.literal(I18n.t("edit.hour")));
					minuteBox = addRenderableWidget(new EditBox(font, x0 + 50, rowExtra, 44, 18, Component.literal(I18n.t("edit.minute"))));
					minuteBox.setValue(Integer.toString(task.gameMinute));
					minuteBox.setHint(Component.literal(I18n.t("edit.minute")));
					daysBox = addRenderableWidget(new EditBox(font, x0 + 100, rowExtra, 44, 18, Component.literal(I18n.t("edit.days"))));
					daysBox.setValue(Integer.toString(task.gameDayInterval));
					daysBox.setHint(Component.literal(I18n.t("edit.days")));

					addRenderableWidget(Button.builder(Component.literal(I18n.t("edit.presetDay")), b -> {
						task.gameHour = 6;
						task.gameMinute = 0;
						rebuildWidgets();
					}).bounds(x0 + 154, rowExtra, 74, 18).build());
					addRenderableWidget(Button.builder(Component.literal(I18n.t("edit.presetNight")), b -> {
						task.gameHour = 19;
						task.gameMinute = 0;
						rebuildWidgets();
					}).bounds(x0 + 232, rowExtra, 74, 18).build());
				}
			}
			case NORMAL -> {
			}
		}

		if (ClientShared.available()) {
			addRenderableWidget(Button.builder(visibilityLabel(), b -> {
				captureFields();
				task.visibility = (task.visibility + 1) % 3;
				rebuildWidgets();
			}).bounds(x0, rowShare, 148, 18).build());
			if (task.visibility > 0) {
				addRenderableWidget(Button.builder(shareModeLabel(), b -> {
					captureFields();
					task.shareAll = !task.shareAll;
					b.setMessage(shareModeLabel());
				}).bounds(x0 + 152, rowShare, 148, 18).build());
			}
		}

		remindBox = addRenderableWidget(new EditBox(font, x0, rowRemind, CONTENT_WIDTH, 18, Component.literal(I18n.t("edit.remind"))));
		remindBox.setMaxLength(120);
		remindBox.setValue(task.remindText);
		remindBox.setHint(Component.literal(I18n.t("edit.remindHint")));

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.save")), b -> save())
				.bounds(width / 2 - 100, rowButtons, 96, 20).build());
		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.cancel")), b -> onClose())
				.bounds(width / 2 + 4, rowButtons, 96, 20).build());
	}

	/**
	 * 从下往上排，窗口再矮也不会把按钮挤到屏幕外：
	 * 内容 44-72 / 类型·优先级 72-90 / 目标 92-122 / 开关 128-146 /
	 * 附加提醒（标签 + 输入框）/ 共享（可见性 + 完成条件）/ 保存·取消。
	 *
	 * <p>矮窗口（GUI 高 225 左右）也能完整显示；高窗口里各块自动下移，留白更均匀。
	 */
	private void layout() {
		rowType = 72;
		rowTarget = 104;
		rowSuggest = rowTarget + 22;
		rowExtra = 128;
		rowButtons = height - 26;
		rowShare = rowButtons - 24;
		rowRemind = rowShare - 26;
	}

	private Component shareModeLabel() {
		return Component.literal(I18n.t("edit.mode",
				I18n.t(task.shareAll ? "share.mode.all" : "share.mode.any")));
	}

	private Component visibilityLabel() {
		String name = switch (task.visibility) {
			case 1 -> I18n.t("vis.shared");
			case 2 -> I18n.t("vis.special");
			default -> I18n.t("vis.personal");
		};
		return Component.literal(I18n.t("edit.visibility", name));
	}
	private Component timerHint() {
		return switch (task.timerMode) {
			case INTERVAL -> Component.literal(I18n.t("edit.timerHint") + I18n.t("edit.intervalHint"));
			case GAME_TIME -> Component.literal(I18n.t("edit.gameTimeHint"));
			default -> Component.literal(I18n.t("edit.timerHint"));
		};
	}

	private static String fmt(double v) {
		return String.format("%.1f", v);
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
		if (hourBox != null) {
			task.gameHour = Math.max(0, Math.min(23, parseInt(hourBox.getValue(), task.gameHour)));
		}
		if (minuteBox != null) {
			task.gameMinute = Math.max(0, Math.min(59, parseInt(minuteBox.getValue(), task.gameMinute)));
		}
		if (daysBox != null) {
			task.gameDayInterval = Math.max(1, parseInt(daysBox.getValue(), task.gameDayInterval));
		}
	}

	private void save() {
		captureFields();
		if (task.visibility > 0) {
			if (!ClientShared.available()) {
				ClientTasks.toast(I18n.t("share.noServer"));
				return;
			}
			String sharedText = task.text == null ? "" : task.text.trim();
			if (sharedText.isEmpty()) {
				ClientTasks.toast(I18n.t("edit.empty"));
				return;
			}
			task.text = sharedText;
			ClientShared.publish(task);
			ClientTasks.toast(I18n.t("share.published"));
			onClose();
			return;
		}
		String text = task.text == null ? "" : task.text.trim();
		if (text.isEmpty()) {
			ClientTasks.toast(I18n.t("edit.empty"));
			return;
		}
		task.text = text;

		switch (task.type) {
			case COLLECT -> {
				String resolved = resolveTarget(false);
				if (resolved.isEmpty()) {
					ClientTasks.toast(I18n.t("edit.needItem"));
					return;
				}
				task.target = resolved;
				task.triggered = false;
			}
			case ADVANCEMENT -> {
				String resolved = resolveTarget(true);
				if (resolved.isEmpty()) {
					ClientTasks.toast(I18n.t("edit.needAdv"));
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
		ClientTasks.toast(creating ? I18n.t("edit.created") : I18n.t("edit.saved"));
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
		graphics.text(font, I18n.t("edit.content"), x0, rowType - 28, 0xFFB0BEC5);

		switch (task.type) {
			case COLLECT -> {
				graphics.text(font, I18n.t("edit.itemHint"), x0, rowTarget - 12, 0xFFB0BEC5);
				suggestionX = x0;
				suggestionY = rowSuggest;
				suggestionWidth = CONTENT_WIDTH - 70;
			}
			case ADVANCEMENT -> {
				graphics.text(font, I18n.t("edit.advHint"), x0, rowTarget - 12, 0xFFB0BEC5);
				suggestionX = x0;
				suggestionY = rowSuggest;
				suggestionWidth = CONTENT_WIDTH;
			}
			case COORDINATE -> graphics.text(font, I18n.t("edit.coordHint"), x0, rowTarget - 12, 0xFFB0BEC5);
			case TIMER -> graphics.text(font, timerHint(), x0, rowTarget - 12, 0xFFB0BEC5);
			case NORMAL -> graphics.text(font, I18n.t("edit.normalHint"), x0, rowTarget - 12, 0xFF80CBC4);
		}

		// 窗口很矮时这一行标签会和上面的开关行重叠，此时只靠输入框里的提示文字
		if (rowRemind - 12 > rowExtra + 18) {
			graphics.text(font, I18n.t("edit.remind"), x0, rowRemind - 12, 0xFFB0BEC5);
		}
		if (!ClientShared.available()) {
			graphics.text(font, I18n.t("share.noServer"), x0, rowButtons - 12, 0xFF9E9E9E);
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
