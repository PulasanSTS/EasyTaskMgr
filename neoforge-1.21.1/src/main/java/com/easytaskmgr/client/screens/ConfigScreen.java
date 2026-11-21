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
 * 左侧一列标签 + 数值，右侧 `- / +` 小按钮；底部「重置位置 / 完成」；
 * 右上角的 HUD 预览带边框和一行示例，可拖动改位置。
 *
 * <p>渲染顺序：预览面板（背景层）→ `super.render`（按钮等控件）→ 文字标签，
 * 这样预览或标签都不会盖住控件（PR #45 审查意见）。
 */
public class ConfigScreen extends Screen {
	private static final int X0 = 16;
	private static final int ROW_HEIGHT = 20;
	private static final int VALUE_X = X0 + 92;
	private static final int BTN1_X = X0 + 170;
	private static final int BTN2_X = X0 + 212;
	private static final int BTN_W = 38;
	private static final int TOGGLE_X2 = X0 + 252;
	private static final int TOGGLE_W = 84;
	private static final int TOP = 34;

	private static final int PREVIEW_W = 150;
	private static final int PREVIEW_H = 44;

	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public ConfigScreen() {
		super(Component.literal(ClientUtil.t("cfg.title")));
	}

	@Override
	protected void init() {
		ModConfig cfg = ModConfig.get();
		int y = TOP;

		addRenderableWidget(small("-", BTN1_X, y, b -> adjust(-2, 0)));
		addRenderableWidget(small("+", BTN2_X, y, b -> adjust(2, 0)));
		addRenderableWidget(small("-", BTN1_X, y + ROW_HEIGHT, b -> adjust(0, -2)));
		addRenderableWidget(small("+", BTN2_X, y + ROW_HEIGHT, b -> adjust(0, 2)));
		addRenderableWidget(small("-", BTN1_X, y + ROW_HEIGHT * 2, b -> adjustWidth(-20)));
		addRenderableWidget(small("+", BTN2_X, y + ROW_HEIGHT * 2, b -> adjustWidth(20)));
		addRenderableWidget(small("-", BTN1_X, y + ROW_HEIGHT * 3, b -> adjustLimit(-1)));
		addRenderableWidget(small("+", BTN2_X, y + ROW_HEIGHT * 3, b -> adjustLimit(1)));

		int toggleY = y + ROW_HEIGHT * 4;
		// 开关按钮放在标签右侧（标签在 X0 / X0+160），避免文字盖住 ✔ / ✘
		addRenderableWidget(toggle(X0 + 92, toggleY, cfg.hudBackground, v -> cfg.hudBackground = v));
		addRenderableWidget(toggle(TOGGLE_X2, toggleY, cfg.showTeammates, v -> cfg.showTeammates = v));
		addRenderableWidget(toggle(X0 + 92, toggleY + ROW_HEIGHT, cfg.showDistance, v -> cfg.showDistance = v));
		addRenderableWidget(toggle(TOGGLE_X2, toggleY + ROW_HEIGHT, cfg.showSpeed, v -> cfg.showSpeed = v));
		addRenderableWidget(toggle(X0 + 92, toggleY + ROW_HEIGHT * 2, cfg.showDirectionArrow, v -> cfg.showDirectionArrow = v));
		addRenderableWidget(toggle(TOGGLE_X2, toggleY + ROW_HEIGHT * 2, cfg.soundEnabled, v -> cfg.soundEnabled = v));

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
		}).bounds(x, y, TOGGLE_W, 16).build();
	}

	private Component toggleLabel(boolean value) {
		return Component.literal(value ? "✔" : "✘");
	}

	private Component languageLabel(ModConfig cfg) {
		return Component.literal(ClientUtil.t("cfg.language") + ": " + cfg.language);
	}

	private int previewX(ModConfig cfg) {
		int px = (int) (width * cfg.hudXPercent / 100.0) - PREVIEW_W;
		return Math.max(2, Math.min(width - PREVIEW_W - 2, px));
	}

	private int previewY(ModConfig cfg) {
		int py = (int) (height * cfg.hudYPercent / 100.0);
		return Math.max(TOP + ROW_HEIGHT * 8, Math.min(height - PREVIEW_H - 4, py));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		ModConfig cfg = ModConfig.get();

		// 1) 预览面板作为背景先画：被拖动到控件上时，控件仍在其上方
		int px = previewX(cfg);
		int py = previewY(cfg);
		graphics.fill(px, py, px + PREVIEW_W, py + PREVIEW_H, 0xB412171C);
		graphics.fill(px, py, px + PREVIEW_W, py + 1, 0x66FFFFFF);
		graphics.fill(px, py + PREVIEW_H - 1, px + PREVIEW_W, py + PREVIEW_H, 0x66FFFFFF);
		graphics.fill(px, py, px + 1, py + PREVIEW_H, 0x66FFFFFF);
		graphics.fill(px + PREVIEW_W - 1, py, px + PREVIEW_W, py + PREVIEW_H, 0x66FFFFFF);
		graphics.drawString(font, ClientUtil.t("hud.title", ClientTasks.active().size()), px + 6, py + 4,
				0xFFFFD54F, true);
		graphics.drawString(font, "1. " + ClientUtil.t("cfg.sample", 1), px + 6, py + 16, 0xFFECEFF1, true);
		graphics.drawString(font, "   Diamond 12/64", px + 6, py + 28, 0xFFB0BEC5, true);

		// 2) 控件（按钮、输入框）
		super.render(graphics, mouseX, mouseY, partialTick);

		// 3) 文字最后画，保证清晰（不被模糊背景覆盖）
		graphics.drawString(font, ClientUtil.t("cfg.title"), X0, 12, 0xFFFFD54F, true);

		int y = TOP;
		row(graphics, ClientUtil.t("cfg.posX"), cfg.hudXPercent + "%", y);
		row(graphics, ClientUtil.t("cfg.posY"), cfg.hudYPercent + "%", y + ROW_HEIGHT);
		row(graphics, ClientUtil.t("cfg.width"), cfg.hudMaxWidth + ClientUtil.t("cfg.px"), y + ROW_HEIGHT * 2);
		row(graphics, ClientUtil.t("cfg.limit"), ClientUtil.t("cfg.items", cfg.hudCompactLimit), y + ROW_HEIGHT * 3);

		int toggleY = y + ROW_HEIGHT * 4;
		graphics.drawString(font, ClientUtil.t("cfg.bg"), X0, toggleY + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.team"), X0 + 160, toggleY + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.dist"), X0, toggleY + ROW_HEIGHT + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.speed"), X0 + 160, toggleY + ROW_HEIGHT + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.arrow"), X0, toggleY + ROW_HEIGHT * 2 + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.sound"), X0 + 160, toggleY + ROW_HEIGHT * 2 + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.language"), X0, toggleY + ROW_HEIGHT * 3 + 4, 0xFFB0BEC5, true);
		graphics.drawString(font, ClientUtil.t("cfg.hint"), X0, height - 52, 0xFF9E9E9E, true);
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
		int px = previewX(cfg);
		int py = previewY(cfg);
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
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xC0000000);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
