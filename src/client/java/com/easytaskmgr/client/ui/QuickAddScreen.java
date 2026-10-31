package com.easytaskmgr.client.ui;

import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 快捷键弹出的「快速添加」界面：一行文字 + 优先级，回车即添加。
 */
public class QuickAddScreen extends Screen {
	private final Screen parent;
	private EditBox input;
	private Priority priority = Priority.NORMAL;
	private Button priorityButton;

	public QuickAddScreen(Screen parent) {
		super(Component.literal(I18n.t("quick.title")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int y = height / 2 - 40;

		input = new EditBox(font, cx - 100, y, 200, 20, Component.literal(I18n.t("quick.placeholder")));
		input.setMaxLength(120);
		input.setValue("");
		addRenderableWidget(input);

		priorityButton = Button.builder(priorityLabel(), b -> {
			priority = priority.next();
			b.setMessage(priorityLabel());
		}).bounds(cx - 100, y + 28, 96, 20).build();
		addRenderableWidget(priorityButton);

		addRenderableWidget(Button.builder(Component.literal(I18n.t("quick.add")), b -> submit())
				.bounds(cx + 4, y + 28, 96, 20).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("quick.more")), b -> openAdvanced())
				.bounds(cx - 100, y + 56, 96, 20).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("ui.cancel")), b -> onClose())
				.bounds(cx + 4, y + 56, 96, 20).build());

		setInitialFocus(input);
	}

	private Component priorityLabel() {
		return Component.literal(I18n.t("ui.priority", priority.label()));
	}

	private void submit() {
		String text = input.getValue().trim();
		if (text.isEmpty()) {
			ClientTasks.toast(I18n.t("edit.empty"));
			return;
		}
		ClientTasks.addQuick(text, priority);
		ClientNet.markDirty();
		onClose();
	}

	private void openAdvanced() {
		Task task = new Task();
		task.text = input.getValue().trim();
		task.setPriority(priority);
		if (minecraft != null) {
			minecraft.setScreenAndShow(new TaskEditScreen(task, true, parent));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			submit();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x99000000);
		graphics.centeredText(font, title, width / 2, height / 2 - 78, 0xFFFFD54F);
		graphics.centeredText(font, Component.literal(I18n.t("quick.hint")),
				width / 2, height / 2 - 64, 0xFF9E9E9E);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
