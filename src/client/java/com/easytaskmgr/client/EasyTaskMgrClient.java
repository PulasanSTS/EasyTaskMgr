package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.task.TaskStore;
import com.easytaskmgr.client.ui.ConfigScreen;
import com.easytaskmgr.client.ui.QuickAddScreen;
import com.easytaskmgr.client.ui.TaskListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;

/**
 * 客户端入口：装配 HUD、快捷键、每 tick 的条件检测与联机同步。
 */
public class EasyTaskMgrClient implements ClientModInitializer {
	private static int saveTimer;
	private static int shareTimer;

	@Override
	public void onInitializeClient() {
		ModConfig.get().load();
		TaskStore.get().load();
		Keybinds.register();
		ClientNet.register();
		AdvancementCatalog.register();

		HudElementRegistry.addLast(TaskHud.ID, new TaskHud());
		TaskHud.setInteractive(ModConfig.get().hudInteractive);

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientNet.reset();
			AdvancementCatalog.reset();
			AdvancementCatalog.requestList();
			ClientTasks.onJoinWorld(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientNet.reset();
			AdvancementCatalog.reset();
		});

		EasyTaskMgr.LOGGER.info("EasyTaskMgr 客户端初始化完成");
	}

	private void onTick(Minecraft mc) {
		while (Keybinds.QUICK_ADD.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.setScreenAndShow(new QuickAddScreen(null));
			}
		}
		while (Keybinds.OPEN_LIST.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.setScreenAndShow(new TaskListScreen(null));
			}
		}
		while (Keybinds.OPEN_CONFIG.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.setScreenAndShow(new ConfigScreen(null));
			}
		}
		while (Keybinds.TOGGLE_HUD.consumeClick()) {
			ModConfig cfg = ModConfig.get();
			cfg.hudHidden = !cfg.hudHidden;
			cfg.save();
			ClientTasks.toast(cfg.hudHidden ? "HUD 已隐藏" : "HUD 已显示");
		}

		while (Keybinds.HUD_INTERACT.consumeClick()) {
			boolean next = !TaskHud.isInteractive();
			TaskHud.setInteractive(next);
			ModConfig.get().hudInteractive = next;
			ModConfig.get().save();
			if (next) {
				mc.mouseHandler.releaseMouse();
				ClientTasks.toast("HUD 交互：点击普通任务完成，按 V 退出");
			} else {
				mc.mouseHandler.grabMouse();
				ClientTasks.toast("已退出 HUD 交互");
			}
		}
		if (TaskHud.isInteractive() && mc.gui.screen() == null && mc.mouseHandler.isMouseGrabbed()) {
			mc.mouseHandler.releaseMouse();
		}

		if (mc.player == null || mc.level == null) {
			return;
		}

		ClientTasks.tick(mc);

		if (++saveTimer >= 100) { // 5 秒
			saveTimer = 0;
			TaskStore.get().saveIfDirty();
		}
		if (++shareTimer >= 40) { // 2 秒检查一次是否需要同步
			shareTimer = 0;
			ClientNet.syncIfNeeded();
		}
	}
}
