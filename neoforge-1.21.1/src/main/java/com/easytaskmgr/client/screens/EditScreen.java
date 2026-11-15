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
 * 新建 / 编辑待办，布局与 Fabric 版 {@code TaskEditScreen} 对齐。
 *
 * <p>300px 单列（窗口更窄时自动收窄）；窗口高度不足时整体上移、按钮不再压住提醒字段；
 * 成就 / 时间提醒不可共享，切换类型时会把可见性清零并禁用共享按钮；输入框的最大长度
 * 会兼容已有文本，避免保存时静默截断。
 */
public class EditScreen extends Screen {
	private static final int MAX_CONTENT_WIDTH = 300;
	private static final int FIELD_HEIGHT = 16;
	private static final int ROW = 22;
	/** 表单自然高度（从标题到按钮底部）。 */
	private static final int NATURAL_HEIGHT = 212;

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
	private Button visibilityButton;
	private Button shareButton;

	private final List<String[]> suggestions = new ArrayList<>();
	private String detailLine = "";
	private String detailFor = "";

	/** 布局：列宽、左边缘、整体上移量。 */
	private int col;
	private int x0;
	private int shift;

	public EditScreen(Task existing) {
		super(Component.literal(ClientUtil.t(existing == null ? "edit.new" : "edit.edit")));
		this.task = existing == null ? new Task() : copyOf(existing);
		this.creating = existing == null;
		if (this.task.shareMode < 0) {
			this.task.shareMode = com.easytaskmgr.share.SharedTasks.MODE_ANY_CREATOR;
		}
	}

	private boolean shareable() {
		return task.type != TaskType.ADVANCEMENT && task.type != TaskType.TIMER;
	}

	private void layout() {
		col = Math.max(160, Math.min(MAX_CONTENT_WIDTH, width - 16));
		x0 = (width - col) / 2;
		// 窗口太矮时整体上移，避免按钮压住提醒输入框（最多上移 24px，保留标题空间）
		shift = Math.min(24, Math.max(0, NATURAL_HEIGHT - height));
	}

	private int row(int y) {
		return y - shift;
	}

	@Override
	protected void init() {
		layout();
		int y = row(30);

		textBox = addRenderableWidget(new EditBox(font, x0, y + 10, col, FIELD_HEIGHT, Component.literal("")));
		textBox.setMaxLength(Math.max(120, task.text == null ? 0 : task.text.length()));
		textBox.setValue(task.text == null ? "" : task.text);
		y = row(62);

		addRenderableWidget(Button.builder(typeLabel(), b -> {
			captureFields();
			task.type = nextType(task.type);
			if (!shareable()) {
				// 成就 / 时间提醒不支持共享：清零，避免之后切到可共享类型时带着旧设置发布
				task.visibility = 0;
			}
			rebuildWidgets();
		}).bounds(x0, y, half(), FIELD_HEIGHT).build());
		addRenderableWidget(Button.builder(priorityLabel(), b -> {
			task.priority = Priority.byWeight(task.priority).next().weight();
			b.setMessage(priorityLabel());
		}).bounds(x0 + half() + 8, y, half(), FIELD_HEIGHT).build());
		y = row(88);

		if (task.type == TaskType.COLLECT) {
			targetBox = addRenderableWidget(new EditBox(font, x0, y, col - 60, FIELD_HEIGHT, Component.literal("")));
			targetBox.setMaxLength(Math.max(120, task.target == null ? 0 : task.target.length()));
			targetBox.setValue(task.target);
			countBox = addRenderableWidget(new EditBox(font, x0 + col - 54, y, 54, FIELD_HEIGHT, Component.literal("")));
			countBox.setValue(Integer.toString(task.targetCount));
		} else if (task.type == TaskType.ADVANCEMENT) {
			targetBox = addRenderableWidget(new EditBox(font, x0, y, col, FIELD_HEIGHT, Component.literal("")));
			targetBox.setMaxLength(Math.max(160, task.target == null ? 0 : task.target.length()));
			targetBox.setValue(task.target);
		} else if (task.type == TaskType.COORDINATE) {
			int bw = Math.max(40, (col - 54 - 12) / 3);
			xBox = addRenderableWidget(new EditBox(font, x0, y, bw, FIELD_HEIGHT, Component.literal("")));
			xBox.setValue(fmt(task.x));
			yBox = addRenderableWidget(new EditBox(font, x0 + bw + 6, y, bw, FIELD_HEIGHT, Component.literal("")));
			yBox.setValue(fmt(task.y));
			zBox = addRenderableWidget(new EditBox(font, x0 + (bw + 6) * 2, y, bw, FIELD_HEIGHT, Component.literal("")));
			zBox.setValue(fmt(task.z));
			radiusBox = addRenderableWidget(new EditBox(font, x0 + col - 54, y, 54, FIELD_HEIGHT, Component.literal("")));
			radiusBox.setValue(Integer.toString(task.radius));
		} else if (task.type == TaskType.TIMER) {
			addRenderableWidget(Button.builder(timerLabel(), b -> {
				captureFields();
				task.timerMode = nextTimer(task.timerMode);
				rebuildWidgets();
			}).bounds(x0, y, half(), FIELD_HEIGHT).build());
			int paramX = x0 + half() + 8;
			if (task.timerMode == TimerMode.INTERVAL) {
				minutesBox = addRenderableWidget(new EditBox(font, paramX, y, 54, FIELD_HEIGHT, Component.literal("")));
				minutesBox.setValue(Integer.toString(task.intervalMinutes));
			} else if (task.timerMode == TimerMode.GAME_TIME) {
				int gw = Math.max(34, (col - half() - 8 - 12) / 3);
				hourBox = addRenderableWidget(new EditBox(font, paramX, y, gw, FIELD_HEIGHT, Component.literal("")));
				hourBox.setValue(Integer.toString(task.gameHour));
				minuteBox = addRenderableWidget(new EditBox(font, paramX + gw + 6, y, gw, FIELD_HEIGHT, Component.literal("")));
				minuteBox.setValue(Integer.toString(task.gameMinute));
				dayBox = addRenderableWidget(new EditBox(font, paramX + (gw + 6) * 2, y, gw, FIELD_HEIGHT, Component.literal("")));
				dayBox.setValue(Integer.toString(task.gameDayInterval));
			}
		}
		y = row(112);

		visibilityButton = addRenderableWidget(Button.builder(visibilityLabel(), b -> {
			task.visibility = task.visibility >= 2 ? 0 : task.visibility + 1;
			b.setMessage(visibilityLabel());
		}).bounds(x0, y, half(), FIELD_HEIGHT).build());
		shareButton = addRenderableWidget(Button.builder(shareLabel(), b -> {
			task.shareMode = (task.shareMode + 1) % 5;
			b.setMessage(shareLabel());
		}).bounds(x0 + half() + 8, y, half(), FIELD_HEIGHT).build());
		boolean canShare = shareable();
		visibilityButton.active = canShare;
		shareButton.active = canShare;
		if (!canShare) {
			task.visibility = 0;
		}

		remindBox = addRenderableWidget(new EditBox(font, x0, row(138), col, FIELD_HEIGHT, Component.literal("")));
		remindBox.setMaxLength(Math.max(160, task.remindText == null ? 0 : task.remindText.length()));
		remindBox.setValue(task.remindText);

		int buttonY = Math.max(20, row(170));
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.save")), b -> save())
				.bounds(x0, buttonY, half(), 20).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.cancel")), b -> onClose())
				.bounds(x0 + half() + 8, buttonY, half(), 20).build());

		applyFieldVisibility();
	}

	private int half() {
		return (col - 8) / 2;
	}

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

	/** 当前类型/提醒方式对应的说明文字。 */
	private String hintText() {
		return switch (task.type) {
			case COLLECT -> ClientUtil.t("edit.itemHint");
			case ADVANCEMENT -> ClientUtil.t("edit.advHint");
			case COORDINATE -> ClientUtil.t("edit.coordHint");
			case NORMAL -> ClientUtil.t("edit.normalHint");
			case TIMER -> switch (task.timerMode) {
				case INTERVAL -> ClientUtil.t("edit.timerHint");
				case GAME_TIME -> ClientUtil.t("edit.hour") + " / " + ClientUtil.t("edit.minute")
						+ " / " + ClientUtil.t("edit.days");
				case ON_JOIN, EVERY_JOIN -> ClientUtil.t("timer." + (task.timerMode == TimerMode.ON_JOIN ? "join" : "everyJoin"));
			};
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

	/** 候选弹窗位置：右侧放不下时改到目标输入框下方，并整体收进屏幕。 */
	private int[] suggestionBox() {
		int heightNeeded = suggestions.size() * 10 + 12;
		int sideX = x0 + col + 4;
		boolean side = sideX + 164 <= width - 4;
		int boxX = side ? sideX : x0;
		int boxY = side ? row(44) : Math.min(height - heightNeeded - 4, row(88) + 20);
		return new int[]{Math.max(4, Math.min(boxX, width - 168)), Math.max(18, boxY)};
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

	/** 切换类型 / 提醒方式前把当前输入写回任务，避免重建控件时丢掉未保存的内容。 */
	private void captureFields() {
		if (textBox != null) {
			task.text = textBox.getValue();
		}
		if (remindBox != null) {
			task.remindText = remindBox.getValue();
		}
		if (targetBox != null && targetBox.isVisible()) {
			task.target = targetBox.getValue().trim();
		}
		if (task.type == TaskType.COLLECT && countBox != null) {
			task.targetCount = Math.max(1, parseInt(countBox, task.targetCount));
		}
		if (task.type == TaskType.COORDINATE) {
			if (radiusBox != null) {
				task.radius = Math.max(1, parseInt(radiusBox, task.radius));
			}
			if (xBox != null) {
				task.x = parseDouble(xBox, task.x);
				task.y = parseDouble(yBox, task.y);
				task.z = parseDouble(zBox, task.z);
			}
		}
		if (minutesBox != null) {
			task.intervalMinutes = Math.max(1, parseInt(minutesBox, task.intervalMinutes));
		}
		if (hourBox != null) {
			task.gameHour = Math.min(23, Math.max(0, parseInt(hourBox, task.gameHour)));
			task.gameMinute = Math.min(59, Math.max(0, parseInt(minuteBox, task.gameMinute)));
			task.gameDayInterval = Math.max(1, parseInt(dayBox, task.gameDayInterval));
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
		if (!shareable()) {
			// 成就 / 时间提醒永远不共享
			task.visibility = 0;
		}
		task.normalize();
		if (creating) {
			ClientTasks.add(task);
		} else {
			TaskStore.get().save();
		}
		if (task.visibility > 0 && shareable()) {
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
		ClientUtil.centered(graphics, font, title.getString(), width / 2, Math.max(2, row(14)), 0xFFFFD54F);

		int contentLabelY = row(30);
		label(graphics, ClientUtil.t("edit.content"), x0, contentLabelY - 10);
		label(graphics, hintText(), x0, row(76));
		label(graphics, ClientUtil.t("edit.remind"), x0, row(128));
		if (!shareable()) {
			graphics.drawString(font, ClientUtil.t("edit.noShare"), x0, row(160), 0xFF9E9E9E, true);
		}

		super.render(graphics, mouseX, mouseY, partialTick);

		refreshSuggestions();
		if (!suggestions.isEmpty()) {
			int[] box = suggestionBox();
			int boxWidth = 164;
			int boxHeight = suggestions.size() * 10 + 4;
			graphics.fill(box[0] - 2, box[1] - 2, box[0] + boxWidth, box[1] + boxHeight, 0xF0101014);
			for (int i = 0; i < suggestions.size(); i++) {
				int sy = box[1] + i * 10;
				boolean hover = mouseX >= box[0] && mouseX <= box[0] + boxWidth && mouseY >= sy && mouseY < sy + 10;
				graphics.drawString(font, ClientUtil.clip(font, suggestions.get(i)[1], 150), box[0] + 2, sy,
						hover ? 0xFFFFFF00 : 0xFF9AD9FF, true);
			}
		}
		if (!detailLine.isBlank()) {
			graphics.drawString(font, ClientUtil.clip(font, detailLine, col), x0,
					Math.min(height - 12, row(160) + 10), 0xFFB0FFB0, true);
		}
}

	private void label(GuiGraphics graphics, String text, int x, int y) {
		graphics.drawString(font, ClientUtil.clip(font, text, col), x, y, 0xFFB0BEC5, true);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// 候选优先处理，避免被下方控件吞掉点击
		refreshSuggestions();
		if (!suggestions.isEmpty()) {
			int[] box = suggestionBox();
			int boxWidth = 164;
			for (int i = 0; i < suggestions.size(); i++) {
				int sy = box[1] + i * 10;
				if (mouseX >= box[0] && mouseX <= box[0] + boxWidth && mouseY >= sy && mouseY < sy + 10) {
					String[] suggestion = suggestions.get(i);
					targetBox.setValue(suggestion[0]);
					detailLine = suggestion[2].isBlank()
							? ""
							: suggestion[2] + (suggestion[3].isBlank() ? "" : " · ↑ " + suggestion[3]);
					detailFor = suggestion[0];
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
