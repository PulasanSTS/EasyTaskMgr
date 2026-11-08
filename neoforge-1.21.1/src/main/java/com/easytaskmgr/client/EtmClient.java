package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgrNeo;
import com.easytaskmgr.client.screens.ConfigScreen;
import com.easytaskmgr.client.screens.ListScreen;
import com.easytaskmgr.client.screens.QuickAddScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：键位、HUD 图层、每秒检测、HUD 交互点击。
 *
 * <p>Fabric 版把 HUD 注册与点击 mixin 分开；NeoForge 版用
 * {@code RegisterGuiLayersEvent} 挂 HUD、用 {@code InputEvent.MouseButton.Pre} 拦点击，不需要 mixin。
 */
@EventBusSubscriber(modid = EasyTaskMgrNeo.MODID, bus = EventBusSubscriber.Bus.GAME, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class EtmClient {
	public static final KeyMapping KEY_QUICK_ADD = new KeyMapping(
			"key.easytaskmgr.quick_add", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.easytaskmgr");
	public static final KeyMapping KEY_LIST = new KeyMapping(
			"key.easytaskmgr.open_list", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.easytaskmgr");
	public static final KeyMapping KEY_CONFIG = new KeyMapping(
			"key.easytaskmgr.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.easytaskmgr");
	public static final KeyMapping KEY_TOGGLE_HUD = new KeyMapping(
			"key.easytaskmgr.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.easytaskmgr");
	public static final KeyMapping KEY_INTERACT = new KeyMapping(
			"key.easytaskmgr.hud_interact", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.easytaskmgr");

	private static int tickCounter;

	private EtmClient() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		// 语言可能被设置界面 / Minecraft 设置改动，这里对齐内置文案的语言（review #26）
		com.easytaskmgr.i18n.I18n.set(ClientUtil.lang());
		while (KEY_QUICK_ADD.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new QuickAddScreen());
			}
		}
		while (KEY_LIST.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new ListScreen());
			}
		}
		while (KEY_CONFIG.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new ConfigScreen());
			}
		}
		while (KEY_TOGGLE_HUD.consumeClick()) {
			var cfg = com.easytaskmgr.config.ModConfig.get();
			cfg.hudHidden = !cfg.hudHidden;
			cfg.save();
			ClientUtil.info(ClientUtil.t(cfg.hudHidden ? "notify.hud.hidden" : "notify.hud.shown"));
		}
		while (KEY_INTERACT.consumeClick()) {
			var cfg = com.easytaskmgr.config.ModConfig.get();
			cfg.hudInteractive = !cfg.hudInteractive;
			cfg.save();
			if (cfg.hudInteractive) {
				mc.mouseHandler.releaseMouse();
				ClientUtil.info(ClientUtil.t("notify.interact.on",
						KEY_INTERACT.getTranslatedKeyMessage().getString()));
			} else {
				mc.mouseHandler.grabMouse();
				ClientUtil.info(ClientUtil.t("notify.interact.off"));
			}
		}

		if (++tickCounter >= 20) {
			tickCounter = 0;
			ClientTasks.tick(mc);
			ClientShared.tick(mc);
		}
	}

	/** HUD 交互模式：左键点住 HUD 行就完成 / 认领，事件被 cancel 后不会再打到游戏里。 */
	@SubscribeEvent
	public static void onMouseButton(InputEvent.MouseButton.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || !com.easytaskmgr.config.ModConfig.get().hudInteractive) {
			return;
		}
		if (event.getAction() != GLFW.GLFW_PRESS || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return;
		}
		double scale = mc.getWindow().getGuiScale();
		double mouseX = mc.mouseHandler.xpos() / scale;
		double mouseY = mc.mouseHandler.ypos() / scale;
		if (Hud.click(mouseX, mouseY)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		Minecraft mc = Minecraft.getInstance();
		// 进服先读配置与本地任务，再处理上线提醒（review #3、#4）
		com.easytaskmgr.config.ModConfig.get().load();
		com.easytaskmgr.task.TaskStore.get().load();
		com.easytaskmgr.i18n.I18n.set(ClientUtil.lang());
		ClientShared.sync();
		ClientTasks.onLogin(mc);
		ClientTasks.queryAllAdvancements();
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientShared.reset();
	}

	/** MOD 总线：键位与 HUD 图层注册。 */
	@EventBusSubscriber(modid = EasyTaskMgrNeo.MODID, bus = EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
	public static final class ModBus {
		private ModBus() {
		}

		@SubscribeEvent
		public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
			event.register(KEY_QUICK_ADD);
			event.register(KEY_LIST);
			event.register(KEY_CONFIG);
			event.register(KEY_TOGGLE_HUD);
			event.register(KEY_INTERACT);
		}

		@SubscribeEvent
		public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
			event.registerAboveAll(EasyTaskMgrNeo.id("hud"), new Hud());
		}
	}
}
