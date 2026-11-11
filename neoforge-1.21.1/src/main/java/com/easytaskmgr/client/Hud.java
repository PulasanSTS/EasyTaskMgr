package com.easytaskmgr.client;

import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.share.SharedTasks;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * 右上角待办 HUD。
 *
 * <p>样式与 Fabric 版 {@code TaskHud} 对齐：
 * 半透明深色面板 + 1px 边框、金色标题、按优先级着色的行、共享任务用蓝色/粉色、
 * 详情行统一灰蓝色；交互模式下底部多一行提示、边框变金色。
 */
public final class Hud implements net.minecraft.client.gui.LayeredDraw.Layer {
	private static final int BACKGROUND = 0xB412171C;
	private static final int BORDER = 0x66FFFFFF;
	private static final int BORDER_INTERACTIVE = 0xAAFFD54F;
	private static final int HEADER_COLOR = 0xFFFFD54F;
	private static final int DETAIL_COLOR = 0xFFB0BEC5;
	private static final int SHARED_COLOR = 0xFF64B5F6;
	private static final int SPECIAL_COLOR = 0xFFFF80AB;

	private static final int PADDING_X = 6;
	private static final int PADDING_TOP = 4;

	/** 顶部闪烁计时。 */
	private static int flashTicks;

	private static final List<Hit> HITS = new ArrayList<>();

	private record Hit(int x0, int y0, int x1, int y1, String taskId, String sharedId) {
	}

	/** 一行文本：prefix 用 prefixColor，正文用 color。 */
	private record Row(String prefix, int prefixColor, String text, int color, String taskId, String sharedId) {
	}

	public static void flash() {
		flashTicks = 30;
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		HITS.clear();
		if (mc.player == null || mc.level == null || ModConfig.get().hudHidden) {
			return;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
		ModConfig cfg = ModConfig.get();
		var font = mc.font;

		List<Task> personal = ClientTasks.active();
		List<ClientShared.Entry> shared = cfg.showTeammates ? ClientShared.all() : List.of();
		int limit = Math.max(1, cfg.hudCompactLimit);

		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < personal.size() && i < limit; i++) {
			Task task = personal.get(i);
			String prefix = "[" + Priority.byWeight(task.priority).label() + "] ";
			rows.add(new Row(prefix, 0xFF000000 | Priority.byWeight(task.priority).color(),
					ClientTasks.title(task), 0xFFECEFF1, task.id, null));
			String detail = task.type == TaskType.COORDINATE ? coordinateDetail(mc, task)
					: ClientTasks.progressText(mc, task);
			if (!detail.isBlank()) {
				rows.add(new Row("", 0, "  " + detail, DETAIL_COLOR, null, null));
			}
		}
		if (personal.size() > limit) {
			rows.add(new Row("", 0, "  …", DETAIL_COLOR, null, null));
		}
		for (ClientShared.Entry entry : shared) {
			int color = entry.visibility == 2 ? SPECIAL_COLOR : SHARED_COLOR;
			String state = stateText(entry);
			String title = ClientTasks.title(entry.task) + (state.isBlank() ? "" : " " + state);
			rows.add(new Row("[" + Priority.byWeight(entry.task.priority).label() + "] ",
					0xFF000000 | Priority.byWeight(entry.task.priority).color(), title, color, null, entry.id));
			String detail = entry.mode == SharedTasks.MODE_COMBINED
					? ClientUtil.itemName(entry.task.target) + "  " + entry.sumCount + "/" + Math.max(1, entry.targetCount)
					: ClientTasks.progressText(mc, entry.task);
			if (!detail.isBlank()) {
				rows.add(new Row("", 0, "  " + detail, DETAIL_COLOR, null, null));
			}
		}
		if (cfg.hudInteractive) {
			rows.add(new Row("", 0, ClientUtil.t("hud.hint", "V"), HEADER_COLOR, null, null));
		}

		String header = ClientUtil.t("hud.title", personal.size() + shared.size());
		int textWidth = font.width(header);
		for (Row row : rows) {
			textWidth = Math.max(textWidth, font.width(row.prefix()) + font.width(row.text()));
		}
		int corner = 2;
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		int panelWidth = Math.min(cfg.hudMaxWidth + 14, screenWidth - 2);
		int lineHeight = font.lineHeight + 1;
		int headerHeight = font.lineHeight + 6;
		int panelHeight = headerHeight + rows.size() * lineHeight + 5;
		int right = Math.min(screenWidth - 2,
				Math.max(panelWidth + 4, (int) (screenWidth * (cfg.hudXPercent / 100.0))));
		int left = Math.max(2, right - panelWidth);
		int top = Math.max(2, Math.min(screenHeight - 10, (int) (screenHeight * (cfg.hudYPercent / 100.0))));
		int bottom = Math.min(screenHeight - 2, top + panelHeight);

		if (cfg.hudBackground) {
			graphics.fill(left, top, right, bottom, BACKGROUND);
		}
		int border = cfg.hudInteractive ? BORDER_INTERACTIVE : BORDER;
		graphics.fill(left, top, right, top + 1, border);
		graphics.fill(left, bottom - 1, right, bottom, border);
		graphics.fill(left, top, left + 1, bottom, border);
		graphics.fill(right - 1, top, right, bottom, border);

		int headerColor = flashTicks > 0 ? 0xFFFFFFFF : HEADER_COLOR;
		graphics.drawString(font, ClientUtil.clip(font, header, panelWidth - PADDING_X * 2),
				left + PADDING_X, top + PADDING_TOP, headerColor, true);

		int y = top + headerHeight;
		for (Row row : rows) {
			int x = left + PADDING_X;
			if (!row.prefix().isEmpty()) {
				graphics.drawString(font, row.prefix(), x, y, row.prefixColor(), true);
				x += font.width(row.prefix());
			}
			graphics.drawString(font, ClientUtil.clip(font, row.text(), right - x - 2), x, y, row.color(), true);
			if (cfg.hudInteractive) {
				if (row.taskId() != null) {
					HITS.add(new Hit(left, y - 1, right, y + lineHeight, row.taskId(), null));
				} else if (row.sharedId() != null) {
					HITS.add(new Hit(left, y - 1, right, y + lineHeight, null, row.sharedId()));
				}
			}
			y += lineHeight;
		}
	}

	private static String coordinateDetail(Minecraft mc, Task task) {
		ModConfig cfg = ModConfig.get();
		List<String> parts = new ArrayList<>();
		if (cfg.showDistance) {
			double dx = mc.player.getX() - task.x;
			double dz = mc.player.getZ() - task.z;
			parts.add((int) Math.sqrt(dx * dx + dz * dz) + "m");
		}
		if (cfg.showSpeed) {
			parts.add(String.format("%.1f m/s", mc.player.getDeltaMovement().horizontalDistance() * 20.0));
		}
		if (cfg.showDirectionArrow) {
			double dx = task.x - mc.player.getX();
			double dz = task.z - mc.player.getZ();
			double angle = Math.toDegrees(Math.atan2(dx, -dz));
			String arrow = Math.abs(angle) < 22.5 ? "↑" : angle < 67.5 ? "↗" : angle < 112.5 ? "→"
					: angle < 157.5 ? "↘" : angle > -67.5 ? "↖" : angle > -112.5 ? "←" : angle > -157.5 ? "↙" : "↓";
			parts.add(arrow);
		}
		return String.join(" · ", parts);
	}

	private static String stateText(ClientShared.Entry entry) {
		if (entry.done) {
			return ClientUtil.t("hud.state.done");
		}
		if (entry.requiresAll()) {
			return entry.claimsDone + "/" + Math.max(1, entry.participants) + " " + ClientUtil.t("hud.state.partial");
		}
		if (entry.claimed) {
			return ClientUtil.t("hud.state.claimed");
		}
		return "";
	}

	/** 处理 HUD 交互模式的点击；返回 true 表示事件已被消费。 */
	public static boolean click(double mouseX, double mouseY) {
		for (Hit hit : HITS) {
			if (mouseX >= hit.x0() && mouseX <= hit.x1() && mouseY >= hit.y0() && mouseY <= hit.y1()) {
				if (hit.taskId() != null) {
					ClientTasks.active().stream()
							.filter(t -> t.id.equals(hit.taskId()))
							.findFirst()
							.ifPresent(ClientTasks::complete);
					return true;
				}
				ClientShared.Entry entry = ClientShared.byId(hit.sharedId());
				if (entry != null) {
					ClientShared.click(entry);
					return true;
				}
			}
		}
		return false;
	}
}
