package com.easytaskmgr.client;

import com.easytaskmgr.i18n.I18n;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 直接读取原版语言文件，让模组的英文模式和当前游戏语言解耦。
 *
 * <p>为什么需要它：物品名、成就标题这些文本来自 Minecraft 的语言文件，
 * `Component#getString()` 永远按**游戏当前语言**解析。如果玩家游戏是中文、
 * 模组语言选了 English，直接调用会得到中文。这里把 `en_us.json` / `zh_cn.json`
 * 读进内存，按模组语言取文案，找不到时再回退。
 */
public final class LangResources {
	private static Map<String, String> english = Map.of();
	private static Map<String, String> chinese = Map.of();
	private static boolean loaded;

	private LangResources() {
	}

	public static void invalidate() {
		loaded = false;
		english = Map.of();
		chinese = Map.of();
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		english = load("en_us");
		chinese = load("zh_cn");
	}

	private static Map<String, String> load(String code) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getResourceManager() == null) {
			return Map.of();
		}
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", "lang/" + code + ".json");
		try (var stream = mc.getResourceManager().open(id)) {
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, String> map = new HashMap<>();
			for (var entry : root.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					map.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			return map;
		} catch (Exception e) {
			return Map.of();
		}
	}

	/** 按模组语言取原版文案；找不到返回 null，由调用方回退。 */
	public static String translate(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		ensureLoaded();
		String value = I18n.lang() == I18n.Lang.ZH_CN ? chinese.get(key) : english.get(key);
		return value == null || value.isBlank() ? null : value;
	}
}
