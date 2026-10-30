package com.easytaskmgr.mixin;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 工作台 shift 快速合成计数。
 *
 * <p>shift 一键取走成品时，服务端走的是 {@code quickMoveStack(索引 0 = 结果槽)}，
 * 不一定会再触发 {@code ResultSlot#onTake} 的可见路径；这里在返回值处补一次统计。
 * {@link EasyTaskMgr#notifyCraft} 内部会按「玩家 + tick + 物品 + 数量」去重，
 * 所以和 onTake 钩子同时触发也只算一次。
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {
	@Inject(method = "quickMoveStack", at = @At("RETURN"))
	private void easytaskmgr$onQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (index != 0 || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		ItemStack moved = cir.getReturnValue();
		if (moved != null && !moved.isEmpty()) {
			EasyTaskMgr.notifyCraft(serverPlayer, moved);
		}
	}
}
