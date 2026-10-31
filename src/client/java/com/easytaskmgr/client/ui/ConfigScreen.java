package com.easytaskmgr.client.ui;

import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 设置界面（支持中英文）。
 *
 * <p>左侧紧凑排布设置项，右侧是 HUD 预览框；按住预览框拖动即可移动 HUD。
 * 语言：自动（跟随 Minecraft）/ 简体中文 / English。
 */
public class ConfigScreen extends Screen {
	private static final int X0 = 16;
	private static final int ROW_HEIGHT = 20;
	private static final int VALUE_X = X0 + 70;
	private static final int BTN1_X = X0 + 108;
	private static final int BTN2_X = X0 + 146;
	private static final int BTN_W = 38;

	private final Screen parent;
	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public ConfigScreen(Screen parent) {
		super(Component.literal(I18n.t("cfg.title")));
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
		int y = 34;

		addRenderableWidget(Button.builder(Component.literal("-"), b -> move(-2, 0)).bounds(BTN1_X, y, BTN_W, 16).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> move(2, 0)).bounds(BTN2_X, y, BTN_W, 16).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> move(0, -2)).bounds(BTN1_X, y + ROW_HEIGHT, BTN_W, 16).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> move(0, 2)).bounds(BTN2_X, y + ROW_HEIGHT, BTN_W, 16).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			cfg().hudMaxWidth = Math.max(120, cfg().hudMaxWidth - 20);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN1_X, y + ROW_HEIGHT * 2, BTN_W, 16).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			cfg().hudMaxWidth = Math.min(400, cfg().hudMaxWidth + 20);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN2_X, y + ROW_HEIGHT * 2, BTN_W, 16).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			cfg().hudCompactLimit = Math.max(1, cfg().hudCompactLimit - 1);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN1_X, y + ROW_HEIGHT * 3, BTN_W, 16).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			cfg().hudCompactLimit = Math.min(20, cfg().hudCompactLimit + 1);
			cfg().save();
			rebuildWidgets();
		}).bounds(BTN2_X, y + ROW_HEIGHT * 3, BTN_W, 16).build());

		int langY = y + ROW_HEIGHT * 4;
		addRenderableWidget(Button.builder(languageValue(), b -> {
			cfg().language = nextLanguage(cfg().language);
			cfg().save();
			com.easytaskmgr.client.Language.apply();
			rebuildWidgets();
		}).bounds(BTN1_X, langY, 110, 16).build());

		int toggleY = langY + 24;
		addRenderableWidget(toggle(X0, toggleY, "cfg.bg", cfg().hudBackground, v -> cfg().hudBackground = v));
		addRenderableWidget(toggle(X0 + 88, toggleY, "cfg.dist", cfg().showDistance, v -> cfg().showDistance = v));
		addRenderableWidget(toggle(X0, toggleY + ROW_HEIGHT, "cfg.speed", cfg().showSpeed, v -> cfg().showSpeed = v));
		addRenderableWidget(toggle(X0 + 88, toggleY + ROW_HEIGHT, "cfg.team", cfg().showTeammates, v -> cfg().showTeammates = v));
		addRenderableWidget(toggle(X0, toggleY + ROW_HEIGHT * 2, "cfg.sound", cfg().soundEnabled, v -> cfg().soundEnabled = v));
		addRenderableWidget(toggle(X0 + 88, toggleY + ROW_HEIGHT * 2, "cfg.share", cfg().shareEnabled, v -> {
			cfg().shareEnabled = v;
			if (!v) {
				ClientNet.reset();
			}
		}));

		int bottom = height - 34;
		addRenderableWidget(Button.builder(Component.literal(I18n.t("cfg.reset")), b -> {
			cfg().hudXPercent = 99;
			cfg().hudYPercent = 2;
			cfg().save();
			rebuildWidgets();
		}).bounds(X0, bottom, 84, 18).build());

		addRenderableWidget(Button.builder(Component.literal(I18n.t("cfg.done")), b -> onClose())
				.bounds(X0 + 92, bottom, 84, 18).build());
	}

	private static String nextLanguage(String current) {
		return switch (current == null ? "auto" : current) {
			case "auto" -> "zh_cn";
			case "zh_cn" -> "en_us";
			default -> "auto";
		};
	}

	private Component languageValue() {
		String value = switch (cfg().language == null ? "auto" : cfg().language) {
			case "zh_cn" -> I18n.t("cfg.lang.zh");
			case "en_us" -> I18n.t("cfg.lang.en");
			default -> I18n.t("cfg.lang.auto");
		};
		return Component.literal(value);
	}

	private Button toggle(int x, int y, String key, boolean initial, java.util.function.Consumer<Boolean> setter) {
		return Button.builder(label(key, initial), b -> {
			boolean next = !read(key);
			setter.accept(next);
			cfg().save();
			b.setMessage(label(key, next));
		}).bounds(x, y, 84, 16).build();
	}

	private boolean read(String key) {
		return switch (key) {
			case "cfg.bg" -> cfg().hudBackground;
			case "cfg.dist" -> cfg().showDistance;
			case "cfg.speed" -> cfg().showSpeed;
			case "cfg.team" -> cfg().showTeammates;
			case "cfg.sound" -> cfg().soundEnabled;
			case "cfg.share" -> cfg().shareEnabled;
			default -> false;
		};
	}

	private Component label(String key, boolean value) {
		return Component.literal(I18n.t(key) + (value ? " ✓" : " ✗"));
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
		graphics.text(font, I18n.t("cfg.title"), X0, 12, 0xFFFFD54F);

		int y = 34;
		graphics.text(font, I18n.t("cfg.posX"), X0, y + 4, 0xFFB0BEC5);
		graphics.text(font, cfg().hudXPercent + "%", VALUE_X, y + 4, 0xFFFFFFFF);
		graphics.text(font, I18n.t("cfg.posY"), X0, y + ROW_HEIGHT + 4, 0xFFB0BEC5);
		graphics.text(font, cfg().hudYPercent + "%", VALUE_X, y + ROW_HEIGHT + 4, 0xFFFFFFFF);
		graphics.text(font, I18n.t("cfg.width"), X0, y + ROW_HEIGHT * 2 + 4, 0xFFB0BEC5);
		graphics.text(font, cfg().hudMaxWidth + I18n.t("cfg.px"), VALUE_X, y + ROW_HEIGHT * 2 + 4, 0xFFFFFFFF);
		graphics.text(font, I18n.t("cfg.limit"), X0, y + ROW_HEIGHT * 3 + 4, 0xFFB0BEC5);
		graphics.text(font, I18n.t("cfg.items", cfg().hudCompactLimit), VALUE_X, y + ROW_HEIGHT * 3 + 4, 0xFFFFFFFF);
		graphics.text(font, I18n.t("cfg.language"), X0, y + ROW_HEIGHT * 4 + 4, 0xFFB0BEC5);

		int[] r = hudRect();
		graphics.fill(r[0], r[1], r[2], r[3], 0x6610171C);
		com.easytaskmgr.client.TaskHud.frame(graphics, r[0], r[1], r[2], r[3], dragging ? 0xFFFFFF00 : 0xAAFFFF00);
		graphics.text(font, I18n.t("hud.title", TaskStore.get().active().size()), r[0] + 5, r[1] + 4, 0xFFFFD54F);
		int lineY = r[1] + font.lineHeight + 8;
		List<Task> active = TaskStore.get().active();
		for (int i = 0; i < Math.min(3, active.size()); i++) {
			graphics.text(font, (i + 1) + ". " + active.get(i).displayText(), r[0] + 5, lineY, active.get(i).priority().color());
			lineY += font.lineHeight + 1;
		}
		if (active.isEmpty()) {
			graphics.text(font, I18n.t("cfg.sample", 1), r[0] + 5, lineY, 0xFFFFFFFF);
			graphics.text(font, I18n.t("cfg.sample", 2), r[0] + 5, lineY + font.lineHeight + 1, 0xFFFFFFFF);
		}

		graphics.text(font, I18n.t("cfg.hint"), X0, height - 12, 0xFF9FB3C8);

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
