package com.easytaskmgr.command;

import com.easytaskmgr.share.SharedTasks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /etm invite <玩家名>}：把自己的特殊共享任务邀请给某个玩家。
 *
 * <p>Fabric 版是客户端命令（只影响自己发出的包）；NeoForge 没有客户端命令，
 * 所以改成服务端命令：服务端已经知道「谁是发布者、有哪些特殊共享任务」，
 * 直接把当前玩家发布的所有特殊共享任务都邀请给目标玩家。
 */
public final class EtmCommand {
	private EtmCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("etm")
				.then(Commands.literal("invite")
						.then(Commands.argument("player", StringArgumentType.word())
								.executes(context -> invite(context.getSource(),
										StringArgumentType.getString(context, "player"))))));
	}

	private static int invite(CommandSourceStack source, String target) {
		ServerPlayer player;
		try {
			player = source.getPlayerOrException();
		} catch (Exception e) {
			source.sendFailure(Component.translatable("easytaskmgr.command.playerOnly"));
			return 0;
		}
		int count = SharedTasks.inviteAllSpecial(player, target);
		if (player.getServer() != null) {
			// 邀请后立刻落盘并广播，在线被邀请者马上能看到（review #9）
			SharedTasks.saveIfDirty(player.getServer());
			SharedTasks.broadcast(player.getServer());
		}
		if (count == 0) {
			source.sendFailure(Component.translatable("easytaskmgr.command.noSpecialTasks"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("easytaskmgr.command.invited", count, target), false);
		return count;
	}
}
