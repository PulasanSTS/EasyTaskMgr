package com.easytaskmgr.client.screens;

import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.ClientUtil;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 快速添加：一句话 + 优先级，回车即保存。 */
public class QuickAddScreen extends Screen {
	private EditBox input;
	private Priority priority = Priority.NORMAL;

	public QuickAddScreen() {
		super(Component.literal("EasyTaskMgr"));
	}

	@Override
	protected void init() {
		input = new EditBox(font, width / 2 - 100, height / 2 - 30, 200, 20, Component.literal("todo"));
		input.setMaxLength(200);
		input.setResponder(text -> {
		});
		addRenderableWidget(input);
		setInitialFocus(input);

		addRenderableWidget(Button.builder(Component.literal("[" + priority.label() + "]"),
						button -> {
							priority = priority.next();
							button.setMessage(Component.literal("[" + priority.label() + "]"));
						})
				.bounds(width / 2 - 100, height / 2, 90, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("ui.save"), button -> save())
				.bounds(width / 2 + 10, height / 2, 90, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("ui.cancel"), button -> onClose())
				.bounds(width / 2 - 100, height / 2 + 24, 200, 20).build());
	}

	private void save() {
		String text = input == null ? "" : input.getValue().trim();
		if (text.isEmpty()) {
			return;
		}
		Task task = new Task();
		task.text = text;
		task.priority = priority.weight();
		ClientTasks.add(task);
		ClientUtil.info(ClientUtil.t("notify.added", text, priority.label()));
		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 55, 0xFFFFFFFF);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 || keyCode == 335) { // Enter / KP Enter
			save();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
