package com.easytaskmgr.client.screens;

import com.easytaskmgr.client.ClientShared;
import com.easytaskmgr.client.ClientTasks;
import com.easytaskmgr.client.ClientUtil;
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

/** 待办列表：点左侧圆圈完成 / 认领，右侧改或删，底部新增 / 设置 / 导出 / 导入。 */
public class ListScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private int scroll;
	/** 是否显示已完成段（Fabric 版有开关）。 */
	private static boolean showDone = true;
	private String status = "";
	private long statusUntil;

	public ListScreen() {
		super(Component.literal(ClientUtil.t("list.title")));
	}

	private record Row(int y, String text, String id, boolean shared, boolean done) {
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
		int y = 46 - scroll;
		for (Task task : ClientTasks.active()) {
			rows.add(new Row(y, ClientTasks.priorityTag(task) + " " + ClientTasks.title(task), task.id, false, false));
			y += ROW_HEIGHT;
		}
		for (ClientShared.Entry entry : ClientShared.all()) {
			if (!showDone && entry.done) {
				continue;
			}
			rows.add(new Row(y, "[S] " + entry.owner + " · " + ClientTasks.title(entry.task), entry.id, true, entry.done));
			y += ROW_HEIGHT;
		}
		if (!showDone) {
			return rows;
		}
		for (Task task : ClientTasks.completed()) {
			rows.add(new Row(y, ClientUtil.t("list.donePrefix") + ClientTasks.title(task), task.id, false, true));
			y += ROW_HEIGHT;
		}
		return rows;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFD54F);
		graphics.drawCenteredString(font, Component.literal(ClientUtil.t("list.scrollHint")), width / 2, 34, 0xFF888888);

		for (Row row : rows()) {
			if (row.y() < 44 || row.y() > height - 34) {
				continue;
			}
			int color = row.done() ? 0xFF888888 : row.shared() ? 0xFF7FB8FF : 0xFFFFFFFF;
			graphics.drawString(font, ClientUtil.clip(font, row.text(), width - 220), 20, row.y(), color, true);
			ClientShared.Entry shared = row.shared() ? ClientShared.byId(row.id()) : null;
			if (!row.shared()) {
				graphics.drawString(font, "[改]", width - 120, row.y(), 0xFF9AD9FF, true);
				graphics.drawString(font, "[删]", width - 82, row.y(), 0xFFFF9E9E, true);
			} else if (shared != null && shared.mine) {
				graphics.drawString(font, "[撤销]", width - 120, row.y(), 0xFFFF9E9E, true);
			}
		}

		if (rows().isEmpty()) {
			graphics.drawCenteredString(font, Component.literal(ClientUtil.t("list.empty")), width / 2, height / 2, 0xFFAAAAAA);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
		if (!status.isBlank() && System.currentTimeMillis() < statusUntil) {
			graphics.drawString(font, status, 6, height - 12, 0xFFCCCCCC, true);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		for (Row row : rows()) {
			// 与渲染保持一致：滚动到可视区外的行不能点（review #21）
			if (row.y() < 44 || row.y() > height - 34) {
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
				if (entry.mine && mouseX >= width - 128) {
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
			if (mouseX > width - 128 && mouseX < width - 90) {
				minecraft.setScreen(new EditScreen(task));
			} else if (mouseX > width - 90) {
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
		scroll = Math.max(0, scroll - (int) Math.round(scrollY * ROW_HEIGHT));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
