package com.easytaskmgr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 名称 / id 双向查询。
 *
 * <p>成就候选优先用服务端下发的目录（{@link AdvancementCatalog}），
 * 因为 26.3 联机时客户端本地只有「有进度」的成就；
 * 服务端没装模组时退回本地 {@code ClientAdvancements}。
 */
public final class Suggestions {
	public record Entry(String id, String name, String path) {
	}

	private static List<Entry> itemCache;
	private static List<Entry> advancementCache;
	private static int advancementCacheKey = -1;

	private Suggestions() {
	}

	public static List<Entry> items() {
		if (itemCache == null) {
			List<Entry> list = new ArrayList<>();
			for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
				Item item = BuiltInRegistries.ITEM.getValue(id);
				if (item == null || item == Items.AIR) {
					continue;
				}
				String name;
				try {
					name = new ItemStack(item).getHoverName().getString();
				} catch (Exception e) {
					name = id.getPath();
				}
				list.add(new Entry(id.toString(), name, id.getPath()));
			}
			list.sort(Comparator.comparing(Entry::name));
			itemCache = list;
		}
		return itemCache;
	}

	public static List<Entry> advancements(Minecraft mc) {
		int localSize = mc.getConnection() == null ? 0 : mc.getConnection().getAdvancements().progress().size();
		int catalogSize = AdvancementCatalog.entries().size();
		int key = catalogSize * 100_000 + localSize;
		if (advancementCache != null && advancementCacheKey == key) {
			return advancementCache;
		}
		List<Entry> list = new ArrayList<>();
		if (catalogSize > 0) {
			for (var entry : AdvancementCatalog.entries()) {
				list.add(new Entry(entry.getKey(), entry.getValue(), pathOf(entry.getKey())));
			}
		} else if (mc.getConnection() != null) {
			for (var holder : mc.getConnection().getAdvancements().progress().keySet()) {
				if (holder.value().display().isEmpty()) {
					continue;
				}
				String name = holder.value().display().get().title().getString();
				if (name.isBlank()) {
					continue;
				}
				list.add(new Entry(holder.id().toString(), name, holder.id().getPath()));
			}
		}
		list.sort(Comparator.comparing(Entry::name));
		advancementCache = list;
		advancementCacheKey = key;
		return list;
	}

	private static String pathOf(String id) {
		int i = id.indexOf(':');
		return i >= 0 ? id.substring(i + 1) : id;
	}

	/** 按名字或 id 模糊搜索，名字前缀匹配的排前面。 */
	public static List<Entry> search(List<Entry> source, String query, int limit) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return List.of();
		}
		List<Entry> hits = new ArrayList<>();
		for (Entry e : source) {
			String name = e.name().toLowerCase(Locale.ROOT);
			String id = e.id().toLowerCase(Locale.ROOT);
			String path = e.path().toLowerCase(Locale.ROOT);
			if (name.contains(q) || id.contains(q) || path.contains(q)) {
				hits.add(e);
			}
		}
		hits.sort(Comparator
				.comparingInt((Entry e) -> e.name().toLowerCase(Locale.ROOT).startsWith(q) ? 0 : 1)
				.thenComparing(Entry::name));
		return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
	}

	public static String nameOfItem(String id) {
		for (Entry e : items()) {
			if (e.id().equals(id)) {
				return e.name();
			}
		}
		return id;
	}

	public static String resolveItem(String input) {
		if (input == null || input.isBlank()) {
			return "";
		}
		String text = input.trim();
		String lower = text.toLowerCase(Locale.ROOT);
		if (text.contains(":")) {
			return text;
		}
		List<Entry> source = items();
		for (Entry e : source) {
			if (e.name().equalsIgnoreCase(text)) {
				return e.id();
			}
		}
		for (Entry e : source) {
			if (e.path().equals(lower)) {
				return e.id();
			}
		}
		for (Entry e : source) {
			if (e.name().toLowerCase(Locale.ROOT).contains(lower)) {
				return e.id();
			}
		}
		return text;
	}

	public static String resolveAdvancement(Minecraft mc, String input) {
		if (input == null || input.isBlank()) {
			return "";
		}
		String text = input.trim();
		if (text.contains(":")) {
			return text;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		List<Entry> source = advancements(mc);
		for (Entry e : source) {
			if (e.name().equalsIgnoreCase(text)) {
				return e.id();
			}
		}
		for (Entry e : source) {
			if (e.path().equalsIgnoreCase(text)) {
				return e.id();
			}
		}
		for (Entry e : source) {
			if (e.name().toLowerCase(Locale.ROOT).contains(lower)) {
				return e.id();
			}
		}
		return "minecraft:" + text;
	}

	public static String nameOfAdvancement(Minecraft mc, String id) {
		for (Entry e : advancements(mc)) {
			if (e.id().equals(id)) {
				return e.name();
			}
		}
		return id;
	}
}
