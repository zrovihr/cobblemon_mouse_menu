package com.cobblemonmousemenu;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket;
import com.cobblemonmousemenu.client.gui.TrainerTeamScreen;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.cobblemonmousemenu.net.PartyItemSwapPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CobblemonMouseMenuClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-mousemenu");
	private static boolean mouseMenuActive = false;
	private static boolean wasKeyPressed = false;
	private static KeyMapping toggleKey;
	private static int hoveredSlot = -1;

	// Mirror Cobblemon's PartyOverlay layout exactly so the highlight lines up with the real slot.
	// (PartyOverlay.kt: SLOT_WIDTH=62, SLOT_HEIGHT=30, SLOT_SPACING=4, slot drawn at panelX=0.)
	private static final int SLOT_WIDTH = 62;
	private static final int SLOT_HEIGHT = 30;
	private static final int SLOT_STEP = 34;   // SLOT_HEIGHT + SLOT_SPACING
	private static final int SLOT_X = 0;        // panelX in PartyOverlay

	// Right-click context menu, rendered as a HUD popup (NOT a Screen) so the rest of the UI
	// (party panel, hotbar) stays visible. Lives inside mouse-menu mode.
	private static final int CTX_ROW_H = 15;
	private static final int CTX_PAD = 4;
	private static boolean contextMenuOpen = false;
	private static int ctxSlot = -1;
	private static int ctxX, ctxY, ctxW, ctxH;
	private static String ctxTitle = "";
	private static final List<String> ctxRows = new ArrayList<>();
	private static final List<Boolean> ctxRowEnabled = new ArrayList<>();

	public static boolean isMouseMenuActive() {
		return mouseMenuActive;
	}

	public static boolean isContextMenuOpen() {
		return contextMenuOpen;
	}

	public static void closeContextMenu() {
		contextMenuOpen = false;
		ctxSlot = -1;
		ctxRows.clear();
		ctxRowEnabled.clear();
	}

	/** Leave mouse-menu mode because we're about to open a screen (the screen owns the cursor). */
	public static void closeMouseMenu() {
		mouseMenuActive = false;
		hoveredSlot = -1;
	}

	public static int getSlotAtMouse(Minecraft client) {
		if (!PartyOverlay.Companion.canRender()) return -1;
		if (client.player == null) return -1;
		if (CobblemonClient.INSTANCE == null) return -1;

		List<Pokemon> party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
		if (party.isEmpty()) return -1;

		int guiWidth = client.getWindow().getGuiScaledWidth();
		int guiHeight = client.getWindow().getGuiScaledHeight();
		double guiScale = (double) client.getWindow().getWidth() / guiWidth;

		int mouseGuiX = (int) (client.mouseHandler.xpos() / guiScale);
		int mouseGuiY = (int) (client.mouseHandler.ypos() / guiScale);

		int totalHeight = party.size() * SLOT_HEIGHT;
		int startY = guiHeight / 2 - totalHeight / 2 - 10;

		for (int i = 0; i < party.size(); i++) {
			int slotY = startY + i * SLOT_STEP;
			if (mouseGuiX >= SLOT_X && mouseGuiX <= SLOT_X + SLOT_WIDTH
					&& mouseGuiY >= slotY && mouseGuiY <= slotY + SLOT_HEIGHT) {
				Pokemon pokemon = party.get(i);
				if (pokemon != null) {
					return i;
				}
				return -1;
			}
		}
		return -1;
	}

	/** Right-click action: open the party-slot context menu (send out / item swap) at the cursor. */
	public static void openContextMenuForSlot(int slotIndex) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;

		List<Pokemon> party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
		if (slotIndex < 0 || slotIndex >= party.size() || party.get(slotIndex) == null) return;
		Pokemon pokemon = party.get(slotIndex);

		ctxSlot = slotIndex;
		ctxTitle = pokemon.getDisplayName(false).getString();
		ctxRows.clear();
		ctxRowEnabled.clear();

		// Row 0: send out / recall (toggles server-side; labelled from the synced state).
		boolean isOut = pokemon.getState() instanceof SentOutState;
		ctxRows.add(isOut ? "Recall" : "Send out");
		ctxRowEnabled.add(true);

		// Row 1: unified take/give held item (needs this mod on the server).
		boolean canSwap = ClientPlayNetworking.canSend(PartyItemSwapPayload.TYPE);
		ItemStack hand = client.player.getMainHandItem();
		ItemStack held = pokemon.heldItem();
		String itemLabel;
		if (!hand.isEmpty()) {
			itemLabel = "Give " + hand.getHoverName().getString();
		} else if (!held.isEmpty()) {
			itemLabel = "Take " + held.getHoverName().getString();
		} else {
			itemLabel = "Give/Take item";
		}
		ctxRows.add(itemLabel);
		ctxRowEnabled.add(canSwap);

		// Size the popup.
		int guiWidth = client.getWindow().getGuiScaledWidth();
		int guiHeight = client.getWindow().getGuiScaledHeight();
		int w = client.font.width(ctxTitle) + CTX_PAD * 2;
		for (String r : ctxRows) {
			w = Math.max(w, client.font.width(r) + CTX_PAD * 2);
		}
		ctxW = Math.max(96, w);
		ctxH = 13 + ctxRows.size() * CTX_ROW_H + CTX_PAD;

		// Anchor it to the RIGHT of the party panel, aligned to the clicked slot. The party portraits
		// are 3D models drawn on top of flat HUD quads, so a popup overlapping the panel would render
		// behind them — placing it clear of the panel avoids the layering problem entirely.
		int totalHeight = party.size() * SLOT_HEIGHT;
		int startY = guiHeight / 2 - totalHeight / 2 - 10;
		int slotY = startY + slotIndex * SLOT_STEP;
		ctxX = Math.max(2, Math.min(SLOT_X + SLOT_WIDTH + 8, guiWidth - ctxW - 2));
		ctxY = Math.max(2, Math.min(slotY, guiHeight - ctxH - 2));

		contextMenuOpen = true;
	}

	/**
	 * Handle a click while the context menu is open: run the row under the cursor (if enabled) or
	 * dismiss. Always consumes the click. Called from the mouse mixin.
	 */
	public static void clickContextMenu() {
		Minecraft client = Minecraft.getInstance();
		int guiWidth = client.getWindow().getGuiScaledWidth();
		double guiScale = (double) client.getWindow().getWidth() / guiWidth;
		int mx = (int) (client.mouseHandler.xpos() / guiScale);
		int my = (int) (client.mouseHandler.ypos() / guiScale);

		if (mx >= ctxX && mx < ctxX + ctxW && my >= ctxY + 13 && my < ctxY + ctxH) {
			int idx = (my - (ctxY + 13)) / CTX_ROW_H;
			if (idx >= 0 && idx < ctxRows.size() && Boolean.TRUE.equals(ctxRowEnabled.get(idx))) {
				runContextAction(idx);
			}
		}
		closeContextMenu();
	}

	private static void runContextAction(int idx) {
		if (ctxSlot < 0) return;
		switch (idx) {
			case 0 -> new SendOutPokemonPacket(ctxSlot).sendToServer();
			case 1 -> {
				if (ClientPlayNetworking.canSend(PartyItemSwapPayload.TYPE)) {
					ClientPlayNetworking.send(new PartyItemSwapPayload(ctxSlot));
				}
			}
			default -> {
			}
		}
	}

	private static void renderContextMenu(GuiGraphics g, Minecraft client) {
		if (!contextMenuOpen || ctxRows.isEmpty()) return;

		int guiWidth = client.getWindow().getGuiScaledWidth();
		double guiScale = (double) client.getWindow().getWidth() / guiWidth;
		int mx = (int) (client.mouseHandler.xpos() / guiScale);
		int my = (int) (client.mouseHandler.ypos() / guiScale);

		g.fill(ctxX - 1, ctxY - 1, ctxX + ctxW + 1, ctxY + ctxH + 1, 0xFF1A2030);
		g.fill(ctxX, ctxY, ctxX + ctxW, ctxY + ctxH, 0xF00C0E16);
		g.fill(ctxX, ctxY, ctxX + ctxW, ctxY + 2, 0xFF55AAFF);
		g.drawString(client.font, ctxTitle, ctxX + CTX_PAD, ctxY + 3, 0xFF7FD4FF, false);

		int y = ctxY + 13;
		for (int i = 0; i < ctxRows.size(); i++) {
			boolean enabled = Boolean.TRUE.equals(ctxRowEnabled.get(i));
			boolean hover = enabled && mx >= ctxX && mx < ctxX + ctxW && my >= y && my < y + CTX_ROW_H;
			if (hover) {
				g.fill(ctxX + 1, y, ctxX + ctxW - 1, y + CTX_ROW_H, 0x553A86C8);
			}
			int color = enabled ? (hover ? 0xFFFFFFFF : 0xFFD8DEE6) : 0xFF5A6270;
			g.drawString(client.font, ctxRows.get(i), ctxX + CTX_PAD, y + 3, color, false);
			y += CTX_ROW_H;
		}
	}

	public static void openSummaryForSlot(int slotIndex) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;

		List<Pokemon> party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
		if (slotIndex < 0 || slotIndex >= party.size()) return;
		if (party.get(slotIndex) == null) return;

		mouseMenuActive = false;
		client.mouseHandler.grabMouse();
		Summary.Companion.open(party, true, slotIndex);
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Cobblemon Mouse Menu initialized");

		// Client side of the trainer-team feature (request + receive). Step 1: debug-key verified.
		com.cobblemonmousemenu.net.TrainerTeamClient.init();

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.cobblemon-mousemenu.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_ALT,
				"key.categories.cobblemon-mousemenu"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;

			boolean keyDown = toggleKey.isDown();
			boolean justPressed = keyDown && !wasKeyPressed;
			wasKeyPressed = keyDown;

			if (client.screen != null) {
				// A screen is open: keep mouse-menu state off, and let the toggle key dismiss the
				// inspector / summary screens this mod opens (same key opens & closes).
				mouseMenuActive = false;
				hoveredSlot = -1;
				if (justPressed && (client.screen instanceof TrainerTeamScreen
						|| client.screen instanceof Summary)) {
					client.setScreen(null);
				}
				return;
			}

			if (justPressed) {
				mouseMenuActive = !mouseMenuActive;
				if (mouseMenuActive) {
					client.mouseHandler.releaseMouse();
				} else {
					client.mouseHandler.grabMouse();
					hoveredSlot = -1;
					closeContextMenu();
				}
			}

			if (mouseMenuActive) {
				hoveredSlot = getSlotAtMouse(client);
			} else {
				hoveredSlot = -1;
			}
		});

		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			Minecraft client = Minecraft.getInstance();
			if (client.options.hideGui) return;

			if (mouseMenuActive && client.screen == null) {
				String keyName = toggleKey.getTranslatedKeyMessage().getString();
				Component text = Component.literal("Cobblemon Mouse Mode: ON (" + keyName + ")");
				int width = client.getWindow().getGuiScaledWidth();
				int height = client.getWindow().getGuiScaledHeight();
				int x = (width - client.font.width(text)) / 2;
				int y = height / 2 - 16;
				drawContext.drawString(client.font, text, x, y, 0x55FF55);
			}

			if (mouseMenuActive && client.screen == null) {
				int guiHeight = client.getWindow().getGuiScaledHeight();
				List<Pokemon> party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
				int totalHeight = party.size() * SLOT_HEIGHT;
				int startY = guiHeight / 2 - totalHeight / 2 - 10;

				if (hoveredSlot >= 0 && !client.options.hideGui) {
					int slotY = startY + hoveredSlot * SLOT_STEP;
					((GuiGraphics) drawContext).fill(SLOT_X, slotY, SLOT_X + SLOT_WIDTH, slotY + SLOT_HEIGHT, 0x44FFFFFF);
				}

				// Click legend on the left, just below the party panel, so players know the controls.
				GuiGraphics g = (GuiGraphics) drawContext;
				int legendY = startY + party.size() * SLOT_STEP + 4;
				g.drawString(client.font, Component.literal("Left-click  ").withStyle(s -> s.withColor(0xFF7FD4FF))
						.append(Component.literal("Inspect").withStyle(s -> s.withColor(0xFFD8DEE6))),
						SLOT_X + 2, legendY, 0xFFFFFFFF, true);
				g.drawString(client.font, Component.literal("Right-click ").withStyle(s -> s.withColor(0xFFE0913A))
						.append(Component.literal("Actions").withStyle(s -> s.withColor(0xFFD8DEE6))),
						SLOT_X + 2, legendY + 11, 0xFFFFFFFF, true);

				// The right-click context popup, drawn on top of everything else (HUD, not a Screen).
				renderContextMenu(g, client);
			}
		});
	}
}
