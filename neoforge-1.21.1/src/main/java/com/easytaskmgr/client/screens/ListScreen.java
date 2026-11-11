package com.easytaskmgr.client.screens;

import com.easytaskmgr.client.ClientShared;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.ClientUtil;
import com.easytaskmgr.task.Priority;
import com.easytaskmgr.task.Task;
import com.easytaskmgr.task.TaskCsv;
import com.easytaskmgr.task.TaskStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 待办列表，布局与 Fabric 版 {@code TaskListScreen} 对齐：
 * 深色遮罩、金色标题、每行 34px（前缀 / 标题换行 / 右侧进度）、右侧滚动条、底部快捷键提示与按钮。
 */
public class ListScreen extends Screen {
	private static final int LIST_TOP = 58;
	private static final int ROW_HEIGHT = 34;
	private static final int TEXT_WIDTH = 210;

	private int scroll;
	private String status = "";
	private long statusUntil;
	private static boolean showDone = true;

	public ListScreen() {
		super(Component.literal(ClientUtil.t("list.title")));
	}

	/** 列表里的一行。 */
	private record Row(int y, String prefix, int prefixColor, String text, String detail, String id,
			boolean shared, boolean done, int visibility) {
	}

	@Override
	protected void init() {
		int y = height - 26;
		int x = width / 2 - 195;
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.new")), b -> minecraft.setScreen(new EditScreen(null)))
				.bounds(x, y, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.settings")), b -> minecraft.setScreen(new ConfigScreen()))
				.bounds(x + 62, y, 50, 20).build());
		addRenderableWidget(Button.builder(doneToggleLabel(), b -> {
			showDone = !showDone;
			b.setMessage(doneToggleLabel());
		}).bounds(x + 114, y, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.clearDone")), b -> {
			TaskStore.get().clearCompleted();
			TaskStore.get().save();
		}).bounds(x + 186, y, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("CSV ↑"), b -> exportCsv())
				.bounds(x + 248, y, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("CSV ↓"), b -> importCsv())
				.bounds(x + 300, y, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal(ClientUtil.t("ui.close")), b -> onClose())
				.bounds(x + 352, y, 50, 20).build());
	}

	private static Component doneToggleLabel() {
		return Component.literal(ClientUtil.t(showDone ? "ui.hideDone" : "ui.showDone"));
	}

	private static Path csvFile() {
		return FMLPaths.CONFIGDIR.get().resolve("easytaskmgr-tasks.csv");
	}

	private void exportCsv() {
		try {
			Files.writeString(csvFile(), TaskCsv.toCsv(TaskStore.get().all()), StandardCharsets.UTF_8);
			status = ClientUtil.t("csv.exported", TaskStore.get().all().size(), csvFile().getFileName());
		} catch (Exception e) {
			status = ClientUtil.t("csv.failed");
		}
		statusUntil = System.currentTimeMillis() + 5000;
	}

	private void importCsv() {
		try {
			TaskCsv.Result result = TaskCsv.fromCsv(Files.readString(csvFile(), StandardCharsets.UTF_8));
			int shared = 0;
			for (Task task : result.tasks()) {
				task.id = java.util.UUID.randomUUID().toString();
				task.normalize();
				boolean canShare = task.type != com.easytaskmgr.task.TaskType.ADVANCEMENT
						&& task.type != com.easytaskmgr.task.TaskType.TIMER;
				if (task.visibility > 0 && canShare && ClientShared.available()) {
					ClientShared.publish(task);
					shared++;
					continue;
				}
				task.visibility = 0;
				TaskStore.get().add(task);
			}
			TaskStore.get().save();
			status = ClientUtil.t("csv.importedMixed", result.tasks().size(), shared,
					result.tasks().size() - shared);
			if (result.skipped() > 0) {
				status += ClientUtil.t("csv.skippedSuffix", result.skipped());
			}
		} catch (Exception e) {
			status = ClientUtil.t("csv.failed");
		}
		statusUntil = System.currentTimeMillis() + 5000;
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		int index = 0;
		List<Task> active = ClientTasks.active();
		for (Task task : active) {
			int y = LIST_TOP + (index - scroll) * ROW_HEIGHT;
			rows.add(new Row(y, "[" + Priority.byWeight(task.priority).label() + "] ",
					0xFF000000 | Priority.byWeight(task.priority).color(), ClientTasks.title(task),
					ClientTasks.progressText(minecraft, task), task.id, false, false, 0));
			index++;
		}
		for (ClientShared.Entry entry : ClientShared.all()) {
			if (!showDone && entry.done) {
				continue;
			}
			int y = LIST_TOP + (index - scroll) * ROW_HEIGHT;
			rows.add(new Row(y, "[" + Priority.byWeight(entry.task.priority).label() + "] ",
					0xFF000000 | Priority.byWeight(entry.task.priority).color(),
					entry.owner + " · " + ClientTasks.title(entry.task), ClientTasks.progressText(minecraft, entry.task),
					entry.id, true, entry.done, entry.visibility));
			index++;
		}
		if (showDone) {
			for (Task task : ClientTasks.completed()) {
				int y = LIST_TOP + (index - scroll) * ROW_HEIGHT;
				rows.add(new Row(y, ClientUtil.t("list.donePrefix"), 0xFF757575, ClientTasks.title(task),
						ClientTasks.progressText(minecraft, task), task.id, false, true, 0));
				index++;
			}
		}
		return rows;
	}

	private int visibleRows() {
		return Math.max(3, (height - LIST_TOP - 46) / ROW_HEIGHT);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Fabric 版使用整屏深色遮罩，而不是原版模糊背景
		graphics.fill(0, 0, width, height, 0xE0101014);
		ClientUtil.centered(graphics, font, title.getString(), width / 2, 22, 0xFFFFD54F);

		int total = rows().size();
		String info = ClientUtil.t("list.range", total == 0 ? 0 : 1,
				Math.min(total, visibleRows()), total) + ClientUtil.t("list.scrollHint");
		ClientUtil.centered(graphics, font, info, width / 2, 40, 0xFF9E9E9E);

		List<Row> rows = rows();
		if (rows.isEmpty()) {
			ClientUtil.centered(graphics, font, ClientUtil.t("list.empty"), width / 2, LIST_TOP + 20, 0xFF9E9E9E);
		}
		int cx = width / 2;
		for (Row row : rows) {
			if (row.y() < LIST_TOP || row.y() + ROW_HEIGHT > height - 46) {
				continue;
			}
			int color = row.done() ? 0xFF757575
					: row.shared() ? (row.visibility() == 2 ? 0xFFFF80AB : 0xFF64B5F6) : 0xFFECEFF1;
			graphics.drawString(font, row.prefix(), cx - 184, row.y() + 2, row.prefixColor(), true);
			int prefixWidth = font.width(row.prefix());
			List<String> lines = ClientUtil.wrap(font, row.text(), TEXT_WIDTH);
			for (int i = 0; i < lines.size() && i < 2; i++) {
				graphics.drawString(font, lines.get(i), cx - 184 + (i == 0 ? prefixWidth : 0), row.y() + 2 + i * 11,
						color, true);
			}
			if (row.detail() != null && !row.detail().isBlank()) {
				graphics.drawString(font, ClientUtil.clip(font, row.detail(), 90), cx + 40, row.y() + 2,
						0xFF9FB3C8, true);
			}
			if (!row.shared()) {
				graphics.drawString(font, "[改]", cx + 140, row.y() + 2, 0xFF9AD9FF, true);
				graphics.drawString(font, "[删]", cx + 170, row.y() + 2, 0xFFFF9E9E, true);
			} else if (row.shared() && ClientShared.byId(row.id()) != null && ClientShared.byId(row.id()).mine) {
				graphics.drawString(font, "[撤销]", cx + 150, row.y() + 2, 0xFFFF9E9E, true);
			}
		}

		// 滚动条
		int barX = width - 6;
		int barTop = LIST_TOP;
		int barHeight = visibleRows() * ROW_HEIGHT - 4;
		graphics.fill(barX, barTop, barX + 3, barTop + barHeight, 0x33FFFFFF);
		if (total > visibleRows()) {
			int thumb = Math.max(12, barHeight * visibleRows() / total);
			int thumbTop = barTop + (barHeight - thumb) * scroll / Math.max(1, total - visibleRows());
			graphics.fill(barX, thumbTop, barX + 3, thumbTop + thumb, 0xAAFFD54F);
		}

		super.render(graphics, mouseX, mouseY, partialTick);

		if (!status.isBlank() && System.currentTimeMillis() < statusUntil) {
			graphics.drawString(font, status, 6, height - 12, 0xFF8BC34A, true);
		} else {
			graphics.drawString(font, ClientUtil.t("list.footer",
					EtmClientKeys.quickAdd(), EtmClientKeys.list(), EtmClientKeys.config(), EtmClientKeys.toggleHud()),
					6, height - 12, 0xFF78909C, true);
		}
	}

	/** 快捷读取键位名称（避免 screens 包直接依赖 EtmClient 的内部字段顺序）。 */
	private static final class EtmClientKeys {
		static String quickAdd() {
			return com.easytaskmgr.client.EtmClient.KEY_QUICK_ADD.getTranslatedKeyMessage().getString();
		}

		static String list() {
			return com.easytaskmgr.client.EtmClient.KEY_LIST.getTranslatedKeyMessage().getString();
		}

		static String config() {
			return com.easytaskmgr.client.EtmClient.KEY_CONFIG.getTranslatedKeyMessage().getString();
		}

		static String toggleHud() {
			return com.easytaskmgr.client.EtmClient.KEY_TOGGLE_HUD.getTranslatedKeyMessage().getString();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int cx = width / 2;
		for (Row row : rows()) {
			if (row.y() < LIST_TOP || row.y() + ROW_HEIGHT > height - 46) {
				continue;
			}
			if (mouseY < row.y() || mouseY > row.y() + ROW_HEIGHT - 2) {
				continue;
			}
			if (row.shared()) {
				ClientShared.Entry entry = ClientShared.byId(row.id());
				if (entry == null) {
					return true;
				}
				if (entry.mine && mouseX >= cx + 140) {
					ClientShared.revoke(entry.id);
				} else {
					ClientShared.click(entry);
				}
				return true;
			}
			Task task = TaskStore.get().byId(row.id()).orElse(null);
			if (task == null) {
				return true;
			}
			if (mouseX >= cx + 135 && mouseX < cx + 165) {
				minecraft.setScreen(new EditScreen(task));
			} else if (mouseX >= cx + 165) {
				ClientTasks.remove(task);
			} else if (task.done) {
				TaskStore.get().toggleDone(task);
				TaskStore.get().save();
			} else {
				ClientTasks.complete(task);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int total = rows().size();
		int maxScroll = Math.max(0, total - visibleRows());
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY)));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
