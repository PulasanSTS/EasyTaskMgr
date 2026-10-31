package com.easytaskmgr.advancement;

import com.easytaskmgr.i18n.I18n;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * 成就条件描述器（公共端，支持中英文）。
 *
 * <p>服务端按「请求方语言」把成就树整理成「标题 + 前置 + 条件」发给客户端；
 * 客户端在单机 / 数据包场景下用它做本地兜底解析。
 */
public final class AdvancementDescriber {
	private AdvancementDescriber() {
	}

	public record Entry(String id, String title) {
	}

	/** 玩家可见的成就（过滤 recipes/ 技术成就和无标题条目）。 */
	public static boolean isVisible(AdvancementHolder holder) {
		if (holder.id().getPath().startsWith("recipes/")) {
			return false;
		}
		return holder.value().display().map(d -> !d.title().getString().isBlank()).orElse(false);
	}

	public static String title(AdvancementHolder holder) {
		return holder.value().display().map(d -> d.title().getString()).orElse(holder.id().getPath());
	}

	public static String description(AdvancementHolder holder) {
		return holder.value().display().map(d -> d.description().getString()).orElse("");
	}

	public static String parentTitle(AdvancementHolder holder, Function<Identifier, AdvancementHolder> lookup,
			I18n.Lang lang) {
		return holder.value().parent()
				.map(id -> {
					AdvancementHolder parent = lookup.apply(id);
					return parent != null ? title(parent) : id.toString();
				})
				.orElse(I18n.t(lang, "info.noParent"));
	}

	/** 前置成就 id（客户端自己查名字，避免服务端语言不同）。 */
	public static String parentId(AdvancementHolder holder) {
		return holder.value().parent().map(Identifier::toString).orElse("");
	}

	public static String titleKey(AdvancementHolder holder) {
		return holder.value().display().map(d -> translationKey(d.title())).orElse("");
	}

	public static String descKey(AdvancementHolder holder) {
		return holder.value().display().map(d -> translationKey(d.description())).orElse("");
	}

	/** 可翻译文本的 key，用于让客户端按自己的语言显示。 */
	public static String translationKey(net.minecraft.network.chat.Component component) {
		if (component == null) {
			return "";
		}
		if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
			return tc.getKey();
		}
		return "";
	}

	/** 把 criteria 翻译成可读条件。 */
	public static List<String> requirements(AdvancementHolder holder, I18n.Lang lang) {
		List<String> result = new ArrayList<>();
		for (var entry : holder.value().criteria().entrySet()) {
			String text = describe(entry.getValue(), lang);
			if (text != null && !text.isBlank()) {
				result.add(text);
			}
		}
		if (result.isEmpty()) {
			result.add(I18n.t(lang, "adv.unknown"));
		}
		return result;
	}

	private static String describe(net.minecraft.advancements.triggers.Criterion<?> criterion, I18n.Lang lang) {
		Object instance = criterion.triggerInstance();
		String name = criterion.trigger().getClass().getSimpleName().toLowerCase(Locale.ROOT);

		if (instance instanceof net.minecraft.advancements.triggers.InventoryChangeTrigger.TriggerInstance inv) {
			List<String> parts = new ArrayList<>();
			for (var predicate : inv.items()) {
				parts.add(itemText(predicate, lang));
			}
			return parts.isEmpty() ? I18n.t(lang, "adv.obtainItem")
					: I18n.t(lang, "adv.obtain", String.join(I18n.t(lang, "adv.or"), parts));
		}
		if (instance instanceof net.minecraft.advancements.triggers.ConsumeItemTrigger.TriggerInstance eat) {
			return eat.item().map(p -> I18n.t(lang, "adv.consume", itemText(p, lang)))
					.orElse(I18n.t(lang, "adv.consumeAny"));
		}
		if (instance instanceof net.minecraft.advancements.triggers.EnterBlockTrigger.TriggerInstance enter) {
			return enter.block().map(b -> I18n.t(lang, "adv.enter", blockText(b, lang)))
					.orElse(I18n.t(lang, "adv.enterAny"));
		}
		if (instance instanceof net.minecraft.advancements.triggers.RecipeCraftedTrigger.TriggerInstance craft) {
			if (!craft.ingredients().isEmpty()) {
				return I18n.t(lang, "adv.craft.ingredients", itemText(craft.ingredients().get(0), lang));
			}
			return I18n.t(lang, "adv.craft");
		}
		if (instance instanceof net.minecraft.advancements.triggers.TradeTrigger.TriggerInstance trade) {
			return trade.item().map(p -> I18n.t(lang, "adv.trade", itemText(p, lang)))
					.orElse(I18n.t(lang, "adv.tradeAny"));
		}
		if (instance instanceof net.minecraft.advancements.triggers.EnchantedItemTrigger.TriggerInstance enchant) {
			return enchant.item().map(p -> I18n.t(lang, "adv.enchant", itemText(p, lang)))
					.orElse(I18n.t(lang, "adv.enchantAny"));
		}
		if (instance instanceof net.minecraft.advancements.triggers.KilledTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.kill");
		}
		if (instance instanceof net.minecraft.advancements.triggers.ChangeDimensionTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.dimension");
		}
		if (instance instanceof net.minecraft.advancements.triggers.FishingRodHookedTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.fish");
		}
		if (instance instanceof net.minecraft.advancements.triggers.BredAnimalsTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.breed");
		}
		if (instance instanceof net.minecraft.advancements.triggers.CuredZombieVillagerTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.cure");
		}
		if (instance instanceof net.minecraft.advancements.triggers.ConstructBeaconTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.beacon");
		}
		if (instance instanceof net.minecraft.advancements.triggers.LevitationTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.levitate");
		}
		if (instance instanceof net.minecraft.advancements.triggers.UsedEnderEyeTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.eye");
		}
		if (instance instanceof net.minecraft.advancements.triggers.ShotCrossbowTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.crossbow");
		}
		if (instance instanceof net.minecraft.advancements.triggers.RecipeUnlockedTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.recipeUnlock");
		}
		if (instance instanceof net.minecraft.advancements.triggers.PlayerTrigger.TriggerInstance) {
			return I18n.t(lang, "adv.player");
		}

		if (name.contains("inventorychange")) {
			return I18n.t(lang, "adv.obtainItem");
		}
		if (name.contains("killed")) {
			return I18n.t(lang, "adv.kill");
		}
		if (name.contains("consume")) {
			return I18n.t(lang, "adv.consumeAny");
		}
		if (name.contains("enterblock")) {
			return I18n.t(lang, "adv.enterAny");
		}
		if (name.contains("location") || name.contains("distance")) {
			return I18n.t(lang, "adv.location");
		}
		if (name.contains("recipe")) {
			return I18n.t(lang, "adv.craft");
		}
		return criterion.trigger().getClass().getSimpleName().replace("Trigger", "");
	}

	private static String itemText(net.minecraft.advancements.predicates.ItemPredicate predicate, I18n.Lang lang) {
		Optional<HolderSet<Item>> items = predicate.items();
		int min = predicate.count().min().orElse(1);
		String itemName;
		if (items.isEmpty()) {
			itemName = I18n.t(lang, "adv.item");
		} else {
			List<String> names = new ArrayList<>();
			int i = 0;
			for (var holder : items.get()) {
				if (i++ >= 3) {
					names.add("…");
					break;
				}
				names.add(itemName(holder.value()));
			}
			itemName = String.join(I18n.t(lang, "adv.or"), names);
		}
		return min > 1 ? I18n.t(lang, "adv.count", min, itemName) : itemName;
	}

	private static String itemName(Item item) {
		try {
			return "{{item:" + BuiltInRegistries.ITEM.getKey(item) + "}}";
		} catch (Exception e) {
			return BuiltInRegistries.ITEM.getKey(item).toString();
		}
	}

	private static String blockText(HolderSet<Block> blocks, I18n.Lang lang) {
		List<String> names = new ArrayList<>();
		int i = 0;
		for (var holder : blocks) {
			if (i++ >= 3) {
				names.add("…");
				break;
			}
			names.add("{{block:" + BuiltInRegistries.BLOCK.getKey(holder.value()) + "}}");
		}
		return String.join(I18n.t(lang, "adv.or"), names);
	}

	/** 供 HUD 用的单行摘要（取前两条条件）。 */
	public static String summary(AdvancementHolder holder, I18n.Lang lang) {
		List<String> reqs = requirements(holder, lang);
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
}
