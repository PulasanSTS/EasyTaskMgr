package com.easytaskmgr.client.ui;

import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.client.TextWrap;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办列表：分页显示，支持勾选完成、编辑、删除，以及进入新增 / 设置界面。
 * 长标题按宽度换行（最多两行），不再用省略号。
 */
public class TaskListScreen extends Screen {
	private static final int PAGE_SIZE = 4;
	private static final int ROW_HEIGHT = 34;
	private static final int TEXT_WIDTH = 210;

	private final Screen parent;
	private int page;
	private boolean showDone;

	public TaskListScreen(Screen parent) {
		super(Component.literal("待办列表"));
		this.parent = parent;
	}

	private List<Task> visibleTasks() {
		List<Task> list = new ArrayList<>(TaskStore.get().active());
		if (showDone) {
			list.addAll(TaskStore.get().completed());
		}
		return list;
	}

	private int pageCount(int size) {
		return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int listTop = 58;
		List<Task> tasks = visibleTasks();
		page = Math.max(0, Math.min(page, pageCount(tasks.size()) - 1));

		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = page * PAGE_SIZE + i;
			if (index >= tasks.size()) {
				break;
			}
			Task task = tasks.get(index);
			int y = listTop + i * ROW_HEIGHT;
			addRenderableWidget(Button.builder(Component.literal(task.done ? "✔" : "□"), b -> {
				TaskStore.get().toggleDone(task);
				TaskStore.get().save();
				ClientNet.markDirty();
				rebuildWidgets();
			}).bounds(cx - 210, y, 20, 20).build());

			addRenderableWidget(Button.builder(Component.literal("改"), b -> {
				if (minecraft != null) {
					minecraft.setScreenAndShow(new TaskEditScreen(task, false, this));
				}
			}).bounds(cx + 150, y, 24, 20).build());

			addRenderableWidget(Button.builder(Component.literal("×"), b -> {
				TaskStore.get().remove(task);
				TaskStore.get().save();
				ClientNet.markDirty();
				rebuildWidgets();
			}).bounds(cx + 178, y, 20, 20).build());
		}

		int bottom = height - 34;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page = Math.max(0, page - 1);
			rebuildWidgets();
		}).bounds(cx - 210, bottom, 20, 20).build());

		addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page = Math.min(pageCount(visibleTasks().size()) - 1, page + 1);
			rebuildWidgets();
		}).bounds(cx - 184, bottom, 20, 20).build());

		addRenderableWidget(Button.builder(Component.literal(showDone ? "隐藏已完成" : "显示已完成"), b -> {
			showDone = !showDone;
			rebuildWidgets();
		}).bounds(cx - 156, bottom, 80, 20).build());

		addRenderableWidget(Button.builder(Component.literal("新增"), b -> {
			if (minecraft != null) {
				minecraft.setScreenAndShow(new TaskEditScreen(new Task(), true, this));
			}
		}).bounds(cx - 68, bottom, 44, 20).build());

		addRenderableWidget(Button.builder(Component.literal("设置"), b -> {
			if (minecraft != null) {
				minecraft.setScreenAndShow(new ConfigScreen(this));
			}
		}).bounds(cx - 20, bottom, 44, 20).build());

		addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
				.bounds(cx + 28, bottom, 48, 20).build());

		addRenderableWidget(Button.builder(Component.literal("清空已完成"), b -> {
			TaskStore.get().clearCompleted();
			TaskStore.get().save();
			ClientNet.markDirty();
			rebuildWidgets();
		}).bounds(cx + 82, bottom, 86, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101014);
		graphics.centeredText(font, title, width / 2, 22, 0xFFFFD54F);

		int cx = width / 2;
		int listTop = 58;
		List<Task> tasks = visibleTasks();

		int total = tasks.size();
		String pageInfo = "第 " + (page + 1) + " / " + pageCount(total) + " 页 · 共 " + total + " 条"
				+ (ClientNet.remoteCount() > 0 ? " · 队友 " + ClientNet.remoteCount() + " 条" : "");
		graphics.centeredText(font, Component.literal(pageInfo), cx, 40, 0xFF9E9E9E);

		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = page * PAGE_SIZE + i;
			if (index >= tasks.size()) {
				break;
			}
			Task task = tasks.get(index);
			int y = listTop + i * ROW_HEIGHT;
			int color = task.done ? 0xFF757575 : task.priority().color();
			String prefix = task.done ? "[已完成] " : "[" + task.priority().label() + "] ";
			List<String> lines = TextWrap.wrap(font, prefix + task.displayText(), TEXT_WIDTH);
			int shown = Math.min(2, lines.size());
			for (int l = 0; l < shown; l++) {
				graphics.text(font, lines.get(l), cx - 184, y + 2 + l * 11, color);
			}

			String detail = detailOf(task);
			if (detail != null && lines.size() <= 2) {
				graphics.text(font, TextWrap.clip(font, detail, 90), cx + 40, y + 2, 0xFF9FB3C8);
			}
		}

		graphics.text(font, "B 快速添加 · N 列表 · K 设置 · H 隐藏 HUD", 6, height - 14, 0xFF78909C);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private String detailOf(Task task) {
		return switch (task.type) {
			case NORMAL -> null;
			case COLLECT, CRAFT -> task.progressText();
			case TIMER -> task.timerMode.label();
			case ADVANCEMENT -> "成就";
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
