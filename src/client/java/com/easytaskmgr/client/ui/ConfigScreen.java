package com.easytaskmgr.client.ui;

import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 设置界面。
 *
 * <p>左侧是一列固定宽度的设置项（16~216px），右侧留着显示 HUD 预览框——
 * 预览框就是真实的 HUD 位置，按住它拖动即可移动 HUD。
 * 这样两块区域在横向上不重叠，小窗口也不会出现文字压字。
 */
public class ConfigScreen extends Screen {
	private static final int X0 = 16;
	private static final int ROW_HEIGHT = 22;
	private static final int VALUE_X = X0 + 74;
	private static final int BTN1_X = X0 + 112;
	private static final int BTN2_X = X0 + 150;
	private static final int BTN_W = 38;

	private final Screen parent;
	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public ConfigScreen(Screen parent) {
		super(Component.literal("EasyTaskMgr 设置"));
		this.parent = parent;
	}

	private ModConfig cfg() {
		return ModConfig.get();
	}

	/** 右侧预览框 = 真实 HUD 的矩形（同一套锚点算法）。 */
	private int[] hudRect() {
		int maxWidth = Math.max(120, cfg().hudMaxWidth);
		List<Task> active = TaskStore.get().active();
		int approxRows = active.isEmpty() ? 3 : 1 + Math.min(active.size(), 3) * 2;
		int lineHeight = font.lineHeight + 1;
		int panelHeight = font.lineHeight + 6 + approxRows * lineHeight + 5;
		int panelWidth = Math.min(maxWidth + 14, Math.max(120, width - 8));

		int right = Math.min(width - 2, Math.max(panelWidth + 4, (int) (width * (cfg().hudXPercent / 100.0))));
		int left = Math.max(2, right - panelWidth);
		int top = Math.max(2, Math.min(height - panelHeight - 2, (int) (height * (cfg().hudYPercent / 100.0))));
		return new int[]{left, top, right, top + panelHeight};
	}

	@Override
	protected void init() {
		int y = 40;

		addRenderableWidget(Button.builder(Component.literal("-"), b -> move(-2, 0)).bounds(BTN1_X, y, BTN_W, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> move(2, 0)).bounds(BTN2_X, y, BTN_W, 18).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> move(0, -2)).bounds(BTN1_X, y + ROW_HEIGHT, BTN_W, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> move(0, 2)).bounds(BTN2_X, y + ROW_HEIGHT, BTN_W, 18).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			cfg().hudMaxWidth = Math.max(120, cfg().hudMaxWidth - 20);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN1_X, y + ROW_HEIGHT * 2, BTN_W, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			cfg().hudMaxWidth = Math.min(400, cfg().hudMaxWidth + 20);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN2_X, y + ROW_HEIGHT * 2, BTN_W, 18).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			cfg().hudCompactLimit = Math.max(1, cfg().hudCompactLimit - 1);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN1_X, y + ROW_HEIGHT * 3, BTN_W, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			cfg().hudCompactLimit = Math.min(20, cfg().hudCompactLimit + 1);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN2_X, y + ROW_HEIGHT * 3, BTN_W, 18).build());

		int toggleY = y + ROW_HEIGHT * 4 + 8;
		addRenderableWidget(toggle(X0, toggleY, "背景", cfg().hudBackground, v -> cfg().hudBackground = v));
		addRenderableWidget(toggle(X0 + 66, toggleY, "距离", cfg().showDistance, v -> cfg().showDistance = v));
		addRenderableWidget(toggle(X0 + 132, toggleY, "速度", cfg().showSpeed, v -> cfg().showSpeed = v));

		addRenderableWidget(toggle(X0, toggleY + ROW_HEIGHT, "队友", cfg().showTeammates, v -> cfg().showTeammates = v));
		addRenderableWidget(toggle(X0 + 66, toggleY + ROW_HEIGHT, "音效", cfg().soundEnabled, v -> cfg().soundEnabled = v));
		addRenderableWidget(toggle(X0 + 132, toggleY + ROW_HEIGHT, "共享", cfg().shareEnabled, v -> {
			cfg().shareEnabled = v;
			if (!v) {
				ClientNet.reset();
			}
		}));

		int bottom = height - 30;
		addRenderableWidget(Button.builder(Component.literal("重置位置"), b -> {
			cfg().hudXPercent = 99;
			cfg().hudYPercent = 2;
			cfg().save();
			rebuildWidgets();
		}).bounds(X0, bottom, 90, 20).build());

		addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
				.bounds(X0 + 100, bottom, 90, 20).build());
	}

	private Button toggle(int x, int y, String name, boolean initial, java.util.function.Consumer<Boolean> setter) {
		return Button.builder(label(name, initial), b -> {
			boolean next = !read(name);
			setter.accept(next);
			cfg().save();
			b.setMessage(label(name, next));
		}).bounds(x, y, 60, 18).build();
	}

	private boolean read(String name) {
		return switch (name) {
			case "背景" -> cfg().hudBackground;
			case "距离" -> cfg().showDistance;
			case "速度" -> cfg().showSpeed;
			case "队友" -> cfg().showTeammates;
			case "音效" -> cfg().soundEnabled;
			case "共享" -> cfg().shareEnabled;
			default -> false;
		};
	}

	private Component label(String name, boolean value) {
		return Component.literal(name + (value ? " ✓" : " ✗"));
	}

	private void move(int dx, int dy) {
		cfg().hudXPercent = clamp(cfg().hudXPercent + dx, 0, 100);
		cfg().hudYPercent = clamp(cfg().hudYPercent + dy, 0, 100);
		cfg().save();
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int[] r = hudRect();
		// 26.3 里左键是 1（不是 GLFW 的 0），用 <=1 兼容两种约定
		if (event.button() <= 1 && event.x() >= r[0] && event.x() <= r[2]
				&& event.y() >= r[1] && event.y() <= r[3]) {
			dragging = true;
			dragOffsetX = event.x() - r[0];
			dragOffsetY = event.y() - r[1];
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			int left = (int) (event.x() - dragOffsetX);
			int top = (int) (event.y() - dragOffsetY);
			int panelWidth = Math.min(cfg().hudMaxWidth + 14, Math.max(120, width - 8));
			cfg().hudXPercent = clamp((int) Math.round((left + panelWidth) * 100.0 / width), 0, 100);
			cfg().hudYPercent = clamp((int) Math.round(top * 100.0 / height), 0, 100);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			cfg().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xC0000000);
		graphics.text(font, "EasyTaskMgr 设置", X0, 12, 0xFFFFD54F);

		int y = 40;
		graphics.text(font, "位置 X", X0, y + 5, 0xFFB0BEC5);
		graphics.text(font, cfg().hudXPercent + "%", VALUE_X, y + 5, 0xFFFFFFFF);
		graphics.text(font, "位置 Y", X0, y + ROW_HEIGHT + 5, 0xFFB0BEC5);
		graphics.text(font, cfg().hudYPercent + "%", VALUE_X, y + ROW_HEIGHT + 5, 0xFFFFFFFF);
		graphics.text(font, "HUD 宽度", X0, y + ROW_HEIGHT * 2 + 5, 0xFFB0BEC5);
		graphics.text(font, cfg().hudMaxWidth + " px", VALUE_X, y + ROW_HEIGHT * 2 + 5, 0xFFFFFFFF);
		graphics.text(font, "最多显示", X0, y + ROW_HEIGHT * 3 + 5, 0xFFB0BEC5);
		graphics.text(font, cfg().hudCompactLimit + " 条", VALUE_X, y + ROW_HEIGHT * 3 + 5, 0xFFFFFFFF);

		int[] r = hudRect();
		graphics.fill(r[0], r[1], r[2], r[3], 0x6610171C);
		com.easytaskmgr.client.TaskHud.frame(graphics, r[0], r[1], r[2], r[3], dragging ? 0xFFFFFF00 : 0xAAFFFF00);
		graphics.text(font, "待办清单 " + TaskStore.get().active().size(), r[0] + 5, r[1] + 4, 0xFFFFD54F);
		int lineY = r[1] + font.lineHeight + 8;
		List<Task> active = TaskStore.get().active();
		for (int i = 0; i < Math.min(3, active.size()); i++) {
			graphics.text(font, (i + 1) + ". " + active.get(i).displayText(), r[0] + 5, lineY, active.get(i).priority().color());
			lineY += font.lineHeight + 1;
		}
		if (active.isEmpty()) {
			graphics.text(font, "1. 示例待办", r[0] + 5, lineY, 0xFFFFFFFF);
			graphics.text(font, "2. 示例待办", r[0] + 5, lineY + font.lineHeight + 1, 0xFFFFFFFF);
		}

		graphics.text(font, "按住右侧 HUD 预览框拖动即可移动位置", X0, 26, 0xFF9FB3C8);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		ModConfig.get().save();
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
