package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgr;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;


/**
 * 快捷键注册。
 *
 * <ul>
 *   <li>B：快速添加待办</li>
 *   <li>N：打开待办列表（含添加/编辑/删除/高级条件）</li>
 *   <li>K：打开设置（HUD 位置、缩放、多人共享开关）</li>
 *   <li>H：临时隐藏 / 显示 HUD</li>
 * </ul>
 */
public final class Keybinds {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(EasyTaskMgr.id("keys"));

	public static final KeyMapping QUICK_ADD = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.easytaskmgr.quick_add", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY));
	public static final KeyMapping OPEN_LIST = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.easytaskmgr.open_list", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, CATEGORY));
	public static final KeyMapping OPEN_CONFIG = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.easytaskmgr.open_config", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
	public static final KeyMapping TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.easytaskmgr.toggle_hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));
	public static final KeyMapping HUD_INTERACT = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.easytaskmgr.hud_interact", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));

	private Keybinds() {
	}

	public static void register() {
		// 触发类初始化（静态字段在类加载时注册）
		EasyTaskMgr.LOGGER.debug("已注册快捷键: {}, {}, {}, {}",
				QUICK_ADD.getName(), OPEN_LIST.getName(), OPEN_CONFIG.getName(), TOGGLE_HUD.getName());
	}
}
