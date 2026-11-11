package com.easytaskmgr.client.screens;

import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.ClientUtil;
import com.easytaskmgr.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 设置界面，布局与 Fabric 版 {@code ConfigScreen} 对齐：
 * 左侧一列标签 + 数值，右侧是 `- / +` 小按钮；底部「重置位置 / 完成」。
 */
public class ConfigScreen extends Screen {
	private static final int X0 = 16;
	private static final int ROW_HEIGHT = 20;
	private static final int VALUE_X = X0 + 92;
	private static final int BTN1_X = X0 + 170;
	private static final int BTN2_X = X0 + 212;
	private static final int BTN_W = 38;
	private static final int TOP = 34;

	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;
	private static final int PREVIEW_W = 140;
	private static final int PREVIEW_H = 54;

	public ConfigScreen() {
		super(Component.literal(ClientUtil.t("cfg.title")));
	}

	@Override
	protected void init() {
		ModConfig cfg = ModConfig.get();
		int y = TOP;

		// HUD 位置 / 宽度 / 条数：数值 + 两个小按钮
		addRenderableWidget(small("-", BTN1_X, y, b -> adjust(-2, 0)));
		addRenderableWidget(small("+", BTN2_X, y, b -> adjust(2, 0)));
		addRenderableWidget(small("-", BTN1_X, y + ROW_HEIGHT, b -> adjust(0, -2)));
		addRenderableWidget(small("+", BTN2_X, y + ROW_HEIGHT, b -> adjust(0, 2)));
		addRenderableWidget(small("-", BTN1_X, y + ROW_HEIGHT * 2, b -> adjustWidth(-20)));
		addRenderableWidget(small("+", BTN2_X, y + ROW_HEIGHT * 2, b -> adjustWidth(20)));
		addRenderableWidget(small("-", BTN1_X, y + ROW_HEIGHT * 3, b -> adjustLimit(-1)));
		addRenderableWidget(small("+", BTN2_X, y + ROW_HEIGHT * 3, b -> adjustLimit(1)));

		int toggleY = y + ROW_HEIGHT * 4;
		addRenderableWidget(toggle(X0, toggleY, cfg.hudBackground, v -> cfg.hudBackground = v));
		addRenderableWidget(toggle(X0 + 160, toggleY, cfg.showTeammates, v -> cfg.showTeammates = v));
		addRenderableWidget(toggle(X0, toggleY + ROW_HEIGHT, cfg.showDistance, v -> cfg.showDistance = v));
		addRenderableWidget(toggle(X0 + 160, toggleY + ROW_HEIGHT, cfg.showSpeed, v -> cfg.showSpeed = v));
		addRenderableWidget(toggle(X0, toggleY + ROW_HEIGHT * 2, cfg.showDirectionArrow, v -> cfg.showDirectionArrow = v));
		addRenderableWidget(toggle(X0 + 160, toggleY + ROW_HEIGHT * 2, cfg.soundEnabled, v -> cfg.soundEnabled = v));

		int langY = toggleY + ROW_HEIGHT * 3;
		addRenderableWidget(Button.builder(languageLabel(cfg), b -> {
			cfg.language = switch (cfg.language) {
				case "auto" -> "zh_cn";
				case "zh_cn" -> "en_us";
				default -> "auto";
			};
			cfg.save();
			b.setMessage(languageLabel(cfg));
		}).bounds(X0 + 92, langY, 160, 16).build());

		int bottom = height - 28;
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("cfg.reset")), b -> {
			cfg.hudXPercent = 99;
			cfg.hudYPercent = 2;
			cfg.save();
		}).bounds(X0, bottom, 84, 18).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("cfg.done")), b -> onClose())
				.bounds(X0 + 92, bottom, 84, 18).build());
	}

	private Button small(String label, int x, int y, java.util.function.Consumer<Button> action) {
		return Button.builder(Component.literal(label), action::accept).bounds(x, y, BTN_W, 16).build();
	}

	private void adjust(int dx, int dy) {
		ModConfig cfg = ModConfig.get();
		cfg.hudXPercent = Math.max(0, Math.min(100, cfg.hudXPercent + dx));
		cfg.hudYPercent = Math.max(0, Math.min(100, cfg.hudYPercent + dy));
		cfg.save();
	}

	private void adjustWidth(int delta) {
		ModConfig cfg = ModConfig.get();
		cfg.hudMaxWidth = Math.max(120, Math.min(400, cfg.hudMaxWidth + delta));
		cfg.save();
	}

	private void adjustLimit(int delta) {
		ModConfig cfg = ModConfig.get();
		cfg.hudCompactLimit = Math.max(1, Math.min(20, cfg.hudCompactLimit + delta));
		cfg.save();
	}

	private Button toggle(int x, int y, boolean initial, java.util.function.Consumer<Boolean> setter) {
		return Button.builder(toggleLabel(initial), button -> {
			boolean next = !button.getMessage().getString().endsWith("✔");
			setter.accept(next);
			ModConfig.get().save();
			button.setMessage(toggleLabel(next));
		}).bounds(x, y, 84, 16).build();
	}

	private Component toggleLabel(boolean value) {
		return Component.literal(value ? "✔" : "✘");
	}

	private Component languageLabel(ModConfig cfg) {
		return Component.literal(ClientUtil.t("cfg.language") + ": " + cfg.language);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xC0000000);
		graphics.drawString(font, ClientUtil.t("cfg.title"), X0, 12, 0xFFFFD54F, true);

		ModConfig cfg = ModConfig.get();
		int y = TOP;
		row(graphics, ClientUtil.t("cfg.posX"), cfg.hudXPercent + "%", y);
		row(graphics, ClientUtil.t("cfg.posY"), cfg.hudYPercent + "%", y + ROW_HEIGHT);
		row(graphics, ClientUtil.t("cfg.width"), cfg.hudMaxWidth + ClientUtil.t("cfg.px"), y + ROW_HEIGHT * 2);
		row(graphics, ClientUtil.t("cfg.limit"), ClientUtil.t("cfg.items", cfg.hudCompactLimit), y + ROW_HEIGHT * 3);

		int toggleY = y + ROW_HEIGHT * 4;
		graphics.drawString(font, ClientUtil.t("cfg.bg"), X0 + 22, toggleY + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.team"), X0 + 182, toggleY + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.dist"), X0 + 22, toggleY + ROW_HEIGHT + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.speed"), X0 + 182, toggleY + ROW_HEIGHT + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.arrow"), X0 + 22, toggleY + ROW_HEIGHT * 2 + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.sound"), X0 + 182, toggleY + ROW_HEIGHT * 2 + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.language"), X0, toggleY + ROW_HEIGHT * 3 + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.hint"), X0, height - 52, 0xFF9E9E9E, true);

		// 预览框：拖动即可改 HUD 位置
		int px = (int) (width * cfg.hudXPercent / 100.0) - PREVIEW_W;
		int py = (int) (height * cfg.hudYPercent / 100.0);
		px = Math.max(2, Math.min(width - PREVIEW_W - 2, px));
		py = Math.max(TOP + ROW_HEIGHT * 8, Math.min(height - PREVIEW_H - 4, py));
		graphics.fill(px, py, px + PREVIEW_W, py + PREVIEW_H, 0xB412171C);
		graphics.fill(px, py, px + PREVIEW_W, py + 1, 0x66FFFFFF);
		graphics.drawString(font, ClientUtil.t("hud.title", ClientTasks.active().size()), px + 6, py + 4,
				0xFFFFD54F, true);

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void row(GuiGraphics graphics, String label, String value, int y) {
		graphics.drawString(font, label, X0, y + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, value, VALUE_X, y + 4, 0xFFFFFFFF, true);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		ModConfig cfg = ModConfig.get();
		int px = (int) (width * cfg.hudXPercent / 100.0) - PREVIEW_W;
		int py = (int) (height * cfg.hudYPercent / 100.0);
		if (button == 0 && mouseX >= px && mouseX <= px + PREVIEW_W && mouseY >= py && mouseY <= py + PREVIEW_H) {
			dragging = true;
			dragOffsetX = mouseX - px;
			dragOffsetY = mouseY - py;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!dragging) {
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		}
		ModConfig cfg = ModConfig.get();
		cfg.hudXPercent = (int) Math.max(0, Math.min(100,
				(mouseX - dragOffsetX + PREVIEW_W) / Math.max(1, width) * 100.0));
		cfg.hudYPercent = (int) Math.max(0, Math.min(100, (mouseY - dragOffsetY) / Math.max(1, height) * 100.0));
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging) {
			dragging = false;
			ModConfig.get().save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
