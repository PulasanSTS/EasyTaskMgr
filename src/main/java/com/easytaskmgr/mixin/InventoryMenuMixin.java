package com.easytaskmgr.mixin;

import com.easytaskmgr.EasyTaskMgr;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 背包 2x2 合成格的 shift 快速合成计数（逻辑同 {@link CraftingMenuMixin}）。
 */
@Mixin(InventoryMenu.class)
public class InventoryMenuMixin {
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
