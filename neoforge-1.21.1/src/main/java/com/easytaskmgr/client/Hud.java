package com.easytaskmgr.client;

import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.share.SharedTasks;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 右上角待办 HUD（NeoForge 1.21.1 用 {@code LayeredDraw.Layer} 挂载）。
 *
 * <p>渲染顺序：标题 → 个人任务（数量受 {@code hudCompactLimit} 限制）→ 共享任务；
 * 每条任务按优先级染色，收集 / 坐标任务下面追加一行进度说明。
 *
 * <p>交互模式（按 V 进入）下会记录每行的屏幕矩形，点击时用 {@link #click} 命中完成任务。
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

	public static void flash() {
		flashTicks = 30;
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || osc().hudHidden) {
			return;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
		HITS.clear();
		ModConfig cfg = osc();

		List<Task> personal = ClientTasks.active();
		List<ClientShared.Entry> shared = cfg.showTeammates ? ClientShared.all() : List.of();
		int total = personal.size() + shared.size();
		int limit = Math.max(1, cfg.hudCompactLimit);

		// 先算宽度：标题 + 各行文本
		int maxWidth = mc.font.width(ClientUtil.t("hud.title", total));
		List<String> lines = new ArrayList<>();
		lines.add(null); // 标题占位
		for (int i = 0; i < personal.size() && i < limit; i++) {
			Task task = personal.get(i);
			String line = (i + 1) + ". " + ClientTasks.priorityTag(task) + " " + ClientTasks.title(task);
			lines.add(line);
			maxWidth = Math.max(maxWidth, mc.font.width(line));
			String detail = ClientTasks.progressText(mc, task);
			if (!detail.isBlank()) {
				lines.add("   " + detail);
				maxWidth = Math.max(maxWidth, mc.font.width("   " + detail));
			}
		}
		for (ClientShared.Entry entry : shared) {
			String line = "[${prio}] ${owner} · ${title} ${state}".replace("${prio}", Priority.byWeight(entry.task.priority).label())
					.replace("${owner}", entry.owner)
					.replace("${title}", ClientTasks.title(entry.task))
					.replace("${state}", stateText(entry));
			lines.add(line);
			maxWidth = Math.max(maxWidth, mc.font.width(line));
			String detail = ClientTasks.progressText(mc, entry.task);
			if (entry.mode == SharedTasks.MODE_COMBINED) {
				detail = ClientUtil.itemName(entry.task.target) + " " + entry.sumCount + "/" + entry.targetCount;
			}
			if (!detail.isBlank()) {
				lines.add("   " + detail);
				maxWidth = Math.max(maxWidth, mc.font.width("   " + detail));
			}
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
		int index = 0;
		for (String line : lines) {
			if (line == null) {
				int color = flashTicks > 0 ? 0xFFFFEB3B : 0xFFFFFFFF;
				graphics.drawString(mc.font, ClientUtil.t("hud.title", total), x + PADDING, lineY, color, true);
			} else {
				graphics.drawString(mc.font, ClientUtil.clip(mc.font, line, width - PADDING * 2), x + PADDING, lineY,
						0xFFE0E0E0, true);
			}
			lineY += LINE_HEIGHT;
			index++;
		}

		// 命中区域：个人任务按行号，共享任务在个人任务之后
		int rowY = y + PADDING;
		int personalRows = Math.min(personal.size(), limit);
		for (int i = 0; i < personalRows; i++) {
			HITS.add(new Hit(x, rowY, x + width, rowY + LINE_HEIGHT, personal.get(i).id, null));
			rowY += LINE_HEIGHT;
			if (!ClientTasks.progressText(mc, personal.get(i)).isBlank()) {
				rowY += LINE_HEIGHT;
			}
		}
		for (ClientShared.Entry entry : shared) {
			HITS.add(new Hit(x, rowY, x + width, rowY + LINE_HEIGHT, null, entry.id));
			rowY += LINE_HEIGHT;
			if (!detailIsBlank(mc, entry)) {
				rowY += LINE_HEIGHT;
			}
		}
	}

	private static boolean detailIsBlank(Minecraft mc, ClientShared.Entry entry) {
		if (entry.mode == SharedTasks.MODE_COMBINED) {
			return false;
		}
		return ClientTasks.progressText(mc, entry.task).isBlank();
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

	public static ModConfig osc() {
		return ModConfig.get();
	}

	/**
	 * 处理 HUD 交互模式的点击。
	 *
	 * @return true 表示点击被 HUD 消费，不再传给游戏
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

	/** 供界面显示用：HUD 上共享任务的状态文本。 */
	public static Component sharedStatus(ClientShared.Entry entry) {
		return Component.literal(stateText(entry));
	}
}
