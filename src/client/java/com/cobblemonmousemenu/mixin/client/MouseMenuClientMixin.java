package com.cobblemonmousemenu.mixin.client;

import com.cobblemonmousemenu.CobblemonMouseMenuClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMenuClientMixin {

	@Inject(at = @At("HEAD"), method = "turnPlayer", cancellable = true)
	private void onTurnPlayer(CallbackInfo info) {
		if (CobblemonMouseMenuClient.isMouseMenuActive()) {
			info.cancel();
		}
	}

	@Inject(at = @At("HEAD"), method = "onPress", cancellable = true)
	private void onMousePress(long window, int button, int action, int mods, CallbackInfo info) {
		if (!CobblemonMouseMenuClient.isMouseMenuActive()) return;

		if (action == GLFW.GLFW_PRESS) {
			Minecraft client = Minecraft.getInstance();
			if (client.screen == null) {
				// A click while the context popup is open is handled by the popup (run row / dismiss).
				if (CobblemonMouseMenuClient.isContextMenuOpen()) {
					CobblemonMouseMenuClient.clickContextMenu();
					info.cancel();
					return;
				}
				if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
					int slot = CobblemonMouseMenuClient.getSlotAtMouse(client);
					if (slot >= 0) {
						CobblemonMouseMenuClient.openSummaryForSlot(slot);
					} else {
						// No party slot under the cursor: try a hovered trainer NPC instead.
						com.cobblemonmousemenu.net.TrainerTeamClient.onClick();
					}
				} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					int slot = CobblemonMouseMenuClient.getSlotAtMouse(client);
					if (slot >= 0) {
						CobblemonMouseMenuClient.openContextMenuForSlot(slot);
					}
				}
			}
		}

		info.cancel();
	}
}
