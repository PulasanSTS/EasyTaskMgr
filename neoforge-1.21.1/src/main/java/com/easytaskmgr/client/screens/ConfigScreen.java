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
		addRenderableWidget(Button.builder(Component.translatable("ui.close"), button -> onClose())
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

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 105, 0xFFFFFFFF);
		super.render(graphics, mouseX, mouseY, partialTick);
		int active = ClientTasks.active().size();
		graphics.drawCenteredString(font, Component.literal(ClientUtil.t("hud.title", active)),
				width / 2, height / 2 + 105, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
