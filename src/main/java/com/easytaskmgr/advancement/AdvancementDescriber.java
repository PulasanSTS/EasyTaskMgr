package com.easytaskmgr.advancement;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * 成就条件描述器（公共端）。
 *
 * <p>服务端用它把成就树整理成「标题 + 前置 + 条件」发给客户端；
 * 客户端在单机 / 数据包场景下也用它做本地兜底解析。
 *
 * <p>能识别常见触发器：收集物品、吃掉物品、进入方块、合成、交易、附魔、击杀、换维度、钓鱼、繁殖等；
 * 未知触发器回退成去掉 {@code Trigger} 的类名。
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

	/** 前置成就标题；需要传入一个「按 id 查 holder」的函数。 */
	public static String parentTitle(AdvancementHolder holder, Function<Identifier, AdvancementHolder> lookup) {
		return holder.value().parent()
				.map(id -> {
					AdvancementHolder parent = lookup.apply(id);
					return parent != null ? title(parent) : id.toString();
				})
				.orElse("无（根成就）");
	}

	/** 把 criteria 翻译成可读条件，例如「获得 1 个 钻石」。 */
	public static List<String> requirements(AdvancementHolder holder) {
		List<String> result = new ArrayList<>();
		for (var entry : holder.value().criteria().entrySet()) {
			String text = describe(entry.getValue());
			if (text != null && !text.isBlank()) {
				result.add(text);
			}
		}
		if (result.isEmpty()) {
			result.add("完成条件由数据包定义");
		}
		return result;
	}

	private static String describe(net.minecraft.advancements.triggers.Criterion<?> criterion) {
		Object instance = criterion.triggerInstance();
		String name = criterion.trigger().getClass().getSimpleName().toLowerCase(Locale.ROOT);

		if (instance instanceof net.minecraft.advancements.triggers.InventoryChangeTrigger.TriggerInstance inv) {
			List<String> parts = new ArrayList<>();
			for (var predicate : inv.items()) {
				parts.add(itemText(predicate));
			}
			return parts.isEmpty() ? "获得物品" : "获得 " + String.join(" 或 ", parts);
		}
		if (instance instanceof net.minecraft.advancements.triggers.ConsumeItemTrigger.TriggerInstance eat) {
			return eat.item().map(p -> "吃掉 " + itemText(p)).orElse("吃掉物品");
		}
		if (instance instanceof net.minecraft.advancements.triggers.EnterBlockTrigger.TriggerInstance enter) {
			return enter.block().map(AdvancementDescriber::blockText).orElse("进入特定方块");
		}
		if (instance instanceof net.minecraft.advancements.triggers.RecipeCraftedTrigger.TriggerInstance craft) {
			if (!craft.ingredients().isEmpty()) {
				return "按配方合成（材料：" + itemText(craft.ingredients().get(0)) + " 等）";
			}
			return "按配方合成指定物品";
		}
		if (instance instanceof net.minecraft.advancements.triggers.TradeTrigger.TriggerInstance trade) {
			return trade.item().map(p -> "与村民交易 " + itemText(p)).orElse("与村民交易");
		}
		if (instance instanceof net.minecraft.advancements.triggers.EnchantedItemTrigger.TriggerInstance enchant) {
			return enchant.item().map(p -> "附魔 " + itemText(p)).orElse("附魔物品");
		}
		if (instance instanceof net.minecraft.advancements.triggers.KilledTrigger.TriggerInstance) {
			return "击杀生物";
		}
		if (instance instanceof net.minecraft.advancements.triggers.ChangeDimensionTrigger.TriggerInstance) {
			return "前往其他维度";
		}
		if (instance instanceof net.minecraft.advancements.triggers.FishingRodHookedTrigger.TriggerInstance) {
			return "钓鱼";
		}
		if (instance instanceof net.minecraft.advancements.triggers.BredAnimalsTrigger.TriggerInstance) {
			return "繁殖动物";
		}
		if (instance instanceof net.minecraft.advancements.triggers.CuredZombieVillagerTrigger.TriggerInstance) {
			return "治愈僵尸村民";
		}
		if (instance instanceof net.minecraft.advancements.triggers.ConstructBeaconTrigger.TriggerInstance) {
			return "搭建信标";
		}
		if (instance instanceof net.minecraft.advancements.triggers.LevitationTrigger.TriggerInstance) {
			return "飘浮";
		}
		if (instance instanceof net.minecraft.advancements.triggers.UsedEnderEyeTrigger.TriggerInstance) {
			return "使用末影之眼";
		}
		if (instance instanceof net.minecraft.advancements.triggers.ShotCrossbowTrigger.TriggerInstance) {
			return "用弩射击";
		}
		if (instance instanceof net.minecraft.advancements.triggers.RecipeUnlockedTrigger.TriggerInstance) {
			return "解锁配方";
		}
		if (instance instanceof net.minecraft.advancements.triggers.PlayerTrigger.TriggerInstance) {
			return "满足特定条件";
		}

		if (name.contains("inventorychange")) {
			return "获得物品";
		}
		if (name.contains("killed")) {
			return "击杀生物";
		}
		if (name.contains("consume")) {
			return "吃掉物品";
		}
		if (name.contains("enterblock")) {
			return "进入方块";
		}
		if (name.contains("location") || name.contains("distance")) {
			return "到达指定位置";
		}
		if (name.contains("recipe")) {
			return "合成 / 解锁配方";
		}
		return criterion.trigger().getClass().getSimpleName().replace("Trigger", "");
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

	private static String itemText(net.minecraft.advancements.predicates.ItemPredicate predicate) {
		Optional<HolderSet<Item>> items = predicate.items();
		int min = predicate.count().min().orElse(1);
		String count = min > 1 ? min + " 个 " : "";
		if (items.isEmpty()) {
			return count + "物品";
		}
		List<String> names = new ArrayList<>();
		int i = 0;
		for (var holder : items.get()) {
			if (i++ >= 3) {
				names.add("…");
				break;
			}
			names.add("{{item:" + BuiltInRegistries.ITEM.getKey(holder.value()) + "}}");
		}
		return count + String.join(" 或 ", names);
	}

	private static String blockText(HolderSet<Block> blocks) {
		List<String> names = new ArrayList<>();
		int i = 0;
		for (var holder : blocks) {
			if (i++ >= 3) {
				names.add("…");
				break;
			}
			names.add("{{block:" + BuiltInRegistries.BLOCK.getKey(holder.value()) + "}}");
		}
		return "进入 " + String.join(" 或 ", names);
	}
}
