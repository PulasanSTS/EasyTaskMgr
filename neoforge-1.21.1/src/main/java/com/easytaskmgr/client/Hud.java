package com.easytaskmgr.client;

import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.share.SharedTasks;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

import java.util.ArrayList;
import java.util.List;

/**
 * 右上角待办 HUD（NeoForge 1.21.1 用 {@code LayeredDraw.Layer} 挂载）。
 *
 * <p>渲染顺序：标题 → 个人任务（数量受 {@code hudCompactLimit} 限制）→ 共享任务；
 * 每条任务按优先级染色，收集 / 坐标任务下面追加一行进度说明。
 *
 * <p>交互模式（按 V 进入）下记录每行的屏幕矩形，点击时用 {@link #click} 命中完成任务。
 */
public final class Hud implements LayeredDraw.Layer {
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;

	/** 顶部闪烁计时。 */
	private static int flashTicks;

	private static final List<Hit> HITS = new ArrayList<>();

	/** 一行可点击区域。 */
	private record Hit(int x0, int y0, int x1, int y1, String taskId, String sharedId) {
	}

	/** 待排布的任务行（尚未拿到屏幕坐标）。 */
	private record Row(String taskId, String sharedId) {
	}

	/** 一行要绘制的内容。 */
	private record Line(String text, int color) {
	}

	public static void flash() {
		flashTicks = 30;
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		// 先清掉旧的命中区：HUD 隐藏时不能留下可点区域（review #19）
		HITS.clear();
		if (mc.player == null || mc.level == null || ModConfig.get().hudHidden) {
			return;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
		ModConfig cfg = ModConfig.get();

		List<Task> personal = ClientTasks.active();
		List<ClientShared.Entry> shared = cfg.showTeammates ? ClientShared.all() : List.of();
		int total = personal.size() + shared.size();
		int limit = Math.max(1, cfg.hudCompactLimit);

		List<Line> lines = new ArrayList<>();
		List<Row> rows = new ArrayList<>();
		int titleColor = flashTicks > 0 ? 0xFFFFEB3B : 0xFFFFFFFF;
		lines.add(new Line(ClientUtil.t("hud.title", total), titleColor));

		for (int i = 0; i < personal.size() && i < limit; i++) {
			Task task = personal.get(i);
			int color = 0xFF000000 | Priority.byWeight(task.priority).color();
			lines.add(new Line((i + 1) + ". " + ClientTasks.priorityTag(task) + " " + ClientTasks.title(task), color));
			rows.add(new Row(task.id, null));
			String detail = detail(mc, task);
			if (!detail.isBlank()) {
				lines.add(new Line("   " + detail, 0xFFB0B0B0));
			}
		}
		for (ClientShared.Entry entry : shared) {
			String line = "[" + Priority.byWeight(entry.task.priority).label() + "] " + entry.owner + " · "
					+ ClientTasks.title(entry.task) + " " + stateText(entry);
			lines.add(new Line(line.trim(), 0xFF7FB8FF));
			rows.add(new Row(null, entry.id));
			String detail = detail(mc, entry);
			if (!detail.isBlank()) {
				lines.add(new Line("   " + detail, 0xFFB0B0B0));
			}
		}

		int maxWidth = 0;
		for (Line line : lines) {
			maxWidth = Math.max(maxWidth, mc.font.width(line.text()));
		}
		int width = Math.min(cfg.hudMaxWidth, maxWidth) + PADDING * 2;
		int height = lines.size() * LINE_HEIGHT + PADDING * 2;
		int x = (int) (mc.getWindow().getGuiScaledWidth() * cfg.hudXPercent / 100.0) - width;
		int y = (int) (mc.getWindow().getGuiScaledHeight() * cfg.hudYPercent / 100.0);

		if (cfg.hudBackground) {
			graphics.fill(x, y, x + width, y + height, 0x66000000);
		}
		if (cfg.hudInteractive) {
			graphics.fill(x, y, x + width, y + 1, 0xFFFFD54F);
		}

		int lineY = y + PADDING;
		for (Line line : lines) {
			graphics.drawString(mc.font, ClientUtil.clip(mc.font, line.text(), width - PADDING * 2), x + PADDING,
					lineY, line.color(), true);
			lineY += LINE_HEIGHT;
		}

		// 命中区域：标题占一行，任务行从第二行开始（review #20）
		int rowY = y + PADDING + LINE_HEIGHT;
		for (Row row : rows) {
			String detail = row.taskId() != null
					? personal.stream().filter(t -> t.id.equals(row.taskId())).findFirst().map(t -> detail(mc, t)).orElse("")
					: java.util.Optional.ofNullable(ClientShared.byId(row.sharedId())).map(e -> detail(mc, e)).orElse("");
			HITS.add(new Hit(x, rowY, x + width, rowY + LINE_HEIGHT, row.taskId(), row.sharedId()));
			rowY += LINE_HEIGHT * (detail.isBlank() ? 1 : 2);
		}
	}

	private static String detail(Minecraft mc, Task task) {
		if (task.type == TaskType.COORDINATE) {
			return coordinateDetail(mc, task);
		}
		return ClientTasks.progressText(mc, task);
	}

	private static String detail(Minecraft mc, ClientShared.Entry entry) {
		if (entry.mode == SharedTasks.MODE_COMBINED) {
			return ClientUtil.itemName(entry.task.target) + " " + entry.sumCount + "/" + entry.targetCount;
		}
		return ClientTasks.progressText(mc, entry.task);
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

	/**
	 * 处理 HUD 交互模式的点击。
	 *
	 * @return true 表示点击被 HUD 消费
	 */
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
