package com.easytaskmgr.client.screens;

import com.easytaskmgr.client.ClientShared;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.ClientUtil;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import com.easytaskmgr.task.TimerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 新建 / 编辑待办。
 *
 * <p>字段按类型显示（Fabric 版行为）：收集只显示目标与数量，成就只显示目标，
 * 坐标显示 x/y/z/半径，定时显示提醒方式与时间参数；内容与附加提醒始终显示。
 *
 * <p>输入物品 / 成就名时下方出现候选（最多 8 条），选中成就时再显示一行
 * 「描述 · 前置成就」，对应 Fabric 版的成就详情面板。
 */
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

	/** 当前候选（id, 显示名, 描述, 前置成就名）。 */
	private final List<String[]> suggestions = new ArrayList<>();
	/** 选中成就后显示的详情行。 */
	private String detailLine = "";
	/** 详情对应的成就 id，目标被改写后清空详情。 */
	private String detailFor = "";

	public EditScreen(Task existing) {
		super(Component.literal(ClientUtil.t(existing == null ? "edit.new" : "edit.edit")));
		// 编辑副本：取消时不会把改动留在仓库对象上
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
		targetBox = box(cx + 120, top, 160, task.target);
		countBox = box(cx + 120, top + 24, 60, Integer.toString(task.targetCount));

		xBox = box(cx - 150, top + 48, 60, fmt(task.x));
		yBox = box(cx - 84, top + 48, 60, fmt(task.y));
		zBox = box(cx - 18, top + 48, 60, fmt(task.z));
		radiusBox = box(cx + 48, top + 48, 60, Integer.toString(task.radius));

		minutesBox = box(cx - 150, top + 48, 60, Integer.toString(task.intervalMinutes));
		hourBox = box(cx - 84, top + 48, 50, Integer.toString(task.gameHour));
		minuteBox = box(cx - 28, top + 48, 50, Integer.toString(task.gameMinute));
		dayBox = box(cx + 28, top + 48, 50, Integer.toString(task.gameDayInterval));

		remindBox = box(cx - 150, top + 96, 300, task.remindText);

		int y = top + 124;
		addRenderableWidget(Button.builder(typeLabel(), b -> {
			task.type = nextType(task.type);
			b.setMessage(typeLabel());
			applyFieldVisibility();
		}).bounds(cx - 150, y, 140, 20).build());
		addRenderableWidget(Button.builder(priorityLabel(), b -> {
			task.priority = Priority.byWeight(task.priority).next().weight();
			b.setMessage(priorityLabel());
		}).bounds(cx + 10, y, 140, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(timerLabel(), b -> {
			task.timerMode = nextTimer(task.timerMode);
			b.setMessage(timerLabel());
			applyFieldVisibility();
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
				.bounds(cx - 150, y, 300, 20).build());

		applyFieldVisibility();
	}

	/** 按任务类型显示 / 隐藏输入框。 */
	private void applyFieldVisibility() {
		boolean collect = task.type == TaskType.COLLECT;
		boolean advancement = task.type == TaskType.ADVANCEMENT;
		boolean coordinate = task.type == TaskType.COORDINATE;
		boolean timer = task.type == TaskType.TIMER;

		setVisible(targetBox, collect || advancement);
		setVisible(countBox, collect);
		setVisible(xBox, coordinate);
		setVisible(yBox, coordinate);
		setVisible(zBox, coordinate);
		setVisible(radiusBox, coordinate);
		setVisible(minutesBox, timer && task.timerMode == TimerMode.INTERVAL);
		setVisible(hourBox, timer && task.timerMode == TimerMode.GAME_TIME);
		setVisible(minuteBox, timer && task.timerMode == TimerMode.GAME_TIME);
		setVisible(dayBox, timer && task.timerMode == TimerMode.GAME_TIME);
	}

	private static void setVisible(EditBox box, boolean visible) {
		if (box != null) {
			box.setVisible(visible);
			box.setEditable(visible);
		}
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
		copy.type = source.type;
		copy.timerMode = source.timerMode;
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

	/** 短标签，避免英文文案在按钮里被截断。 */
	private Component timerLabel() {
		String mode = switch (task.timerMode) {
			case ON_JOIN -> ClientUtil.t("timer.join");
			case EVERY_JOIN -> ClientUtil.t("timer.everyJoin");
			case INTERVAL -> ClientUtil.t("timer.interval");
			case GAME_TIME -> ClientUtil.t("timer.gameTime");
		};
		return Component.literal(ClientUtil.t("ui.timer", mode));
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
			case 2 -> zh ? "任一+发布者" : "Any + creator";
			case 3 -> zh ? "所有+发布者" : "All + creator";
			case 4 -> zh ? "共同完成" : "Combined";
			default -> "?";
		};
	}

	/** 刷新候选；成就额外带「描述 / 前置成就」。 */
	private void refreshSuggestions() {
		suggestions.clear();
		if (targetBox == null || !targetBox.isVisible()) {
			return;
		}
		String query = targetBox.getValue().trim().toLowerCase();
		if (!detailFor.isBlank() && !detailFor.equals(targetBox.getValue().trim())) {
			detailLine = "";
			detailFor = "";
		}
		if (query.isEmpty()) {
			return;
		}
		if (task.type == TaskType.COLLECT) {
			for (var entry : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
				String id = entry.getKey().location().toString();
				String name = entry.getValue().getDescription().getString();
				if (name.toLowerCase().contains(query) || id.contains(query)) {
					suggestions.add(new String[]{id, name, "", ""});
					if (suggestions.size() >= 8) {
						break;
					}
				}
			}
		} else if (task.type == TaskType.ADVANCEMENT) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.getConnection() != null) {
				for (var node : mc.getConnection().getAdvancements().getTree().nodes()) {
					var display = node.holder().value().display();
					if (display.isEmpty()) {
						continue;
					}
					String title = display.get().getTitle().getString();
					if (title.toLowerCase().contains(query)) {
						String desc = display.get().getDescription().getString();
						String parent = node.parent() == null ? ""
								: node.parent().holder().value().display()
										.map(d -> d.getTitle().getString()).orElse("");
						suggestions.add(new String[]{node.holder().id().toString(), title, desc, parent});
						if (suggestions.size() >= 8) {
							break;
						}
					}
				}
			}
		}
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
		if (task.type == TaskType.COLLECT || task.type == TaskType.ADVANCEMENT) {
			task.target = targetBox.getValue().trim();
			if (task.target.isEmpty()) {
				ClientUtil.warn(ClientUtil.t("edit.needTarget"));
				return;
			}
		}
		if (task.type == TaskType.COLLECT) {
			task.targetCount = Math.max(1, parseInt(countBox, 1));
		}
		// 无论当前类型如何都读一遍：隐藏的输入框保留原值，切换类型不会丢数据（review #2）
		task.x = parseDouble(xBox, task.x);
		task.y = parseDouble(yBox, task.y);
		task.z = parseDouble(zBox, task.z);
		task.radius = Math.max(1, parseInt(radiusBox, task.radius));
		task.intervalMinutes = Math.max(1, parseInt(minutesBox, task.intervalMinutes));
		task.gameHour = Math.min(23, Math.max(0, parseInt(hourBox, task.gameHour)));
		task.gameMinute = Math.min(59, Math.max(0, parseInt(minuteBox, task.gameMinute)));
		task.gameDayInterval = Math.max(1, parseInt(dayBox, task.gameDayInterval));
		task.remindText = remindBox.getValue().trim();
		task.normalize();
		if (creating) {
			ClientTasks.add(task);
		} else {
			TaskStore.get().save();
		}
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

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
		int cx = width / 2;
		graphics.drawString(font, ClientUtil.t("edit.content"), cx - 110, 30, 0xFFAAAAAA, false);
		graphics.drawString(font, ClientUtil.t("edit.remind"), cx - 150, 86, 0xFFAAAAAA, false);

		refreshSuggestions();
		int sugY = 88;
		for (String[] suggestion : suggestions) {
			boolean hover = mouseX >= cx + 120 && mouseX <= cx + 280 && mouseY >= sugY && mouseY < sugY + 10;
			graphics.drawString(font, ClientUtil.clip(font, suggestion[1], 150), cx + 120, sugY,
					hover ? 0xFFFFFF00 : 0xFF9AD9FF, false);
			sugY += 10;
		}
		if (!detailLine.isBlank()) {
			graphics.drawString(font, ClientUtil.clip(font, detailLine, 320), cx - 150, sugY + 4,
					0xFFB0FFB0, false);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int cx = width / 2;
		int sugY = 88;
		List<String[]> snapshot = new ArrayList<>(suggestions);
		for (String[] suggestion : snapshot) {
			if (mouseX >= cx + 120 && mouseX <= cx + 280 && mouseY >= sugY && mouseY < sugY + 10) {
				targetBox.setValue(suggestion[0]);
				detailLine = suggestion[2].isBlank()
						? ""
						: suggestion[2] + (suggestion[3].isBlank() ? "" : " · ↑ " + suggestion[3]);
				detailFor = suggestion[0];
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
