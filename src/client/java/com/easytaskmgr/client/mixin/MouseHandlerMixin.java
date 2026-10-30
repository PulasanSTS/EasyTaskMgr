package com.easytaskmgr.client.mixin;

import com.easytaskmgr.client.TaskHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD 点击完成：开启「HUD 交互」（V）后鼠标会释放，
 * 这里拦截鼠标按下事件，命中普通任务行就直接完成并吞掉事件（避免顺手砍一刀）。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void easytaskmgr$onButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
		if (action != 1 || button.button() > 1) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || !TaskHud.isInteractive()) {
			return;
		}
		MouseHandler self = (MouseHandler) (Object) this;
		double x = self.getScaledXPos(mc.getWindow());
		double y = self.getScaledYPos(mc.getWindow());
		if (TaskHud.tryComplete(mc, x, y)) {
			ci.cancel();
		}
	}
}
