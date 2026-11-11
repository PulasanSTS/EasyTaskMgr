package com.easytaskmgr.client;

import com.easytaskmgr.config.ModConfig;
import com.easytaskmgr.i18n.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;

/** 客户端小工具：语言、物品/成就名称、背包计数、聊天提示、文本换行。 */
public final class ClientUtil {
	private ClientUtil() {
	}

	public static I18n.Lang lang() {
		String cfg = ModConfig.get().language;
		if ("zh_cn".equals(cfg)) {
			return I18n.Lang.ZH_CN;
		}
		if ("en_us".equals(cfg)) {
			return I18n.Lang.EN_US;
		}
		String code = Minecraft.getInstance().options.languageCode;
		return I18n.Lang.byCode(code);
	}

	public static String t(String key, Object... args) {
		return I18n.t(lang(), key, args);
	}

	public static void info(String message) {
		chat(message, ChatFormatting.GREEN, false);
	}

	public static void success(String message) {
		chat(message, ChatFormatting.GREEN, true);
	}

	public static void warn(String message) {
		chat(message, ChatFormatting.YELLOW, false);
	}

	/** 完成 / 提醒：聊天栏 + 音效 + HUD 顶部闪烁。 */
	public static void remind(String message) {
		chat(message, ChatFormatting.GREEN, true);
		Hud.flash();
	}

	private static void chat(String message, ChatFormatting color, boolean sound) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Component line = Component.literal("[EasyTaskMgr] ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(message).withStyle(color));
		mc.player.displayClientMessage(line, false);
		if (sound && ModConfig.get().soundEnabled) {
			mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F);
		}
	}

	/** 背包里的数量（含副手与潜影盒内容物）。 */
	public static int countItem(Minecraft mc, Item item) {
		if (mc.player == null) {
			return 0;
		}
		int total = 0;
		for (ItemStack stack : mc.player.getInventory().items) {
			total += countIn(stack, item);
		}
		total += countIn(mc.player.getOffhandItem(), item);
		for (ItemStack stack : mc.player.getInventory().items) {
			if (!(stack.getItem() instanceof BlockItem blockItem)
					|| !(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
				continue;
			}
			ItemContainerContents contents = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
			if (contents == null) {
				continue;
			}
			NonNullList<ItemStack> inner = NonNullList.withSize(27, ItemStack.EMPTY);
			contents.copyInto(inner);
			for (ItemStack stack2 : inner) {
				total += countIn(stack2, item);
			}
		}
		return total;
	}

	private static int countIn(ItemStack stack, Item item) {
		return stack != null && !stack.isEmpty() && stack.is(item) ? stack.getCount() : 0;
	}

	/** 按 id 找物品；找不到返回 null。 */
	public static Item item(String id) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		return key == null ? null : BuiltInRegistries.ITEM.get(key);
	}

	public static String itemName(String id) {
		Item item = item(id);
		return item == null || item == net.minecraft.world.item.Items.AIR
				? id
				: item.getDescription().getString();
	}

	/** 成就显示名（客户端拿不到进度，只用于展示）。 */
	public static String advancementName(String id) {
		Minecraft mc = Minecraft.getInstance();
		ResourceLocation key = ResourceLocation.tryParse(id);
		if (mc.getConnection() == null || key == null) {
			return id;
		}
		var holder = mc.getConnection().getAdvancements().get(key);
		if (holder == null) {
			return id;
		}
		Component name = holder.value().display().map(d -> (Component) d.getTitle()).orElse(null);
		return name == null ? id : name.getString();
	}

	public static List<String> wrap(Font font, String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return lines;
		}
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			current.append(c);
			if (font.width(current.toString()) > maxWidth && current.length() > 1) {
				lines.add(current.toString());
				current.setLength(0);
			}
		}
		if (current.length() > 0) {
			lines.add(current.toString());
		}
		return lines;
	}

	/** 居中绘制带阴影的文本（原生 drawCenteredString 不带阴影）。 */
	public static void centered(net.minecraft.client.gui.GuiGraphics graphics,
			net.minecraft.client.gui.Font font, String text, int centerX, int y, int color) {
		graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, true);
	}

	/** 超长文本末尾截断。 */
	public static String clip(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		StringBuilder sb = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (font.width(sb.toString() + c + "…") > maxWidth) {
				break;
			}
			sb.append(c);
		}
		return sb + "…";
	}
}
