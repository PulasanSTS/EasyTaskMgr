package com.easytaskmgr.client;

import com.easytaskmgr.advancement.AdvancementDescriber;
import net.minecraft.client.Minecraft;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 成就信息门面：优先用服务端下发的目录（{@link AdvancementCatalog}），
 * 单机 / 数据包场景回退到本地 {@code ClientAdvancements}。
 *
 * <p>条件文本里的物品 / 方块用 {@code {{item:id}}} 占位，这里按客户端语言翻译，
 * 避免英文服务端把「钻石」显示成 Diamond。
 */
public final class AdvancementInfo {
	private static final Map<String, List<String>> LOCAL_CACHE = new ConcurrentHashMap<>();
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(item|block):([^}]+)}}");

	private AdvancementInfo() {
	}

	/** HUD 用的简短条件列表，例如「获得 1 个 钻石」。 */
	public static List<String> requirements(Minecraft mc, String rawId) {
		String id = AdvancementCatalog.normalize(rawId);
		AdvancementCatalog.Detail detail = AdvancementCatalog.detail(id);
		if (detail != null) {
			return localizeAll(detail.reqs());
		}
		AdvancementCatalog.requestDetail(id);

		List<String> cached = LOCAL_CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		AdvancementHolder holder = resolve(mc, id);
		if (holder == null) {
			return List.of();
		}
		List<String> reqs = localizeAll(AdvancementDescriber.requirements(holder));
		LOCAL_CACHE.put(id, reqs);
		return reqs;
	}

	/** 编辑界面用的长描述：标题 / 描述 / 前置成就 / 达成方式。 */
	public static List<String> describe(Minecraft mc, String rawId) {
		String id = AdvancementCatalog.normalize(rawId);
		List<String> lines = new ArrayList<>();

		AdvancementCatalog.Detail catalog = AdvancementCatalog.detail(id);
		if (catalog != null) {
			lines.add("标题：" + catalog.title());
			if (!catalog.desc().isBlank()) {
				lines.add("描述：" + catalog.desc());
			}
			String parent = catalog.parentId().isBlank()
					? "无（根成就）"
					: AdvancementCatalog.titleOf(catalog.parentId());
			lines.add("前置成就：" + parent);
			lines.add("达成方式：");
			for (String req : localizeAll(catalog.reqs())) {
				lines.add("  · " + req);
			}
			return lines;
		}
		AdvancementCatalog.requestDetail(id);

		AdvancementHolder holder = resolve(mc, id);
		if (holder == null) {
			lines.add("正在向服务端查询这个成就…（如果一直没有结果，说明服务端没装模组或该成就不存在）");
			return lines;
		}
		holder.value().display().ifPresent(display -> {
			lines.add("标题：" + display.title().getString());
			String description = display.description().getString();
			if (!description.isBlank()) {
				lines.add("描述：" + description);
			}
		});
		lines.add("前置成就：" + AdvancementDescriber.parentTitle(holder,
				key -> mc.getConnection() == null ? null : mc.getConnection().getAdvancements().get(key)));
		lines.add("达成方式：");
		for (String req : localizeAll(AdvancementDescriber.requirements(holder))) {
			lines.add("  · " + req);
		}
		return lines;
	}

	public static void clearCache() {
		LOCAL_CACHE.clear();
	}

	/** 兼容「不带命名空间的写法」。 */
	public static AdvancementHolder resolve(Minecraft mc, String rawId) {
		if (mc.getConnection() == null || rawId == null || rawId.isBlank()) {
			return null;
		}
		var advancements = mc.getConnection().getAdvancements();
		String id = AdvancementCatalog.normalize(rawId);
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		AdvancementHolder holder = advancements.get(identifier);
		if (holder != null) {
			return holder;
		}
		for (AdvancementHolder candidate : advancements.progress().keySet()) {
			if (candidate.id().getPath().equals(identifier.getPath())) {
				return candidate;
			}
		}
		return null;
	}

	/** 供 HUD 用的单行摘要（取前两条条件）。 */
	public static String summary(Minecraft mc, String rawId) {
		List<String> reqs = requirements(mc, rawId);
		if (reqs.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(2, reqs.size()); i++) {
			if (i > 0) {
				sb.append(" / ");
			}
			sb.append(reqs.get(i));
		}
		return sb.toString();
	}

	private static List<String> localizeAll(List<String> input) {
		List<String> out = new ArrayList<>(input.size());
		for (String s : input) {
			out.add(localize(s));
		}
		return out;
	}

	private static String localize(String text) {
		if (text == null || !text.contains("{{")) {
			return text;
		}
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String id = matcher.group(2);
			String name = matcher.group(1).equals("item") ? itemName(id) : blockName(id);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(name));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String itemName(String id) {
		try {
			return Suggestions.nameOfItem(id);
		} catch (Exception e) {
			return id;
		}
	}

	private static String blockName(String id) {
		try {
			var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
			return block == null ? id : block.getName().getString();
		} catch (Exception e) {
			return id;
		}
	}

	static String lower(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT);
	}
}
