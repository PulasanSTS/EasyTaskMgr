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
 * 深色遮罩、金色标题、行内 `[优先级] 标题（可换行）/ 右侧进度`、右侧滚动条、底部键位提示。
 *
 * <p>行高按标题换行数动态计算，长标题会完整显示（issue #38）。
 */
public class ListScreen extends Screen {
	private static final int LIST_TOP = 58;
	private static final int MIN_ROW_HEIGHT = 20;
	private static final int LINE_HEIGHT = 11;
	private static final int TEXT_WIDTH = 210;

	private int scroll;
	private String status = "";
	private long statusUntil;
	private static boolean showDone = true;

	public ListScreen() {
		super(Component.literal(ClientUtil.t("list.title")));
	}

	/** 一行任务（已换行、未定位）。 */
	private record RowData(List<String> lines, String prefix, int prefixColor, String detail, String id,
			boolean shared, boolean done, int visibility, int height) {
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

	private RowData rowData(String text, String prefix, int prefixColor, String detail, String id,
			boolean shared, boolean done, int visibility, int reservedWidth) {
		List<String> lines = ClientUtil.wrap(font, text, Math.max(40, TEXT_WIDTH - reservedWidth));
		if (lines.isEmpty()) {
			lines = List.of("");
		}
		int height = Math.max(MIN_ROW_HEIGHT, lines.size() * LINE_HEIGHT + 6);
		return new RowData(lines, prefix, prefixColor, detail, id, shared, done, visibility, height);
	}

	private List<RowData> rows() {
		List<RowData> rows = new ArrayList<>();
		for (Task task : ClientTasks.active()) {
			rows.add(rowData(ClientTasks.title(task), "[" + Priority.byWeight(task.priority).label() + "] ",
					0xFF000000 | Priority.byWeight(task.priority).color(),
					ClientTasks.progressText(minecraft, task), task.id, false, false, 0, 64));
		}
		for (ClientShared.Entry entry : ClientShared.all()) {
			if (!showDone && entry.done) {
				continue;
			}
			rows.add(rowData(entry.owner + " · " + ClientTasks.title(entry.task),
					"[" + Priority.byWeight(entry.task.priority).label() + "] ",
					0xFF000000 | Priority.byWeight(entry.task.priority).color(),
					ClientTasks.progressText(minecraft, entry.task), entry.id, true, entry.done, entry.visibility, 64));
		}
		if (showDone) {
			for (Task task : ClientTasks.completed()) {
				rows.add(rowData(ClientTasks.title(task), ClientUtil.t("list.donePrefix"), 0xFF757575,
						ClientTasks.progressText(minecraft, task), task.id, false, true, 0, 64));
			}
		}
		return rows;
	}

	/** 从 scroll 开始，能完整放下的行数。 */
	private int visibleCount(List<RowData> rows) {
		int y = LIST_TOP;
		int count = 0;
		for (int i = scroll; i < rows.size(); i++) {
			int h = rows.get(i).height();
			if (y + h > height - 52) {
				break;
			}
			y += h;
			count++;
		}
		return Math.max(1, count);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 与 Fabric 一致：整屏深色遮罩
		graphics.fill(0, 0, width, height, 0xE0101014);
		ClientUtil.centered(graphics, font, title.getString(), width / 2, 22, 0xFFFFD54F);

		List<RowData> rows = rows();
		int visible = visibleCount(rows);
		String info = ClientUtil.t("list.range", rows.isEmpty() ? 0 : scroll + 1,
				Math.min(rows.size(), scroll + visible), rows.size()) + ClientUtil.t("list.scrollHint");
		ClientUtil.centered(graphics, font, info, width / 2, 40, 0xFF9E9E9E);

		if (rows.isEmpty()) {
			ClientUtil.centered(graphics, font, ClientUtil.t("list.empty"), width / 2, LIST_TOP + 20, 0xFF9E9E9E);
		}
		int cx = width / 2;
		int y = LIST_TOP;
		for (int i = scroll; i < rows.size(); i++) {
			RowData row = rows.get(i);
			if (y + row.height() > height - 52) {
				break;
			}
			int color = row.done() ? 0xFF757575
					: row.shared() ? (row.visibility() == 2 ? 0xFFFF80AB : 0xFF64B5F6) : 0xFFECEFF1;
			graphics.drawString(font, row.prefix(), cx - 184, y + 2, row.prefixColor(), true);
			int prefixWidth = font.width(row.prefix());
			for (int line = 0; line < row.lines().size(); line++) {
				graphics.drawString(font, row.lines().get(line), cx - 184 + (line == 0 ? prefixWidth : 0),
						y + 2 + line * LINE_HEIGHT, color, true);
			}
			if (row.detail() != null && !row.detail().isBlank()) {
				graphics.drawString(font, ClientUtil.clip(font, row.detail(), 90), cx + 40, y + 2, 0xFF9FB3C8, true);
			}
			if (!row.shared()) {
				graphics.drawString(font, "[改]", cx + 140, y + 2, 0xFF9AD9FF, true);
				graphics.drawString(font, "[删]", cx + 170, y + 2, 0xFFFF9E9E, true);
			} else {
				ClientShared.Entry entry = ClientShared.byId(row.id());
				if (entry != null && entry.mine) {
					graphics.drawString(font, "[撤销]", cx + 150, y + 2, 0xFFFF9E9E, true);
				}
			}
			y += row.height();
		}

		// 滚动条
		int barX = width - 6;
		int barTop = LIST_TOP;
		int barHeight = Math.max(20, height - 52 - LIST_TOP);
		graphics.fill(barX, barTop, barX + 3, barTop + barHeight, 0x33FFFFFF);
		if (rows.size() > visible) {
			int thumb = Math.max(12, barHeight * visible / rows.size());
			int thumbTop = barTop + (barHeight - thumb) * scroll / Math.max(1, rows.size() - visible);
			graphics.fill(barX, thumbTop, barX + 3, thumbTop + thumb, 0xAAFFD54F);
		}

		super.render(graphics, mouseX, mouseY, partialTick);

		if (!status.isBlank() && System.currentTimeMillis() < statusUntil) {
			graphics.drawString(font, status, 6, height - 14, 0xFF8BC34A, true);
		} else {
			graphics.drawString(font, ClientUtil.t("list.footer",
					com.easytaskmgr.client.EtmClient.KEY_QUICK_ADD.getTranslatedKeyMessage().getString(),
					com.easytaskmgr.client.EtmClient.KEY_LIST.getTranslatedKeyMessage().getString(),
					com.easytaskmgr.client.EtmClient.KEY_CONFIG.getTranslatedKeyMessage().getString(),
					com.easytaskmgr.client.EtmClient.KEY_TOGGLE_HUD.getTranslatedKeyMessage().getString()),
					6, height - 14, 0xFF78909C, true);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		List<RowData> rows = rows();
		int cx = width / 2;
		int y = LIST_TOP;
		for (int i = scroll; i < rows.size(); i++) {
			RowData row = rows.get(i);
			if (y + row.height() > height - 52) {
				break;
			}
			if (mouseY >= y && mouseY <= y + row.height() - 2) {
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
			y += row.height();
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int total = rows().size();
		int visible = visibleCount(rows());
		int maxScroll = Math.max(0, total - visible);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY)));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
