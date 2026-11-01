package com.easytaskmgr.client.ui;

import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.client.ClientShared;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.Keybinds;
import com.easytaskmgr.client.TaskHud;
import com.easytaskmgr.client.TextWrap;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办列表（单页 + 滚动）。
 *
 * <p>不再分页：一次显示若干行，用鼠标滚轮上下滚动；右侧画一条滚动条指示位置。
 * 每行仍是「勾选 / 文本 / 编辑 / 删除」。
 */
public class TaskListScreen extends Screen {
	private static final int LIST_TOP = 58;
	private static final int ROW_HEIGHT = 34;
	private static final int TEXT_WIDTH = 210;

	private final Screen parent;
	private int scrollOffset;
	private boolean showDone;
	private int visibleRows = 4;

	public TaskListScreen(Screen parent) {
		super(Component.literal(I18n.t("list.title")));
		this.parent = parent;
	}

	private List<Task> visibleTasks() {
		List<Task> list = new ArrayList<>(TaskStore.get().active());
		if (showDone) {
			list.addAll(TaskStore.get().completed());
		}
		for (ClientShared.Entry entry : ClientShared.entries()) {
			// 「显示已完成」打开时，共享任务里的已完成条目也要出现（issue #10）
			if (entry.done() && !showDone) {
				continue;
			}
			list.add(entry.task());
		}
		return list;
	}

	private int maxOffset(int total) {
		return Math.max(0, total - visibleRows);
	}

	@Override
	protected void init() {
		visibleRows = Math.max(3, (height - LIST_TOP - 46) / ROW_HEIGHT);
		int cx = width / 2;
		List<Task> tasks = visibleTasks();
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset(tasks.size())));

		for (int i = 0; i < visibleRows; i++) {
			int index = scrollOffset + i;
			if (index >= tasks.size()) {
				break;
			}
			Task task = tasks.get(index);
			int y = LIST_TOP + i * ROW_HEIGHT;
			ClientShared.Entry shared = ClientShared.find(task.id);
			String mark = task.done ? "✔" : shared != null && shared.doneByMe() ? "◐" : "□";
			addRenderableWidget(Button.builder(Component.literal(mark), b -> {
				if (shared != null) {
					if (shared.done()) {
						// 已完成的共享任务只展示，不再能点
					} else if (shared.canClaim()) {
						ClientShared.claim(shared.id());
						ClientTasks.toast(I18n.t("share.claimedToast", task.displayText()));
					} else if (shared.iAmParticipant() && shared.doneByMe()) {
						ClientTasks.toast(I18n.t("share.waitOthers"));
					} else if (shared.iAmParticipant()) {
						boolean partial = ClientShared.willWaitOthers(shared);
						ClientShared.complete(shared.id());
						ClientShared.notifyProgress(shared, partial);
					}
				} else if (task.done) {
					TaskStore.get().toggleDone(task);
					TaskStore.get().save();
				} else {
					ClientTasks.finish(task);
				}
				ClientNet.markDirty();
				rebuildWidgets();
			}).bounds(cx - 210, y, 20, 20).build());

			// 共享任务不在本地任务表里：不提供「编辑 / 删除」，避免误删本地副本或改坏别人的任务
			if (shared == null) {
				addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.edit")), b -> {
					if (minecraft != null) {
						minecraft.setScreenAndShow(new TaskEditScreen(task, false, this));
					}
				}).bounds(cx + 150, y, 28, 20).build());

				addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.delete")), b -> {
					TaskStore.get().remove(task);
					TaskStore.get().save();
					ClientNet.markDirty();
					rebuildWidgets();
				}).bounds(cx + 182, y, 20, 20).build());
			} else if (shared.mine()) {
				// 自己的共享任务：留一个撤回按钮
				addRenderableWidget(Button.builder(Component.literal(I18n.t("share.revoke")), b -> {
					ClientShared.revoke(shared.id());
					ClientTasks.toast(I18n.t("share.revoked"));
					rebuildWidgets();
				}).bounds(cx + 178, y, 26, 20).build());
			}
		}

		int bottom = height - 34;
		addRenderableWidget(Button.builder(Component.literal(showDone ? I18n.t("ui.hideDone") : I18n.t("ui.showDone")), b -> {
			showDone = !showDone;
			scrollOffset = 0;
			rebuildWidgets();
		}).bounds(cx - 210, bottom, 88, 20).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.new")), b -> {
			if (minecraft != null) {
				minecraft.setScreenAndShow(new TaskEditScreen(new Task(), true, this));
			}
		}).bounds(cx - 114, bottom, 44, 20).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.settings")), b -> {
			if (minecraft != null) {
				minecraft.setScreenAndShow(new ConfigScreen(this));
			}
		}).bounds(cx - 62, bottom, 44, 20).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.close")), b -> onClose())
				.bounds(cx - 10, bottom, 48, 20).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.clearDone")), b -> {
			TaskStore.get().clearCompleted();
			TaskStore.get().save();
			ClientNet.markDirty();
			scrollOffset = 0;
			rebuildWidgets();
		}).bounds(cx + 46, bottom, 86, 20).build());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int listBottom = LIST_TOP + visibleRows * ROW_HEIGHT;
		if (mouseX >= width / 2 - 212 && mouseX <= width / 2 + 204 && mouseY >= LIST_TOP && mouseY <= listBottom) {
			List<Task> tasks = visibleTasks();
			int max = maxOffset(tasks.size());
			if (max > 0) {
				scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(vertical)));
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101014);
		graphics.centeredText(font, title, width / 2, 22, 0xFFFFD54F);

		int cx = width / 2;
		List<Task> tasks = visibleTasks();
		int total = tasks.size();
		int end = Math.min(total, scrollOffset + visibleRows);
		String info = I18n.t("list.range", total == 0 ? 0 : scrollOffset + 1, end, total)
				+ (maxOffset(total) > 0 ? " · " + I18n.t("list.scrollHint") : "")
				+ (ClientShared.entries().isEmpty() ? "" : I18n.t("list.sharedCount", ClientShared.entries().size()));
		graphics.centeredText(font, Component.literal(info), cx, 40, 0xFF9E9E9E);

		for (int i = 0; i < visibleRows; i++) {
			int index = scrollOffset + i;
			if (index >= tasks.size()) {
				break;
			}
			Task task = tasks.get(index);
			int y = LIST_TOP + i * ROW_HEIGHT;
			ClientShared.Entry sharedEntry = ClientShared.find(task.id);
			int color = task.done ? 0xFF757575
					: sharedEntry == null ? task.priority().color()
					: sharedEntry.visibility() == 2 ? 0xFFFF80AB : 0xFF64B5F6;
			String prefix = task.done ? I18n.t("list.donePrefix") : "[" + task.priority().label() + "] ";
			int prefixWidth = font.width(prefix);
			// 优先级前缀永远用优先级颜色（issue #8），正文才跟随共享任务的蓝 / 粉紫
			graphics.text(font, prefix, cx - 184, y + 2, task.priority().color());
			List<String> lines = TextWrap.wrap(font, task.displayText(), Math.max(60, TEXT_WIDTH - prefixWidth));
			int shown = Math.min(2, lines.size());
			for (int l = 0; l < shown; l++) {
				graphics.text(font, lines.get(l), cx - 184 + prefixWidth, y + 2 + l * 11, color);
			}

			String detail = sharedEntry == null ? detailOf(task) : TaskHud.sharedDetail(minecraft, sharedEntry);
			if (detail != null && lines.size() <= 2) {
				graphics.text(font, TextWrap.clip(font, detail, 90), cx + 40, y + 2, 0xFF9FB3C8);
			}
		}

		if (total == 0) {
			graphics.centeredText(font, Component.literal(I18n.t("list.empty")), cx, LIST_TOP + 20, 0xFF9E9E9E);
		}

		// 滚动条
		int max = maxOffset(total);
		if (max > 0) {
			int barX = cx + 206;
			int barTop = LIST_TOP;
			int barHeight = visibleRows * ROW_HEIGHT - 4;
			graphics.fill(barX, barTop, barX + 3, barTop + barHeight, 0x33FFFFFF);
			int thumbHeight = Math.max(12, barHeight * visibleRows / total);
			int thumbTop = barTop + (barHeight - thumbHeight) * scrollOffset / max;
			graphics.fill(barX, thumbTop, barX + 3, thumbTop + thumbHeight, 0xAAFFD54F);
		}

		graphics.text(font, I18n.t("list.footer",
				Keybinds.QUICK_ADD.getTranslatedKeyMessage().getString(),
				Keybinds.OPEN_LIST.getTranslatedKeyMessage().getString(),
				Keybinds.OPEN_CONFIG.getTranslatedKeyMessage().getString(),
				Keybinds.TOGGLE_HUD.getTranslatedKeyMessage().getString()), 6, height - 12, 0xFF78909C);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private String detailOf(Task task) {
		return switch (task.type) {
			case NORMAL -> null;
			case COLLECT -> {
				String item = ClientTasks.itemName(task.target);
				String progress = task.progressText();
				yield progress == null || progress.isBlank() ? item : item + "  " + progress;
			}
			case TIMER -> task.timerMode.label();
			case ADVANCEMENT -> ClientTasks.advancementName(minecraft, task.target);
			case COORDINATE -> String.format("%.0f, %.0f, %.0f", task.x, task.y, task.z);
		};
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
