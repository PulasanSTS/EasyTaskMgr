package com.easytaskmgr.mixin;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端权威的合成计数：取走合成结果时，把物品 id + 数量推给该玩家。
 *
 * <p>同时覆盖「点一下拿一个」和「shift 一键全部拿走」两种操作。
 */
@Mixin(ResultSlot.class)
public class ResultSlotServerMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void easytaskmgr$onTake(Player player, ItemStack stack, CallbackInfo ci) {
		if (player.level().isClientSide() || stack.isEmpty()) {
			return;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			EasyTaskMgr.notifyCraft(serverPlayer, stack);
		}
	}
}
