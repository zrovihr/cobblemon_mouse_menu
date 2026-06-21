package com.cobblemonmousemenu.mixin.client;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds a "Copy Box" button to Cobblemon's PC screen. When pressed it builds a plain-text
 * list of every Pokémon in the currently viewed box — name, level, gender/shiny, nature,
 * IVs and EVs — and puts it on the system clipboard so the player can paste it anywhere.
 *
 * Extends {@link Screen} purely so the mixin can call the protected {@code addRenderableWidget}
 * that PCGUI inherits; at runtime {@code this} is the real PCGUI instance.
 */
@Mixin(PCGUI.class)
public abstract class PCGUIMixin extends Screen {

	private PCGUIMixin(Component title) {
		super(title);
	}

	// Mirror of PCGUI's base-frame geometry so we can anchor the button to the same spot.
	private static final int BASE_WIDTH = 349;
	private static final int BASE_HEIGHT = 205;

	// IV/EV display order matches Cobblemon's own stat panel: HP, Atk, Def, SpA, SpD, Spe.
	private static final Stat[] STATS = {
			Stats.HP, Stats.ATTACK, Stats.DEFENCE,
			Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
	};
	private static final String[] STAT_LABELS = { "HP", "Atk", "Def", "SpA", "SpD", "Spe" };

	@Inject(method = "init", at = @At("TAIL"))
	private void cobblemonmousemenu$addCopyButton(CallbackInfo ci) {
		int x = (this.width - BASE_WIDTH) / 2;
		int y = (this.height - BASE_HEIGHT) / 2;

		Component label = Component.literal("Copy Box Info");
		int btnH = 12;
		int btnW = Minecraft.getInstance().font.width(label) + 8;
		// Anchor just below the PC frame, but clamp fully on-screen so it never clips off an edge
		// (placing it above the frame went off the top of the screen at higher GUI scales).
		int btnX = Math.max(2, Math.min(x, this.width - btnW - 2));
		int btnY = Math.max(2, Math.min(y + BASE_HEIGHT + 3, this.height - btnH - 2));

		Button copyButton = Button.builder(
						label,
						b -> cobblemonmousemenu$copyCurrentBox())
				.bounds(btnX, btnY, btnW, btnH)
				.tooltip(Tooltip.create(Component.literal(
						"Copy this box's Pokémon (levels, IVs, EVs) to the clipboard")))
				.build();

		this.addRenderableWidget(copyButton);
	}

	private void cobblemonmousemenu$copyCurrentBox() {
		PCGUI self = (PCGUI) (Object) this;
		ClientPC pc = self.getPc();
		int boxIndex = self.getStorage().getBox();

		List<ClientBox> boxes = pc.getBoxes();
		if (boxIndex < 0 || boxIndex >= boxes.size()) return;
		ClientBox box = boxes.get(boxIndex);

		StringBuilder sb = new StringBuilder();
		String boxLabel = box.getName() != null ? box.getName().getString() : ("Box " + (boxIndex + 1));
		sb.append(boxLabel).append('\n');
		sb.append("====================\n");

		int count = 0;
		for (Pokemon pokemon : box.getSlots()) {
			if (pokemon == null) continue;
			count++;
			cobblemonmousemenu$appendPokemon(sb, count, pokemon);
		}

		if (count == 0) {
			sb.append("(empty)\n");
		}

		Minecraft client = Minecraft.getInstance();
		client.keyboardHandler.setClipboard(sb.toString());
		if (client.player != null) {
			client.player.displayClientMessage(
					Component.literal("Copied " + count + " Pokémon from " + boxLabel + " to clipboard"),
					false);
		}
	}

	private void cobblemonmousemenu$appendPokemon(StringBuilder sb, int index, Pokemon pokemon) {
		sb.append(index).append(". ")
				.append(pokemon.getDisplayName(false).getString())
				.append("  Lv.").append(pokemon.getLevel());

		String gender = cobblemonmousemenu$genderSymbol(pokemon.getGender());
		if (!gender.isEmpty()) sb.append(' ').append(gender);
		if (pokemon.getShiny()) sb.append(" ✧"); // ✧ shiny marker
		// Nature.getDisplayName() returns a lang key (e.g. "cobblemon.nature.lonely"); resolve it.
		sb.append("  ").append(Component.translatable(pokemon.getNature().getDisplayName()).getString());
		sb.append('\n');

		sb.append("   IVs  ");
		cobblemonmousemenu$appendStats(sb, pokemon, true);
		sb.append('\n');

		sb.append("   EVs  ");
		cobblemonmousemenu$appendStats(sb, pokemon, false);
		sb.append('\n');
	}

	private void cobblemonmousemenu$appendStats(StringBuilder sb, Pokemon pokemon, boolean ivs) {
		for (int i = 0; i < STATS.length; i++) {
			if (i > 0) sb.append(" / ");
			int value = ivs
					? pokemon.getIvs().getOrDefault(STATS[i])
					: pokemon.getEvs().getOrDefault(STATS[i]);
			sb.append(STAT_LABELS[i]).append(' ').append(value);
		}
	}

	private String cobblemonmousemenu$genderSymbol(Gender gender) {
		return switch (gender) {
			case MALE -> "♂";   // ♂
			case FEMALE -> "♀"; // ♀
			default -> "";
		};
	}
}
