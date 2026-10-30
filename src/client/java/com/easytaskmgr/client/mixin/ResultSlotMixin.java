package com.easytaskmgr.client.mixin;

import com.easytaskmgr.client.ClientNet;
import com.easytaskmgr.client.ClientTasks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端兜底钩子：服务端没装本模组时才本地计数。
 *
 * <p>服务端装了模组时，合成事件由 {@code ResultSlotServerMixin} 通过
 * {@code CraftEventS2C} 推送，避免单机和联机重复计数。
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void easytaskmgr$onCraft(Player player, ItemStack stack, CallbackInfo ci) {
		if (player == null || !player.level().isClientSide()) {
			return;
		}
		if (ClientNet.serverHasMod()) {
			return;
		}
		ClientTasks.onCrafted(stack);
	}
}
