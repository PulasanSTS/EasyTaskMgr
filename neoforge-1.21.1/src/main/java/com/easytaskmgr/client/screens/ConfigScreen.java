package com.easytaskmgr.client.screens;

import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.ClientUtil;
import com.easytaskmgr.client.Hud;
import com.easytaskmgr.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 设置：HUD 显示项、位置、语言、音效。 */
public class ConfigScreen extends Screen {
	/** 正在拖动 HUD 预览（review: HUD 拖拽定位）。 */
	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;
	private static final int PREVIEW_W = 140;
	private static final int PREVIEW_H = 54;
	public ConfigScreen() {
		super(Component.literal("EasyTaskMgr"));
	}

	@Override
	protected void init() {
		ModConfig cfg = ModConfig.get();
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		int y = height / 2 - 80;
		int w = 150;
		int h = 20;
		int gap = 24;

		addRenderableWidget(toggle(left, y, w, h, "cfg.bg", cfg.hudBackground, v -> cfg.hudBackground = v));
		addRenderableWidget(toggle(right, y, w, h, "cfg.team", cfg.showTeammates, v -> cfg.showTeammates = v));
		y += gap;
		addRenderableWidget(toggle(left, y, w, h, "cfg.dist", cfg.showDistance, v -> cfg.showDistance = v));
		addRenderableWidget(toggle(right, y, w, h, "cfg.speed", cfg.showSpeed, v -> cfg.showSpeed = v));
		y += gap;
		addRenderableWidget(toggle(left, y, w, h, "cfg.arrow", cfg.showDirectionArrow, v -> cfg.showDirectionArrow = v));
		addRenderableWidget(toggle(right, y, w, h, "cfg.sound", cfg.soundEnabled, v -> cfg.soundEnabled = v));
		y += gap;

		addRenderableWidget(Button.builder(label("cfg.width", cfg.hudMaxWidth), button -> {
			cfg.hudMaxWidth = cfg.hudMaxWidth >= 360 ? 120 : cfg.hudMaxWidth + 20;
			cfg.save();
			button.setMessage(label("cfg.width", cfg.hudMaxWidth));
		}).bounds(left, y, w, h).build());
		addRenderableWidget(Button.builder(label("cfg.limit", cfg.hudCompactLimit), button -> {
			cfg.hudCompactLimit = cfg.hudCompactLimit >= 20 ? 1 : cfg.hudCompactLimit + 1;
			cfg.save();
			button.setMessage(label("cfg.limit", cfg.hudCompactLimit));
		}).bounds(right, y, w, h).build());
		y += gap;

		addRenderableWidget(Button.builder(languageLabel(cfg), button -> {
			cfg.language = switch (cfg.language) {
				case "auto" -> "zh_cn";
				case "zh_cn" -> "en_us";
				default -> "auto";
			};
			cfg.save();
			button.setMessage(languageLabel(cfg));
		}).bounds(left, y, w, h).build());
		addRenderableWidget(Button.builder(label("cfg.posX", cfg.hudXPercent), button -> {
			cfg.hudXPercent = cfg.hudXPercent <= 20 ? 99 : cfg.hudXPercent - 10;
			cfg.save();
			button.setMessage(label("cfg.posX", cfg.hudXPercent));
		}).bounds(right, y, w, h).build());
		y += gap;
		addRenderableWidget(Button.builder(label("cfg.posY", cfg.hudYPercent), button -> {
			cfg.hudYPercent = cfg.hudYPercent >= 80 ? 2 : cfg.hudYPercent + 5;
			cfg.save();
			button.setMessage(label("cfg.posY", cfg.hudYPercent));
		}).bounds(left, y, w, h).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.close")), button -> onClose())
				.bounds(right, y, w, h).build());
	}

	private Button toggle(int x, int y, int w, int h, String key, boolean initial, java.util.function.Consumer<Boolean> setter) {
		return Button.builder(toggleLabel(key, initial), button -> {
			boolean value = button.getMessage().getString().endsWith("✔");
			setter.accept(!value);
			ModConfig.get().save();
			button.setMessage(toggleLabel(key, !value));
		}).bounds(x, y, w, h).build();
	}

	private Component toggleLabel(String key, boolean value) {
		return Component.literal(ClientUtil.t(key) + ": " + (value ? "✔" : "✘"));
	}

	private Component label(String key, int value) {
		return Component.literal(ClientUtil.t(key) + ": " + value);
	}

	private Component languageLabel(ModConfig cfg) {
		return Component.literal(ClientUtil.t("cfg.language") + ": " + cfg.language);
	}

	private int previewX() {
		return (int) (width * ModConfig.get().hudXPercent / 100.0) - PREVIEW_W;
	}

	private int previewY() {
		return (int) (height * ModConfig.get().hudYPercent / 100.0);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 105, 0xFFFFD54F);
		super.render(graphics, mouseX, mouseY, partialTick);
		// 预览框：按住拖动即可改 HUD 位置（对应 Fabric 版拖拽）
		int px = previewX();
		int py = previewY();
		graphics.fill(px, py, px + PREVIEW_W, py + PREVIEW_H, 0x66FFFFFF);
		graphics.drawString(font, ClientUtil.t("cfg.hint"), px, py + PREVIEW_H + 4, 0xFFAAAAAA, true);
		graphics.drawString(font, ClientUtil.t("hud.title", ClientTasks.active().size()), px + 4, py + 4,
				0xFFFFFFFF, true);
		int active = ClientTasks.active().size();
		graphics.drawCenteredString(font, Component.literal(ClientUtil.t("hud.title", active)),
				width / 2, height / 2 + 105, 0xFFAAAAAA);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int px = previewX();
		int py = previewY();
		if (button == 0 && mouseX >= px && mouseX <= px + PREVIEW_W && mouseY >= py && mouseY <= py + PREVIEW_H) {
			dragging = true;
			dragOffsetX = mouseX - px;
			dragOffsetY = mouseY - py;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!dragging) {
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		}
		ModConfig cfg = ModConfig.get();
		double x = Math.max(0, Math.min(100, (mouseX - dragOffsetX + PREVIEW_W) / Math.max(1, width) * 100.0));
		double y = Math.max(0, Math.min(100, (mouseY - dragOffsetY) / Math.max(1, height) * 100.0));
		cfg.hudXPercent = (int) Math.round(x);
		cfg.hudYPercent = (int) Math.round(y);
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
