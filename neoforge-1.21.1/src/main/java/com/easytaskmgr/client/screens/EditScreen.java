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
	/** 当前候选（id, 显示名）；输入物品 / 成就名时出现。 */
	private final java.util.List<String[]> suggestions = new java.util.ArrayList<>();

	public EditScreen(Task existing) {
		super(Component.literal(ClientUtil.t(existing == null ? "edit.new" : "edit.edit")));
		// 编辑副本：取消时不会把改动留在仓库对象上（review #18）
		this.task = existing == null ? new Task() : copyOf(existing);
		this.creating = existing == null;
		if (this.task.shareMode < 0) {
			this.task.shareMode = com.easytaskmgr.share.SharedTasks.MODE_ANY_CREATOR;
		}
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
		}).bounds(cx - 150, y, 140, 20).build());
		addRenderableWidget(Button.builder(priorityLabel(), b -> {
			task.priority = Priority.byWeight(task.priority).next().weight();
			b.setMessage(priorityLabel());
		}).bounds(cx + 10, y, 140, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(timerLabel(), b -> {
			task.timerMode = nextTimer(task.timerMode);
			b.setMessage(timerLabel());
		}).bounds(cx - 150, y, 140, 20).build());
		addRenderableWidget(Button.builder(visibilityLabel(), b -> {
			task.visibility = task.visibility >= 2 ? 0 : task.visibility + 1;
			b.setMessage(visibilityLabel());
		}).bounds(cx + 10, y, 140, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(shareLabel(), b -> {
			task.shareMode = (task.shareMode + 1) % 5;
			b.setMessage(shareLabel());
		}).bounds(cx - 150, y, 140, 20).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.save")), b -> save())
				.bounds(cx + 10, y, 140, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.cancel")), b -> onClose())
				.bounds(cx - 110, y, 220, 20).build());
	}

	private EditBox box(int x, int y, int w, String value) {
		EditBox box = new EditBox(font, x, y, w, 18, Component.literal(""));
		box.setValue(value == null ? "" : value);
		box.setMaxLength(200);
		addRenderableWidget(box);
		return box;
	}

	private static Task copyOf(Task source) {
		Task copy = new Task();
		copy.id = source.id;
		copy.text = source.text;
		copy.priority = source.priority;
		copy.done = source.done;
		copy.createdAt = source.createdAt;
		copy.completedAt = source.completedAt;
		copy.target = source.target;
		copy.targetCount = source.targetCount;
		copy.x = source.x;
		copy.y = source.y;
		copy.z = source.z;
		copy.radius = source.radius;
		copy.intervalMinutes = source.intervalMinutes;
		copy.gameHour = source.gameHour;
		copy.gameMinute = source.gameMinute;
		copy.gameDayInterval = source.gameDayInterval;
		copy.lastGameDay = source.lastGameDay;
		copy.lastClockTime = source.lastClockTime;
		copy.remindText = source.remindText;
		copy.progress = source.progress;
		copy.triggered = source.triggered;
		copy.nextTriggerAt = source.nextTriggerAt;
		copy.owner = source.owner;
		copy.visibility = source.visibility;
		copy.shareMode = source.shareMode;
		return copy;
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
		return Component.literal(ClientUtil.t("ui.type", ClientUtil.t("type." + task.type.name().toLowerCase())));
	}

	private Component priorityLabel() {
		return Component.literal(ClientUtil.t("ui.priority", Priority.byWeight(task.priority).label()));
	}

	private Component timerLabel() {
		return Component.literal(ClientUtil.t("ui.timer", switch (task.timerMode) {
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
		return Component.literal(ClientUtil.t("edit.visibility", ClientUtil.t(key)));
	}

	private Component shareLabel() {
		return Component.literal(ClientUtil.t("edit.mode", modeText()));
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
		// 收集 / 成就任务必须有目标，否则永远完不成（review #23）
		if ((task.type == TaskType.COLLECT || task.type == TaskType.ADVANCEMENT) && task.target.isBlank()) {
			ClientUtil.warn(ClientUtil.t("edit.needTarget"));
			return;
		}
		task.normalize();
		if (creating) {
			ClientTasks.add(task);
		} else {
			TaskStore.get().save();
		}
		// 共享任务：先发布，成功才从本地表移除；失败 / 服务端没模组时降级并提示（review #8、#28）
		boolean shareable = task.type != TaskType.ADVANCEMENT && task.type != TaskType.TIMER;
		if (task.visibility > 0 && shareable) {
			if (!ClientShared.available()) {
				ClientUtil.warn(ClientUtil.t("share.serverMissing"));
			} else if (!ClientShared.publish(task)) {
				ClientUtil.warn(ClientUtil.t("share.publishFailed"));
			} else {
				TaskStore.get().remove(task);
				TaskStore.get().save();
			}
		}
		if (task.type == TaskType.ADVANCEMENT) {
			ClientTasks.queryAdvancement(task.target);
		}
		onClose();
	}

	/** 根据输入框内容刷新候选列表（物品 / 成就，最多 8 条）。 */
	private void refreshSuggestions() {
		suggestions.clear();
		String query = targetBox == null ? "" : targetBox.getValue().trim().toLowerCase();
		if (query.isEmpty()) {
			return;
		}
		if (task.type == TaskType.COLLECT) {
			for (var entry : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
				String id = entry.getKey().location().toString();
				String name = entry.getValue().getDescription().getString();
				if (name.toLowerCase().contains(query) || id.contains(query)) {
					suggestions.add(new String[]{id, name});
					if (suggestions.size() >= 8) {
						break;
					}
				}
			}
		} else if (task.type == TaskType.ADVANCEMENT) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.getConnection() != null) {
				for (var node : mc.getConnection().getAdvancements().getTree().nodes()) {
					var display = node.holder().value().display();
					if (display.isEmpty()) {
						continue;
					}
					String title = display.get().getTitle().getString();
					if (title.toLowerCase().contains(query)) {
						suggestions.add(new String[]{node.holder().id().toString(), title});
						if (suggestions.size() >= 8) {
							break;
						}
					}
				}
			}
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
		int cx = width / 2;
		graphics.drawString(font, Component.literal(ClientUtil.t("edit.content")), cx - 110, 30, 0xFFAAAAAA, false);
		graphics.drawString(font, Component.literal(ClientUtil.t("edit.remind")), cx - 110, 86, 0xFFAAAAAA, false);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int cx = width / 2;
		int sugY = 40 + 20;
		java.util.List<String[]> snapshot = new java.util.ArrayList<>(suggestions);
		for (String[] suggestion : snapshot) {
			if (mouseX >= cx + 120 && mouseX <= cx + 260 && mouseY >= sugY && mouseY < sugY + 10) {
				targetBox.setValue(suggestion[0]);
				return true;
			}
			sugY += 10;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
