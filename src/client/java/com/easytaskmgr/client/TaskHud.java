package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.i18n.I18n;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.task.TaskType;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 右上角常驻 HUD。
 *
 * <p>位置用屏幕百分比：{@code hudXPercent} 是右边缘、{@code hudYPercent} 是上边缘；
 * 面板宽度固定为配置值，长文本按宽度换行。
 *
 * <p>开启「HUD 交互」（V 键）后，鼠标会释放，点击普通任务即可直接完成。
 */
public final class TaskHud implements HudElement {
	public static final Identifier ID = EasyTaskMgr.id("hud");

	private record Row(String text, int color, String taskId) {
	}

	/** 可点击区域（渲染时记录，点击时命中测试）。 */
	public record Hit(String taskId, int x0, int y0, int x1, int y1) {
	}

	private static final List<Hit> HITS = new ArrayList<>();
	private static boolean interactive;

	public TaskHud() {
	}

	public static boolean isInteractive() {
		return interactive;
	}

	public static void setInteractive(boolean value) {
		interactive = value;
	}

	/** 点击 HUD：命中普通任务则标记完成。 */
	public static int hitCount() {
		return HITS.size();
	}

	public static boolean tryComplete(Minecraft mc, double x, double y) {
		for (Hit hit : List.copyOf(HITS)) {
			if (x >= hit.x0() && x <= hit.x1() && y >= hit.y0() && y <= hit.y1()) {
				return TaskStore.get().byId(hit.taskId()).map(task -> {
					if (task.done || task.remote) {
						return false;
					}
					ClientTasks.finish(task);
					TaskStore.get().save();
					ClientNet.markDirty();
					ClientTasks.toast("✓ " + I18n.t("notify.done", task.displayText()));
					return true;
				}).orElse(false);
			}
		}
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		HITS.clear();
		if (mc.player == null || mc.level == null) {
			return;
		}
		ModConfig cfg = ModConfig.get();
		if (cfg.hudHidden) {
			return;
		}

		List<Task> active = TaskStore.get().active();
		List<ClientShared.Entry> shared = cfg.showTeammates ? ClientShared.entries() : List.of();
		String flash = ClientTasks.flashText();
		boolean hasShared = false;
		for (ClientShared.Entry entry : shared) {
			if (!entry.done()) {
				hasShared = true;
				break;
			}
		}
		if (active.isEmpty() && !hasShared && flash == null && !interactive) {
			return;
		}

		Font font = mc.font;
		int maxWidth = Math.max(120, cfg.hudMaxWidth);
		List<Row> rows = new ArrayList<>();
		int shown = Math.min(active.size(), cfg.hudCompactLimit);

		if (flash != null) {
			for (String line : TextWrap.wrap(font, flash, maxWidth)) {
				rows.add(new Row(line, 0xFF7CFF9B, null));
			}
		}
		for (int i = 0; i < shown; i++) {
			Task task = active.get(i);
			List<String> titleLines = TextWrap.wrap(font, (i + 1) + ". " + task.displayText(), maxWidth);
			for (int lineIndex = 0; lineIndex < titleLines.size(); lineIndex++) {
				rows.add(new Row(titleLines.get(lineIndex), task.priority().color(), lineIndex == 0 ? task.id : null));
			}
			String detail = detail(mc, task);
			if (detail != null && !detail.isBlank()) {
				for (String line : TextWrap.wrap(font, detail, maxWidth - 10)) {
					rows.add(new Row("  " + line, 0xFFB0BEC5, null));
				}
			}
		}
		if (active.size() > shown) {
			rows.add(new Row(I18n.t("hud.more", active.size() - shown), 0xFF90A4AE, null));
		}
		if (!shared.isEmpty()) {
			for (ClientShared.Entry entry : shared) {
				if (entry.done()) {
					continue;
				}
				String tag = entry.mine() ? I18n.t("share.tagMine")
						: entry.claimed() ? I18n.t("share.tagClaimed")
						: entry.canClaim() ? I18n.t("share.tagClaim") : I18n.t("share.tagLocked");
				for (String line : TextWrap.wrap(font, entry.owner() + " · " + entry.task().displayText() + " " + tag, maxWidth)) {
					rows.add(new Row(line, 0xFF64B5F6, null));
				}
			}
		}
		if (interactive) {
			rows.add(new Row(I18n.t("hud.hint", Keybinds.HUD_INTERACT.getTranslatedKeyMessage().getString()), 0xFFFFD54F, null));
		}

		String header = I18n.t("hud.title", active.size());
		int textWidth = font.width(header);
		for (Row row : rows) {
			textWidth = Math.max(textWidth, font.width(row.text()));
		}
		int panelWidth = Math.min(maxWidth + 14, screenWidthSafe(mc));
		int lineHeight = font.lineHeight + 1;
		int headerHeight = font.lineHeight + 6;
		int panelHeight = headerHeight + rows.size() * lineHeight + 5;

		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		int right = Math.min(screenWidth - 2, Math.max(panelWidth + 4, (int) (screenWidth * (cfg.hudXPercent / 100.0))));
		int left = Math.max(2, right - panelWidth);
		int top = Math.max(2, Math.min(screenHeight - 10, (int) (screenHeight * (cfg.hudYPercent / 100.0))));
		int bottom = Math.min(screenHeight - 2, top + panelHeight);

		if (cfg.hudBackground) {
			graphics.fill(left, top, right, bottom, 0xB412171C);
		}
		frame(graphics, left, top, right, bottom, interactive ? 0xAAFFD54F : 0x66FFFFFF);

		graphics.text(font, header, left + 6, top + 4, 0xFFFFD54F);
		int y = top + headerHeight;
		for (Row row : rows) {
			graphics.text(font, row.text(), left + 6, y, row.color());
			if (row.taskId() != null && interactive && isClickable(active, row.taskId())) {
				HITS.add(new Hit(row.taskId(), left, y - 1, right, y + lineHeight));
			}
			y += lineHeight;
		}
	}

	private static boolean isClickable(List<Task> active, String taskId) {
		for (Task task : active) {
			if (task.id.equals(taskId)) {
				return !task.done && !task.remote && task.type == TaskType.NORMAL;
			}
		}
		return false;
	}

	private static int screenWidthSafe(Minecraft mc) {
		return Math.max(120, mc.getWindow().getGuiScaledWidth() - 8);
	}

	/** 手画四条边：{@code GuiGraphicsExtractor#outline} 的参数是 (x, y, width, height)，容易传错。 */
	public static void frame(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		if (x1 - x0 < 2 || y1 - y0 < 2) {
			return;
		}
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}

	/** 任务第二行：进度 / 坐标信息。 */
	public static String detail(Minecraft mc, Task task) {
		ModConfig cfg = ModConfig.get();
		return switch (task.type) {
			case COLLECT -> task.progressText();
			case ADVANCEMENT -> {
				String base = I18n.t("hud.advancement", shortId(task.target));
				String req = null;
				yield req == null || req.isBlank() ? base : base + I18n.t("hud.need", req);
			}
			case TIMER -> task.progressText() + (task.remindText.isBlank() ? "" : " · " + task.remindText);
			case COORDINATE -> coordinate(mc, task, cfg);
			case NORMAL -> null;
		};
	}

	private static String coordinate(Minecraft mc, Task task, ModConfig cfg) {
		if (mc.player == null) {
			return null;
		}
		double dx = task.x - mc.player.getX();
		double dz = task.z - mc.player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double bearing = Math.toDegrees(Math.atan2(-dx, dz));
		double relative = wrapDegrees(bearing - mc.player.getYRot());

		StringBuilder sb = new StringBuilder();
		if (cfg.showDirectionArrow) {
			sb.append(arrow(relative)).append(' ');
		}
		sb.append(String.format("%.0f, %.0f, %.0f", task.x, task.y, task.z));
		if (cfg.showDistance) {
			sb.append(String.format("  ·  %.0fm", distance));
			sb.append("  ·  ").append(direction(relative)).append(String.format(" %.0f°", Math.abs(relative)));
		}
		if (cfg.showSpeed) {
			sb.append(String.format("  ·  %.1f m/s", mc.player.getDeltaMovement().horizontalDistance() * 20.0));
		}
		return sb.toString();
	}

	/** 相对玩家视角的 8 方向箭头。 */
	private static String arrow(double relative) {
		double abs = Math.abs(relative);
		boolean right = relative > 0;
		if (abs < 22.5) {
			return "↑";
		}
		if (abs < 67.5) {
			return right ? "↗" : "↖";
		}
		if (abs < 112.5) {
			return right ? "→" : "←";
		}
		if (abs < 157.5) {
			return right ? "↘" : "↙";
		}
		return "↓";
	}

	private static String direction(double relative) {
		double abs = Math.abs(relative);
		if (abs < 22.5) {
			return I18n.t("dir.front");
		}
		if (abs < 67.5) {
			return relative > 0 ? I18n.t("dir.frontRight") : I18n.t("dir.frontLeft");
		}
		if (abs < 112.5) {
			return relative > 0 ? I18n.t("dir.right") : I18n.t("dir.left");
		}
		if (abs < 157.5) {
			return relative > 0 ? I18n.t("dir.backRight") : I18n.t("dir.backLeft");
		}
		return I18n.t("dir.back");
	}

	private static double wrapDegrees(double degrees) {
		double d = degrees % 360.0;
		if (d >= 180.0) {
			d -= 360.0;
		}
		if (d < -180.0) {
			d += 360.0;
		}
		return d;
	}

	private static String suffix(Task task) {
		String progress = task.progressText();
		return progress == null || progress.isBlank() ? "" : " " + progress;
	}

	private static String shortId(String id) {
		if (id == null) {
			return "";
		}
		int i = id.indexOf(':');
		return i >= 0 ? id.substring(i + 1) : id;
	}
}
