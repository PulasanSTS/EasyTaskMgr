package com.easytaskmgr.client;

import com.easytaskmgr.EasyTaskMgr;
import com.easytaskmgr.net.AdvancementDataS2C;
import com.easytaskmgr.net.AdvancementQueryC2S;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端成就目录缓存。
 *
 * <p>26.3 的服务端只把「玩家有进度」的成就同步给客户端（联机时往往只有 recipes/root 之类），
 * 所以成就候选和前置条件必须向服务端要：进服时拉一次全量列表，用到某个成就时再按需拉详情。
 *
 * <p>服务端只发「翻译键 + id」，中文名由客户端用 {@link Component#translatable} 现场解析，
 * 这样英文服务端也不会把「钻石」显示成 Diamond。
 */
public final class AdvancementCatalog {
	public record Detail(String id, String title, String desc, String parentId, List<String> reqs) {
	}

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Map<String, String> TITLES = new LinkedHashMap<>();
	private static final Map<String, Detail> DETAILS = new ConcurrentHashMap<>();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
	private static boolean listRequested;

	private AdvancementCatalog() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AdvancementDataS2C.TYPE, (payload, context) ->
				context.client().execute(() -> handle(payload.json())));
	}

	public static void reset() {
		TITLES.clear();
		DETAILS.clear();
		PENDING.clear();
		listRequested = false;
	}

	public static void requestList() {
		if (listRequested || !canSend()) {
			return;
		}
		listRequested = true;
		ClientPlayNetworking.send(new AdvancementQueryC2S("", com.easytaskmgr.i18n.I18n.lang().code()));
	}

	public static void requestDetail(String id) {
		if (id == null || id.isBlank() || DETAILS.containsKey(id) || !canSend()) {
			return;
		}
		if (PENDING.add(id)) {
			ClientPlayNetworking.send(new AdvancementQueryC2S(normalize(id), com.easytaskmgr.i18n.I18n.lang().code()));
		}
	}

	public static Detail detail(String id) {
		return id == null ? null : DETAILS.get(normalize(id));
	}

	public static String titleOf(String id) {
		return TITLES.getOrDefault(normalize(id), normalize(id));
	}

	public static List<Map.Entry<String, String>> entries() {
		return new ArrayList<>(TITLES.entrySet());
	}

	public static boolean hasList() {
		return !TITLES.isEmpty();
	}

	private static boolean canSend() {
		try {
			return ClientPlayNetworking.canSend(AdvancementQueryC2S.TYPE);
		} catch (Exception e) {
			return false;
		}
	}

	public static String normalize(String id) {
		String s = id.trim();
		return s.contains(":") ? s : "minecraft:" + s;
	}

	private static String localize(String key, String fallback) {
		if (key != null && !key.isBlank()) {
			String translated = LangResources.translate(key);
			if (translated != null) {
				return translated;
			}
			try {
				return Component.translatable(key).getString();
			} catch (Exception ignored) {
				// 回退到服务端给的文本
			}
		}
		return fallback == null ? "" : fallback;
	}

	private static void handle(String json) {
		try {
			JsonObject root = GSON.fromJson(json, JsonObject.class);
			if (root == null) {
				return;
			}
			if (root.has("entries")) {
				TITLES.clear();
				JsonArray arr = root.getAsJsonArray("entries");
				for (JsonElement element : arr) {
					JsonObject o = element.getAsJsonObject();
					String id = o.get("id").getAsString();
					String title = o.has("title") ? o.get("title").getAsString() : id;
					String key = o.has("titleKey") ? o.get("titleKey").getAsString() : "";
					TITLES.put(id, localize(key, title));
				}
				EasyTaskMgr.LOGGER.info("收到成就列表：{} 条", TITLES.size());
				return;
			}
			String id = root.get("id").getAsString();
			String title = root.has("title") ? root.get("title").getAsString() : id;
			String titleKey = root.has("titleKey") ? root.get("titleKey").getAsString() : "";
			String desc = root.has("desc") ? root.get("desc").getAsString() : "";
			String descKey = root.has("descKey") ? root.get("descKey").getAsString() : "";
			String parentId = root.has("parentId") ? root.get("parentId").getAsString() : "";
			List<String> reqs = new ArrayList<>();
			if (root.has("reqs")) {
				for (JsonElement element : root.getAsJsonArray("reqs")) {
					reqs.add(element.getAsString());
				}
			}
			DETAILS.put(id, new Detail(id, localize(titleKey, title), localize(descKey, desc), parentId, reqs));
			PENDING.remove(id);
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.warn("解析成就数据失败", e);
		}
	}
}
