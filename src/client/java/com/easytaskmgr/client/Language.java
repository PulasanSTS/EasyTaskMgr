package com.easytaskmgr.client;

import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.i18n.I18n;
import net.minecraft.client.Minecraft;

/**
 * 语言解析：配置为 auto 时跟随 Minecraft 语言，只有简体中文走中文，其余一律英文。
 *
 * <p>语言变化时清空成就目录缓存并重新请求，保证成就名也跟着切换。
 */
public final class Language {
	private static String lastApplied;

	private Language() {
	}

	public static void apply() {
		String option = ModConfig.get().language == null ? "auto" : ModConfig.get().language;
		I18n.Lang target;
		if ("zh_cn".equals(option)) {
			target = I18n.Lang.ZH_CN;
		} else if ("en_us".equals(option)) {
			target = I18n.Lang.EN_US;
		} else {
			Minecraft mc = Minecraft.getInstance();
			String code = mc == null || mc.getLanguageManager() == null ? null : mc.getLanguageManager().getSelected();
			target = I18n.Lang.byCode(code);
		}

		String key = option + ":" + target.code();
		if (key.equals(lastApplied)) {
			return;
		}
		lastApplied = key;
		boolean changed = target != I18n.lang();
		I18n.set(target);
		if (changed) {
			LangResources.invalidate();
			Suggestions.invalidate();
			AdvancementInfo.clearCache();
			AdvancementCatalog.reset();
			AdvancementCatalog.requestList();
		}
	}
}
