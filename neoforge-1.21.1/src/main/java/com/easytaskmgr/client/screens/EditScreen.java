package com.easytaskmgr.client.screens;

import com.easytaskmgr.client.ClientShared;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.ClientUtil;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import com.easytaskmgr.task.TimerMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 新建 / 编辑待办：类型、数量、坐标、提醒方式、优先级、共享设置。 */
public class EditScreen extends Screen {
	private final Task task;
	private final boolean creating;
	private EditBox textBox;
	private EditBox targetBox;
	private EditBox countBox;
	private EditBox xBox;
	private EditBox yBox;
	private EditBox zBox;
	private EditBox radiusBox;
	private EditBox minutesBox;
	private EditBox hourBox;
	private EditBox minuteBox;
	private EditBox dayBox;
	private EditBox remindBox;

	public EditScreen(Task existing) {
		super(Component.translatable(existing == null ? "edit.new" : "edit.edit"));
		this.task = existing == null ? new Task() : existing;
		this.creating = existing == null;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int top = 40;
		textBox = box(cx - 110, top, 220, task.text);
		targetBox = box(cx + 120, top, 140, task.target);
		countBox = box(cx + 120, top + 24, 60, Integer.toString(task.targetCount));

		xBox = box(cx - 110, top + 48, 60, fmt(task.x));
		yBox = box(cx - 44, top + 48, 60, fmt(task.y));
		zBox = box(cx + 22, top + 48, 60, fmt(task.z));
		radiusBox = box(cx + 88, top + 48, 60, Integer.toString(task.radius));

		minutesBox = box(cx - 110, top + 72, 60, Integer.toString(task.intervalMinutes));
		hourBox = box(cx - 44, top + 72, 50, Integer.toString(task.gameHour));
		minuteBox = box(cx + 10, top + 72, 50, Integer.toString(task.gameMinute));
		dayBox = box(cx + 64, top + 72, 50, Integer.toString(task.gameDayInterval));

		remindBox = box(cx - 110, top + 96, 220, task.remindText);

		int y = top + 124;
		addRenderableWidget(Button.builder(typeLabel(), b -> {
			task.type = nextType(task.type);
			b.setMessage(typeLabel());
		}).bounds(cx - 110, y, 110, 20).build());
		addRenderableWidget(Button.builder(priorityLabel(), b -> {
			task.priority = Priority.byWeight(task.priority).next().weight();
			b.setMessage(priorityLabel());
		}).bounds(cx + 4, y, 110, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(timerLabel(), b -> {
			task.timerMode = nextTimer(task.timerMode);
			b.setMessage(timerLabel());
		}).bounds(cx - 110, y, 110, 20).build());
		addRenderableWidget(Button.builder(visibilityLabel(), b -> {
			task.visibility = task.visibility >= 2 ? 0 : task.visibility + 1;
			b.setMessage(visibilityLabel());
		}).bounds(cx + 4, y, 110, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(shareLabel(), b -> {
			task.shareMode = (task.shareMode + 1) % 5;
			b.setMessage(shareLabel());
		}).bounds(cx - 110, y, 110, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("ui.save"), b -> save())
				.bounds(cx + 4, y, 110, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.translatable("ui.cancel"), b -> onClose())
				.bounds(cx - 110, y, 220, 20).build());
	}

	private EditBox box(int x, int y, int w, String value) {
		EditBox box = new EditBox(font, x, y, w, 18, Component.literal(""));
		box.setValue(value == null ? "" : value);
		box.setMaxLength(200);
		addRenderableWidget(box);
		return box;
	}

	private static String fmt(double value) {
		return value == Math.floor(value) ? Integer.toString((int) value) : Double.toString(value);
	}

	private static TaskType nextType(TaskType type) {
		TaskType[] values = TaskType.values();
		return values[(type.ordinal() + 1) % values.length];
	}

	private static TimerMode nextTimer(TimerMode mode) {
		TimerMode[] values = TimerMode.values();
		return values[(mode.ordinal() + 1) % values.length];
	}

	private Component typeLabel() {
		return Component.translatable("ui.type", Component.translatable("type." + task.type.name().toLowerCase()));
	}

	private Component priorityLabel() {
		return Component.translatable("ui.priority", Priority.byWeight(task.priority).label());
	}

	private Component timerLabel() {
		return Component.translatable("ui.timer", Component.translatable("timer." + switch (task.timerMode) {
			case ON_JOIN -> "join";
			case EVERY_JOIN -> "everyJoin";
			case INTERVAL -> "interval";
			case GAME_TIME -> "gameTime";
		}));
	}

	private Component visibilityLabel() {
		String key = switch (task.visibility) {
			case 1 -> "vis.shared";
			case 2 -> "vis.special";
			default -> "vis.personal";
		};
		return Component.translatable("edit.visibility", Component.translatable(key));
	}

	private Component shareLabel() {
		return Component.translatable("edit.mode", Component.literal(modeText()));
	}

	private String modeText() {
		boolean zh = ClientUtil.lang() == com.easytaskmgr.i18n.I18n.Lang.ZH_CN;
		return switch (task.shareMode) {
			case 0 -> zh ? "任一认领者" : "Any claimant";
			case 1 -> zh ? "所有认领者" : "All claimants";
			case 2 -> zh ? "任一（含发布者）" : "Any (incl. creator)";
			case 3 -> zh ? "所有（含发布者）" : "All (incl. creator)";
			case 4 -> zh ? "共同完成" : "Combined";
			default -> "?";
		};
	}

	private static int parseInt(EditBox box, int fallback) {
		try {
			return Integer.parseInt(box.getValue().trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private static double parseDouble(EditBox box, double fallback) {
		try {
			return Double.parseDouble(box.getValue().trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private void save() {
		task.text = textBox.getValue().trim();
		if (task.text.isEmpty() && task.type == TaskType.NORMAL) {
			return;
		}
		task.target = targetBox.getValue().trim();
		task.targetCount = Math.max(1, parseInt(countBox, 1));
		task.x = parseDouble(xBox, 0);
		task.y = parseDouble(yBox, 0);
		task.z = parseDouble(zBox, 0);
		task.radius = Math.max(1, parseInt(radiusBox, 8));
		task.intervalMinutes = Math.max(1, parseInt(minutesBox, 30));
		task.gameHour = Math.min(23, Math.max(0, parseInt(hourBox, 6)));
		task.gameMinute = Math.min(59, Math.max(0, parseInt(minuteBox, 0)));
		task.gameDayInterval = Math.max(1, parseInt(dayBox, 1));
		task.remindText = remindBox.getValue().trim();
		task.normalize();
		if (creating) {
			ClientTasks.add(task);
		} else {
			TaskStore.get().save();
		}
		// 共享任务：直接发布到服务端（本地表只保留个人任务）
		if (task.visibility > 0 && ClientShared.available()
				&& task.type != TaskType.ADVANCEMENT && task.type != TaskType.TIMER) {
			ClientShared.publish(task);
			TaskStore.get().remove(task);
			TaskStore.get().save();
		}
		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
		int cx = width / 2;
		graphics.drawString(font, Component.translatable("edit.content"), cx - 110, 30, 0xFFAAAAAA, false);
		graphics.drawString(font, Component.translatable("edit.remind"), cx - 110, 86, 0xFFAAAAAA, false);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
