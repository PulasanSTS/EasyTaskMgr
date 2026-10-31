package com.easytaskmgr.client;

import com.easytaskmgr.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * 提醒器：聊天栏文字 + 提示音。所有「任务完成 / 到点提醒」都从这里发出。
 */
public final class Notifier {
	private Notifier() {
	}

	public static void info(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		mc.player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
	}

	public static void success(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		mc.player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.GREEN)));
		playChime(mc);
	}

	/** 警告 / 失败提示：红色、不响铃。 */
	public static void warn(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		mc.player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.RED)));
	}

	public static void remind(String message) {		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		mc.player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.GOLD)));
		playChime(mc);
	}

	private static MutableComponent prefix() {
		return Component.literal("[EasyTaskMgr] ").withStyle(ChatFormatting.AQUA);
	}

	private static void playChime(Minecraft mc) {
		if (!ModConfig.get().soundEnabled || mc.player == null) {
			return;
		}
		mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F);
	}
}
