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
 * 新建 / 编辑待办，布局与 Fabric 版 {@code TaskEditScreen} 对齐：
 * 300px 宽的一列，从上到下依次为内容 / 类型+优先级 / 目标 / 参数 / 共享 / 附加提醒 / 按钮，
 * 标签统一灰蓝、标题金色带阴影，长文本不再挤在一起（issue #43）。
 */
public class EditScreen extends Screen {
	private static final int CONTENT_WIDTH = 300;
	private static final int FIELD_HEIGHT = 16;
	private static final int ROW = 22;

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
	private Button typeButton;
	private Button visibilityButton;
	private Button shareButton;

	private final List<String[]> suggestions = new ArrayList<>();
	private String detailLine = "";
	private String detailFor = "";

	public EditScreen(Task existing) {
		super(Component.literal(ClientUtil.t(existing == null ? "edit.new" : "edit.edit")));
		this.task = existing == null ? new Task() : copyOf(existing);
		this.creating = existing == null;
		if (this.task.shareMode < 0) {
			this.task.shareMode = com.easytaskmgr.share.SharedTasks.MODE_ANY_CREATOR;
		}
	}

	@Override
	protected void init() {
		int x0 = width / 2 - CONTENT_WIDTH / 2;
		int y = 30;

		// 内容
		textBox = addRenderableWidget(new EditBox(font, x0, y + 10, CONTENT_WIDTH, FIELD_HEIGHT, Component.literal("")));
		textBox.setMaxLength(120);
		textBox.setValue(task.text);
		y += 32;

		// 类型 + 优先级
		typeButton = addRenderableWidget(Button.builder(typeLabel(), b -> {
			task.type = nextType(task.type);
			if (task.type == TaskType.ADVANCEMENT || task.type == TaskType.TIMER) {
				task.visibility = 0;
			}
			rebuildWidgets();
		}).bounds(x0, y, 140, FIELD_HEIGHT).build());
		addRenderableWidget(Button.builder(priorityLabel(), b -> {
			task.priority = Priority.byWeight(task.priority).next().weight();
			b.setMessage(priorityLabel());
		}).bounds(x0 + 148, y, 140, FIELD_HEIGHT).build());
		y += ROW + 4;

		// 目标 / 坐标 / 定时参数
		if (task.type == TaskType.COLLECT) {
			targetBox = addRenderableWidget(new EditBox(font, x0, y, CONTENT_WIDTH - 60, FIELD_HEIGHT, Component.literal("")));
			targetBox.setMaxLength(120);
			targetBox.setValue(task.target);
			countBox = addRenderableWidget(new EditBox(font, x0 + CONTENT_WIDTH - 54, y, 54, FIELD_HEIGHT, Component.literal("")));
			countBox.setValue(Integer.toString(task.targetCount));
		} else if (task.type == TaskType.ADVANCEMENT) {
			targetBox = addRenderableWidget(new EditBox(font, x0, y, CONTENT_WIDTH, FIELD_HEIGHT, Component.literal("")));
			targetBox.setMaxLength(160);
			targetBox.setValue(task.target);
		} else if (task.type == TaskType.COORDINATE) {
			xBox = addRenderableWidget(new EditBox(font, x0, y, 60, FIELD_HEIGHT, Component.literal("")));
			xBox.setValue(fmt(task.x));
			yBox = addRenderableWidget(new EditBox(font, x0 + 66, y, 60, FIELD_HEIGHT, Component.literal("")));
			yBox.setValue(fmt(task.y));
			zBox = addRenderableWidget(new EditBox(font, x0 + 132, y, 60, FIELD_HEIGHT, Component.literal("")));
			zBox.setValue(fmt(task.z));
			radiusBox = addRenderableWidget(new EditBox(font, x0 + 198, y, 54, FIELD_HEIGHT, Component.literal("")));
			radiusBox.setValue(Integer.toString(task.radius));
		} else if (task.type == TaskType.TIMER) {
			addRenderableWidget(Button.builder(timerLabel(), b -> {
				task.timerMode = nextTimer(task.timerMode);
				rebuildWidgets();
			}).bounds(x0, y, 150, FIELD_HEIGHT).build());
			if (task.timerMode == TimerMode.INTERVAL) {
				minutesBox = addRenderableWidget(new EditBox(font, x0 + 158, y, 54, FIELD_HEIGHT, Component.literal("")));
				minutesBox.setValue(Integer.toString(task.intervalMinutes));
			} else if (task.timerMode == TimerMode.GAME_TIME) {
				hourBox = addRenderableWidget(new EditBox(font, x0 + 158, y, 42, FIELD_HEIGHT, Component.literal("")));
				hourBox.setValue(Integer.toString(task.gameHour));
				minuteBox = addRenderableWidget(new EditBox(font, x0 + 204, y, 42, FIELD_HEIGHT, Component.literal("")));
				minuteBox.setValue(Integer.toString(task.gameMinute));
				dayBox = addRenderableWidget(new EditBox(font, x0 + 250, y, 44, FIELD_HEIGHT, Component.literal("")));
				dayBox.setValue(Integer.toString(task.gameDayInterval));
			}
		}
		if (targetBox != null) {
			y += ROW + 2;
		} else {
			y += ROW + 2;
		}

		// 共享：可见性 + 完成条件
		visibilityButton = addRenderableWidget(Button.builder(visibilityLabel(), b -> {
			task.visibility = task.visibility >= 2 ? 0 : task.visibility + 1;
			b.setMessage(visibilityLabel());
		}).bounds(x0, y, 140, FIELD_HEIGHT).build());
		shareButton = addRenderableWidget(Button.builder(shareLabel(), b -> {
			task.shareMode = (task.shareMode + 1) % 5;
			b.setMessage(shareLabel());
		}).bounds(x0 + 148, y, 140, FIELD_HEIGHT).build());
		y += ROW + 4;

		// 附加提醒
		remindBox = addRenderableWidget(new EditBox(font, x0, y, CONTENT_WIDTH, FIELD_HEIGHT, Component.literal("")));
		remindBox.setMaxLength(160);
		remindBox.setValue(task.remindText);
		y += ROW + 10;

		// 按钮
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.save")), b -> save())
				.bounds(x0, y, 140, 20).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.cancel")), b -> onClose())
				.bounds(x0 + 148, y, 140, 20).build());

		applyFieldVisibility();
	}

	/** 目标/参数输入框在重建时按类型创建，这里只需要控制坐标/时间参数的可见性。 */
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

	/** 刷新候选；成就额外带描述与前置成就。 */
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
		if (task.type == TaskType.COORDINATE) {
			task.x = parseDouble(xBox, task.x);
			task.y = parseDouble(yBox, task.y);
			task.z = parseDouble(zBox, task.z);
			task.radius = Math.max(1, parseInt(radiusBox, task.radius));
		}
		if (task.type == TaskType.TIMER) {
			if (task.timerMode == TimerMode.INTERVAL && minutesBox != null) {
				task.intervalMinutes = Math.max(1, parseInt(minutesBox, task.intervalMinutes));
			} else if (task.timerMode == TimerMode.GAME_TIME && hourBox != null) {
				task.gameHour = Math.min(23, Math.max(0, parseInt(hourBox, task.gameHour)));
				task.gameMinute = Math.min(59, Math.max(0, parseInt(minuteBox, task.gameMinute)));
				task.gameDayInterval = Math.max(1, parseInt(dayBox, task.gameDayInterval));
			}
		}
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
		graphics.fill(0, 0, width, height, 0xE0101014);
		ClientUtil.centered(graphics, font, title.getString(), width / 2, 14, 0xFFFFD54F);

		int x0 = width / 2 - CONTENT_WIDTH / 2;
		// 与 init() 的固定行位置保持一致
		label(graphics, ClientUtil.t("edit.content"), x0, 30);
		label(graphics, task.type == TaskType.TIMER ? ClientUtil.t("edit.timerHint")
				: task.type == TaskType.COORDINATE ? ClientUtil.t("edit.coordHint")
				: task.type == TaskType.ADVANCEMENT ? ClientUtil.t("edit.advHint")
				: ClientUtil.t("edit.itemHint"), x0, 76);
		label(graphics, ClientUtil.t("edit.remind"), x0, 128);
		if (task.type == TaskType.ADVANCEMENT || task.type == TaskType.TIMER) {
			graphics.drawString(font, ClientUtil.t("edit.noShare"), x0, 158, 0xFF9E9E9E, true);
		}

		refreshSuggestions();
		int sugY = 62;
		for (String[] suggestion : suggestions) {
			boolean hover = mouseX >= x0 + CONTENT_WIDTH && mouseX <= x0 + CONTENT_WIDTH + 160
					&& mouseY >= sugY && mouseY < sugY + 10;
			graphics.drawString(font, ClientUtil.clip(font, suggestion[1], 150), x0 + CONTENT_WIDTH + 4, sugY,
					hover ? 0xFFFFFF00 : 0xFF9AD9FF, true);
			sugY += 10;
		}
		if (!detailLine.isBlank()) {
			graphics.drawString(font, ClientUtil.clip(font, detailLine, 320), x0, sugY + 2, 0xFFB0FFB0, true);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void label(GuiGraphics graphics, String text, int x, int y) {
		graphics.drawString(font, ClientUtil.clip(font, text, CONTENT_WIDTH), x, y, 0xFFB0BEC5, true);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int x0 = width / 2 - CONTENT_WIDTH / 2;
		int sugY = 62;
		List<String[]> snapshot = new ArrayList<>(suggestions);
		for (String[] suggestion : snapshot) {
			if (mouseX >= x0 + CONTENT_WIDTH && mouseX <= x0 + CONTENT_WIDTH + 160
					&& mouseY >= sugY && mouseY < sugY + 10) {
				targetBox.setValue(suggestion[0]);
				detailLine = suggestion[2].isBlank()
						? ""
						: suggestion[2] + (suggestion[3].isBlank() ? "" : " · ↑ " + suggestion[3]);
				detailFor = suggestion[0];
				return true;
			}
			sugY += 10;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
