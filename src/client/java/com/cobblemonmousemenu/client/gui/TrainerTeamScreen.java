package com.cobblemonmousemenu.client.gui;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemonmousemenu.CobblemonMouseMenu;
import com.cobblemonmousemenu.net.TeamEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A Cobblemon-styled trainer inspector: a selectable team list on the left and a full detail pane on
 * the right (full-body render, types, ability, nature, held item, moves and per-stat IV/EV with
 * computed final stats). The header shows the battle format (1v1 / 2v2).
 *
 * <p>Pokemon rendering uses Cobblemon's own {@code drawProfile}/{@code drawPosablePortrait}. Those
 * touch a lot of internal render state, so they're wrapped defensively — if they ever throw, model
 * rendering is disabled for the session and the (still useful) text keeps working.</p>
 */
public final class TrainerTeamScreen extends Screen {

    private static final int PROFILE_BOX = 66;
    private static final int THUMB = 26;
    private static final String[] STAT_LABELS = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    private static final Stat[] STAT_ORDER = {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };

    private static boolean modelsBroken = false;

    private final Component trainerName;
    private final String battleFormat;
    private final List<TeamEntry> team;
    private int selected = 0;
    private int ticks = 0;
    private boolean overview = false;

    // Layout, recomputed each frame; read by mouseClicked.
    private int px, py, pw, ph, bodyY, leftW, listRowH;
    // Close (X) button bounds in the header, recomputed each frame.
    private int closeX, closeY, closeSize;
    // "All / Detail" view-toggle button bounds in the header.
    private int toggleX, toggleY, toggleW, toggleH;
    // Overview is drawn in a fixed virtual canvas then scaled to fit the window. These capture that
    // transform so mouse hit-testing (and the portrait scissor, which is in raw screen px) can undo it.
    private boolean ovActive;
    private float ovScale = 1f;
    private float ovOffX, ovOffY;

    public TrainerTeamScreen(Component trainerName, String battleFormat, List<TeamEntry> team) {
        super(Component.literal("Trainer Team"));
        this.trainerName = trainerName;
        this.battleFormat = battleFormat == null ? "" : battleFormat;
        this.team = team;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        // Use the LIVE window dimensions, not the screen's cached width/height: the cached values can
        // lag a frame behind on open (the panel then overflows until a manual resize re-inits them).
        int sw = this.minecraft.getWindow().getGuiScaledWidth();
        int sh = this.minecraft.getWindow().getGuiScaledHeight();

        ovActive = overview && !team.isEmpty();
        if (ovActive) {
            // Lay the overview out in a fixed, roomy virtual canvas (so rows never overlap and move
            // names fit), then scale the whole thing to fit the window. This decouples it from the
            // integer GUI Scale entirely — small windows just render a smaller (still complete) sheet.
            int rows = Math.max(1, team.size());
            pw = 600;
            ph = 34 + rows * 64;
            ovScale = Math.min(1f, Math.min((sw - 8) / (float) pw, (sh - 8) / (float) ph));
            ovOffX = (sw - pw * ovScale) / 2f;
            ovOffY = (sh - ph * ovScale) / 2f;
            px = 0;
            py = 0;
        } else {
            pw = Math.min(448, sw - 32);
            ph = Math.min(276, sh - 32);
            px = (sw - pw) / 2;
            py = (sh - ph) / 2;
        }
        bodyY = py + 24;
        leftW = 132;

        if (ovActive) {
            g.pose().pushPose();
            g.pose().translate(ovOffX, ovOffY, 0);
            g.pose().scale(ovScale, ovScale, 1f);
        }
        // Mouse position in the (possibly scaled) panel space, for header hover tests.
        int vmx = ovActive ? (int) ((mouseX - ovOffX) / ovScale) : mouseX;
        int vmy = ovActive ? (int) ((mouseY - ovOffY) / ovScale) : mouseY;

        // Panel.
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF1A2030);
        g.fill(px, py, px + pw, py + ph, 0xF00C0E16);
        g.fill(px, py, px + pw, py + 2, 0xFF55AAFF);
        g.fill(px, py + ph - 2, px + pw, py + ph, 0xFF2B5C8A);
        if (!overview) {
            g.fill(px + leftW, bodyY, px + leftW + 1, py + ph - 4, 0x33FFFFFF); // detail-view divider
        }

        // Header: title + battle format + count + close button.
        g.drawString(this.font, Component.literal(trainerName.getString() + "'s Team"),
                px + 8, py + 8, 0xFF7FD4FF, true);

        // Close (X) button, far right.
        closeSize = 13;
        closeX = px + pw - closeSize - 4;
        closeY = py + 5;
        boolean closeHover = vmx >= closeX && vmx < closeX + closeSize
                && vmy >= closeY && vmy < closeY + closeSize;
        g.fill(closeX, closeY, closeX + closeSize, closeY + closeSize, closeHover ? 0xFFC8423A : 0x55FFFFFF);
        g.drawString(this.font, "✕", closeX + 3, closeY + 3, 0xFFFFFFFF, false);

        // View toggle: switch between the single-Pokemon detail and the all-team overview sheet.
        String toggleLabel = overview ? "Detail" : "All";
        toggleH = closeSize;
        toggleW = this.font.width(toggleLabel) + 8;
        toggleX = closeX - toggleW - 5;
        toggleY = closeY;
        boolean toggleHover = vmx >= toggleX && vmx < toggleX + toggleW
                && vmy >= toggleY && vmy < toggleY + toggleH;
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleHover ? 0xFF3A6EA8 : 0x44FFFFFF);
        g.drawString(this.font, toggleLabel, toggleX + 4, toggleY + 3, 0xFFFFFFFF, false);

        int rightX = toggleX - 6;
        String count = team.size() + "/6";
        rightX -= this.font.width(count);
        g.drawString(this.font, count, rightX, py + 8, 0xFF8090A0, false);
        if (!battleFormat.isEmpty()) {
            String fmt = battleFormat;
            int w = this.font.width(fmt) + 8;
            rightX -= w + 6;
            int col = battleFormat.startsWith("2") ? 0xFFE0913A : 0xFF3AA0E0;
            g.fill(rightX, py + 6, rightX + w, py + 17, col);
            g.drawString(this.font, fmt, rightX + 4, py + 8, 0xFFFFFFFF, false);
        }

        if (team.isEmpty()) {
            g.drawString(this.font, "(no Pokemon)", px + 8, bodyY + 6, 0xFFAAAAAA, false);
            if (ovActive) g.pose().popPose();
            return;
        }
        if (selected >= team.size()) {
            selected = 0;
        }

        if (overview) {
            renderOverview(g, partialTick);
            if (ovActive) g.pose().popPose();
            return;
        }

        // Left list.
        int bodyH = py + ph - 4 - bodyY;
        listRowH = Math.max(22, Math.min(42, bodyH / team.size()));
        for (int i = 0; i < team.size(); i++) {
            renderListRow(g, team.get(i), px + 4, bodyY + i * listRowH, leftW - 8, listRowH - 2, i == selected, partialTick);
        }

        // Right detail.
        renderDetail(g, team.get(selected), px + leftW + 8, bodyY + 2, pw - leftW - 16, partialTick);
    }

    private void renderListRow(GuiGraphics g, TeamEntry e, int x, int y, int w, int h, boolean sel, float partialTick) {
        g.fill(x, y, x + w, y + h, sel ? 0x553A86C8 : 0x18FFFFFF);
        if (sel) {
            g.fill(x, y, x + 2, y + h, 0xFF55AAFF);
        }
        int tb = THUMB;
        int iy = y + (h - tb) / 2;
        Species species = resolveSpecies(e.speciesId());
        if (!renderThumb(g, species, e, x + 3, iy, tb, partialTick)) {
            String letter = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase(Locale.ROOT);
            g.drawString(this.font, letter, x + 3 + tb / 2 - 3, iy + tb / 2 - 4, 0xFFB0B0B0, false);
        }
        int tx = x + tb + 6;
        g.drawString(this.font, trim(e.name(), w - tb - 10), tx, y + 4, sel ? 0xFFFFFFFF : 0xFFD8DEE6, false);
        String sub = "Lv " + e.level() + genderSuffix(e.gender()) + (e.shiny() ? " ✦" : "");
        g.drawString(this.font, sub, tx, y + h - 11, 0xFF9AA4B2, false);
    }

    private void renderDetail(GuiGraphics g, TeamEntry e, int x, int y, int w, float partialTick) {
        Species species = resolveSpecies(e.speciesId());

        // Profile render box (full body).
        g.fill(x, y, x + PROFILE_BOX, y + PROFILE_BOX, 0x33000000);
        if (!renderProfile(g, species, e, x, y, PROFILE_BOX, partialTick)) {
            renderThumb(g, species, e, x + (PROFILE_BOX - THUMB) / 2, y + (PROFILE_BOX - THUMB) / 2, THUMB, partialTick);
        }

        int rx = x + PROFILE_BOX + 8;
        int rw = w - PROFILE_BOX - 8;

        // Name + level + gender + shiny.
        g.drawString(this.font, e.name(), rx, y, 0xFFFFFFFF, true);
        String lvl = "Lv " + e.level();
        g.drawString(this.font, lvl, rx, y + 11, 0xFFB8C0CC, false);
        int gx = rx + this.font.width(lvl) + 6;
        String gsym = genderSymbol(e.gender());
        if (!gsym.isEmpty()) {
            g.drawString(this.font, gsym, gx, y + 11, e.gender().equalsIgnoreCase("MALE") ? 0xFF5C9DFF : 0xFFFF7BC0, false);
            gx += this.font.width(gsym) + 5;
        }
        if (e.shiny()) {
            g.drawString(this.font, "✦", gx, y + 11, 0xFFFFD24A, false);
        }

        // Types.
        if (species != null) {
            int bx = rx;
            for (ElementalType type : typesOf(species)) {
                String tn = type.getDisplayName().getString();
                int bw = this.font.width(tn) + 8;
                g.fill(bx, y + 22, bx + bw, y + 32, typeColor(type.getName()));
                g.drawString(this.font, tn, bx + 4, y + 23, 0xFFFFFFFF, false);
                bx += bw + 4;
            }
        }

        // Ability / nature / held.
        int my = y + 36;
        if (!e.ability().isEmpty()) {
            g.drawString(this.font, "Ability: " + prettify(e.ability()), rx, my, 0xFFC8D0DA, false);
            my += 11;
        }
        if (!e.nature().isEmpty()) {
            g.drawString(this.font, "Nature: " + prettify(e.nature()), rx, my, 0xFFC8D0DA, false);
            my += 11;
        }
        if (!e.heldItem().isEmpty()) {
            g.drawString(this.font, "Item: " + prettify(e.heldItem()), rx, my, 0xFFC8D0DA, false);
            my += 11;
        }

        // Moves (2x2) below the portrait row.
        int movesY = Math.max(y + PROFILE_BOX + 6, my + 4);
        g.drawString(this.font, "Moves", x, movesY, 0xFF7FD4FF, false);
        movesY += 11;
        int chipW = (w - 4) / 2;
        for (int i = 0; i < e.moveIds().size() && i < 4; i++) {
            MoveTemplate tmpl = Moves.INSTANCE.getByNameOrDummy(e.moveIds().get(i));
            int cx = x + (i % 2) * (chipW + 4);
            int cy = movesY + (i / 2) * 13;
            int col = typeColor(tmpl.getElementalType().getName());
            g.fill(cx, cy, cx + chipW, cy + 11, withAlpha(col, 0xCC));
            g.fill(cx, cy, cx + 2, cy + 11, darken(col));
            g.drawString(this.font, trim(tmpl.getDisplayName().getString(), chipW - 6), cx + 4, cy + 2, 0xFFFFFFFF, false);
        }

        // Stats. Clamp the row height so the 6 rows always fit above the panel bottom.
        int statsY = movesY + 2 * 13 + 6;
        int bottom = py + ph - 4;
        renderStats(g, e, species, x, statsY, w, bottom);
    }

    private void renderStats(GuiGraphics g, TeamEntry e, Species species, int x, int y, int w, int bottom) {
        g.drawString(this.font, "Stats", x, y, 0xFF7FD4FF, false);
        y += 11;

        int[] base = new int[6];
        if (species != null) {
            Map<Stat, Integer> bs = species.getBaseStats();
            for (int i = 0; i < 6; i++) {
                base[i] = bs.getOrDefault(STAT_ORDER[i], 0);
            }
        }
        Nature nature = e.nature().isEmpty() ? null : Natures.INSTANCE.getNature(e.nature());

        int[] finals = new int[6];
        int maxFinal = 1;
        for (int i = 0; i < 6; i++) {
            finals[i] = computeStat(i, base[i], statAt(e.ivs(), i), statAt(e.evs(), i), e.level(), nature);
            maxFinal = Math.max(maxFinal, finals[i]);
        }

        int labelW = 22;
        int valW = 24;
        int ivEvW = 64;
        int barX = x + labelW;
        int barW = w - labelW - valW - ivEvW - 6;
        // Fit all six rows in the remaining space (min 9px so the 8px bar still shows).
        int rowH = Math.max(9, Math.min(12, (bottom - y) / 6));
        int barH = Math.min(8, rowH - 2);

        for (int i = 0; i < 6; i++) {
            int ry = y + i * rowH;
            g.drawString(this.font, STAT_LABELS[i], x, ry, 0xFFAEB6C2, false);
            // bar
            g.fill(barX, ry, barX + barW, ry + barH, 0x33000000);
            int fill = Math.max(1, (int) (barW * (finals[i] / (float) maxFinal)));
            g.fill(barX, ry, barX + fill, ry + barH, statColor(finals[i]));
            // value
            String val = String.valueOf(finals[i]);
            g.drawString(this.font, val, barX + barW + 4, ry, 0xFFFFFFFF, false);
            // iv/ev
            String ie = "IV " + statAt(e.ivs(), i) + "  EV " + statAt(e.evs(), i);
            g.drawString(this.font, ie, x + w - this.font.width(ie), ry, 0xFF8A93A3, false);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Overview (all-team sheet, for screenshotting)
    // ---------------------------------------------------------------------------------------------

    /** One screenshot-friendly sheet: every Pokemon as a horizontal strip with its key info. */
    private void renderOverview(GuiGraphics g, float partialTick) {
        int x0 = px + 6;
        int top = bodyY + 2;
        int w = pw - 12;
        int bottom = py + ph - 6;
        int n = team.size();
        // Divide the available height evenly so the rows ALWAYS fit the panel (no min floor, which
        // is what pushed the last row off-screen). Cap the row height so a small team isn't giant.
        int cellH = Math.min(72, (bottom - top) / n);
        for (int i = 0; i < n; i++) {
            int y = top + i * cellH;
            if (i > 0) {
                g.fill(x0, y, x0 + w, y + 1, 0x18FFFFFF); // row separator
            }
            renderOverviewRow(g, team.get(i), x0, y + 1, w, cellH - 2, partialTick);
        }
    }

    private void renderOverviewRow(GuiGraphics g, TeamEntry e, int x, int y, int w, int h, float partialTick) {
        Species species = resolveSpecies(e.speciesId());

        // Portrait thumbnail on the far left.
        int box = Math.min(h, 46);
        int iy = y + (h - box) / 2;
        if (!renderThumb(g, species, e, x + 2, iy, box, partialTick)) {
            String letter = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase(Locale.ROOT);
            g.drawString(this.font, letter, x + 2 + box / 2 - 3, iy + box / 2 - 4, 0xFFB0B0B0, false);
        }

        // Column layout: meta | moves | stats.
        int colMeta = x + box + 8;
        int metaW = (int) (w * 0.30);
        int colMoves = colMeta + metaW + 6;
        int movesW = (int) (w * 0.27);
        int colStats = colMoves + movesW + 6;
        int statsW = x + w - colStats;

        // Meta: name + level/gender, types, ability/nature/item.
        String head = e.name() + "  Lv " + e.level() + genderSuffix(e.gender()) + (e.shiny() ? " ✦" : "");
        g.drawString(this.font, trim(head, metaW), colMeta, y + 1, 0xFFFFFFFF, true);
        int my = y + 11;
        if (species != null) {
            int bx = colMeta;
            for (ElementalType type : typesOf(species)) {
                String tn = type.getDisplayName().getString();
                int bw = this.font.width(tn) + 6;
                g.fill(bx, my, bx + bw, my + 9, typeColor(type.getName()));
                g.drawString(this.font, tn, bx + 3, my + 1, 0xFFFFFFFF, false);
                bx += bw + 3;
            }
            my += 11;
        }
        if (!e.ability().isEmpty()) {
            g.drawString(this.font, trim("Ability: " + prettify(e.ability()), metaW), colMeta, my, 0xFFC8D0DA, false);
            my += 10;
        }
        String ni = "";
        if (!e.nature().isEmpty()) {
            ni = prettify(e.nature());
        }
        if (!e.heldItem().isEmpty()) {
            ni = ni.isEmpty() ? ("@ " + prettify(e.heldItem())) : (ni + " · @" + prettify(e.heldItem()));
        }
        if (!ni.isEmpty()) {
            g.drawString(this.font, trim(ni, metaW), colMeta, my, 0xFFAEB6C2, false);
        }

        // Moves. Use a single full-width column (full names) only when the row is tall enough for
        // four stacked chips; otherwise fall back to a compact 2x2 that fits. This adapts to the
        // GUI scale and prevents the last move overflowing into the next Pokemon's row.
        g.drawString(this.font, "Moves", colMoves, y + 1, 0xFF7FD4FF, false);
        boolean singleCol = h - 11 >= 4 * 10;
        int chipW = singleCol ? movesW : (movesW - 4) / 2;
        for (int i = 0; i < e.moveIds().size() && i < 4; i++) {
            MoveTemplate tmpl = Moves.INSTANCE.getByNameOrDummy(e.moveIds().get(i));
            int cx = singleCol ? colMoves : colMoves + (i % 2) * (chipW + 4);
            int cy = singleCol ? y + 11 + i * 10 : y + 11 + (i / 2) * 11;
            int col = typeColor(tmpl.getElementalType().getName());
            g.fill(cx, cy, cx + chipW, cy + 9, withAlpha(col, 0xCC));
            g.drawString(this.font, trim(tmpl.getDisplayName().getString(), chipW - 4), cx + 3, cy + 1, 0xFFFFFFFF, false);
        }

        // Stats: 6 values in a 3x2 grid (HP Atk Def / SpA SpD Spe), each "Lbl 999".
        int[] base = new int[6];
        if (species != null) {
            Map<Stat, Integer> bs = species.getBaseStats();
            for (int i = 0; i < 6; i++) {
                base[i] = bs.getOrDefault(STAT_ORDER[i], 0);
            }
        }
        Nature nature = e.nature().isEmpty() ? null : Natures.INSTANCE.getNature(e.nature());
        // 3x2 grid of "label value" pairs. Both the label AND the value are left-aligned at fixed
        // offsets within each cell, so labels line up in one column and values line up in the next
        // — and each value stays tight to its own label rather than drifting toward the next pair.
        int labelW = 21;   // room for the widest label ("SpA") + a small gap
        int pairW = Math.min(labelW + 28, statsW / 3);
        for (int i = 0; i < 6; i++) {
            int finalStat = computeStat(i, base[i], statAt(e.ivs(), i), statAt(e.evs(), i), e.level(), nature);
            int cellX = colStats + (i % 3) * pairW;
            int sy = y + (i / 3) * 11;
            g.drawString(this.font, STAT_LABELS[i], cellX, sy, 0xFF8A93A3, false);
            g.drawString(this.font, String.valueOf(finalStat), cellX + labelW, sy, statTextColor(finalStat), false);
        }
    }

    /** Brighter variant of {@link #statColor} for small text on the overview sheet. */
    private static int statTextColor(int value) {
        float t = Math.min(1f, value / 200f);
        int r = (int) (t < 0.5f ? 235 : 235 - 150 * ((t - 0.5f) * 2));
        int gr = (int) (140 + 100 * Math.min(1f, t * 1.4f));
        return 0xFF000000 | (Math.max(60, Math.min(235, r)) << 16) | (Math.max(120, Math.min(240, gr)) << 8) | 0x70;
    }

    /** Standard Cobblemon/Pokemon stat formula. i==0 is HP. */
    private static int computeStat(int i, int base, int iv, int ev, int level, Nature nature) {
        int common = (int) Math.floor((2.0 * base + iv + Math.floor(ev / 4.0)) * level / 100.0);
        if (i == 0) {
            return common + level + 10;
        }
        double mod = 1.0;
        if (nature != null) {
            if (Objects.equals(nature.getIncreasedStat(), STAT_ORDER[i])) mod = 1.1;
            else if (Objects.equals(nature.getDecreasedStat(), STAT_ORDER[i])) mod = 0.9;
        }
        return (int) Math.floor((common + 5) * mod);
    }

    // ---------------------------------------------------------------------------------------------
    // Model rendering
    // ---------------------------------------------------------------------------------------------

    private boolean renderProfile(GuiGraphics g, Species species, TeamEntry e, int x, int y, int box, float partialTick) {
        if (modelsBroken || species == null) {
            return false;
        }
        try {
            PoseStack pose = g.pose();
            scissorBox(g, x, y, box);
            pose.pushPose();
            // drawProfile applies scale(s, s, -s) internally and the PROFILE sprite is a quad
            // spanning x in [-1, 1] and y in [0, 2] (growing DOWNWARD from the origin). So anchor
            // at the top-center of the box and use scale = box/2 to fill it exactly.
            pose.translate(x + box / 2.0, y, 0.0);
            GuiUtilsKt.drawProfile(species.getResourceIdentifier(), pose, state(e), partialTick, box / 2.0F);
            pose.popPose();
            g.disableScissor();
            return true;
        } catch (Throwable t) {
            disableModels(g, t);
            return false;
        }
    }

    private boolean renderThumb(GuiGraphics g, Species species, TeamEntry e, int x, int y, int box, float partialTick) {
        if (modelsBroken || species == null) {
            return false;
        }
        try {
            PoseStack pose = g.pose();
            scissorBox(g, x, y, box);
            pose.pushPose();
            float scale = 13F * (box / 21F);
            pose.translate(x + box / 2.0 - 1.0, y - 12.0 * (box / 21F), 0.0);
            GuiUtilsKt.drawPosablePortrait(
                    species.getResourceIdentifier(), pose, scale, 1F, false, state(e), partialTick,
                    0F, 0F, 0F, 0F, 0F, false, 1F, 1F, 1F, 1F);
            pose.popPose();
            g.disableScissor();
            return true;
        } catch (Throwable t) {
            disableModels(g, t);
            return false;
        }
    }

    /**
     * Enable a scissor for a virtual-space box. {@code enableScissor} works in raw screen pixels and
     * ignores the PoseStack, so when the overview is scaled we must convert the box ourselves.
     */
    private void scissorBox(GuiGraphics g, int x, int y, int box) {
        if (ovActive) {
            int sx = Math.round(ovOffX + x * ovScale);
            int sy = Math.round(ovOffY + y * ovScale);
            int sx2 = Math.round(ovOffX + (x + box) * ovScale);
            int sy2 = Math.round(ovOffY + (y + box) * ovScale);
            g.enableScissor(sx, sy, sx2, sy2);
        } else {
            g.enableScissor(x, y, x + box, y + box);
        }
    }

    private static FloatingState state(TeamEntry e) {
        FloatingState s = new FloatingState();
        Set<String> aspects = new HashSet<>(e.aspects());
        if (e.shiny()) {
            aspects.add("shiny");
        }
        s.setCurrentAspects(aspects);
        return s;
    }

    private void disableModels(GuiGraphics g, Throwable t) {
        modelsBroken = true;
        CobblemonMouseMenu.LOGGER.warn("[TrainerTeam] model rendering disabled: {}", t.toString());
        try {
            g.disableScissor();
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ticks >= 1) {
            // In overview the panel is drawn scaled; undo that transform to hit-test in panel space.
            double mx = ovActive ? (mouseX - ovOffX) / ovScale : mouseX;
            double my = ovActive ? (mouseY - ovOffY) / ovScale : mouseY;

            // Close (X) button.
            if (mx >= closeX && mx < closeX + closeSize
                    && my >= closeY && my < closeY + closeSize) {
                this.onClose();
                return true;
            }
            // View toggle (All / Detail).
            if (mx >= toggleX && mx < toggleX + toggleW
                    && my >= toggleY && my < toggleY + toggleH) {
                overview = !overview;
                return true;
            }
            // Select a list row (detail mode only).
            if (!overview && !team.isEmpty() && mx >= px + 4 && mx <= px + leftW - 4 && my >= bodyY) {
                int idx = (int) ((my - bodyY) / listRowH);
                if (idx >= 0 && idx < team.size()) {
                    selected = idx;
                    return true;
                }
            }
            // Click outside the panel closes.
            if (mx < px || mx > px + pw || my < py || my > py + ph) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!team.isEmpty()) {
            if (keyCode == 264) { // down
                selected = (selected + 1) % team.size();
                return true;
            }
            if (keyCode == 265) { // up
                selected = (selected - 1 + team.size()) % team.size();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static int statAt(List<Integer> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : 0;
    }

    private static Species resolveSpecies(String speciesId) {
        if (speciesId == null || speciesId.isEmpty()) {
            return null;
        }
        String key = speciesId.contains(":") ? speciesId.substring(speciesId.indexOf(':') + 1) : speciesId;
        return PokemonSpecies.INSTANCE.getByName(key.toLowerCase(Locale.ROOT));
    }

    private static List<ElementalType> typesOf(Species species) {
        java.util.ArrayList<ElementalType> list = new java.util.ArrayList<>();
        for (ElementalType t : species.getTypes()) {
            list.add(t);
        }
        return list;
    }

    private String trim(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        while (s.length() > 1 && this.font.width(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private static String genderSymbol(String gender) {
        if (gender == null) return "";
        if (gender.equalsIgnoreCase("MALE")) return "♂";
        if (gender.equalsIgnoreCase("FEMALE")) return "♀";
        return "";
    }

    private static String genderSuffix(String gender) {
        String s = genderSymbol(gender);
        return s.isEmpty() ? "" : " " + s;
    }

    private static String prettify(String raw) {
        String s = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        s = s.replace('_', ' ').trim();
        if (s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            out.append(cap ? Character.toUpperCase(c) : c);
            cap = c == ' ';
        }
        return out.toString();
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int darken(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.6f);
        int gr = (int) (((color >> 8) & 0xFF) * 0.6f);
        int b = (int) ((color & 0xFF) * 0.6f);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    /** Red (low) -> yellow -> green (high), by absolute stat value. */
    private static int statColor(int value) {
        float t = Math.min(1f, value / 200f);
        int r = (int) (t < 0.5f ? 230 : 230 * (1 - (t - 0.5f) * 2));
        int gr = (int) (t < 0.5f ? 120 + 200 * (t * 2) : 200);
        r = Math.max(40, Math.min(230, r));
        gr = Math.max(60, Math.min(220, gr));
        return 0xFF000000 | (r << 16) | (gr << 8) | 0x40;
    }

    private static final Map<String, Integer> TYPE_COLORS = new HashMap<>();

    static {
        TYPE_COLORS.put("normal", 0xFFA8A878);
        TYPE_COLORS.put("fire", 0xFFF08030);
        TYPE_COLORS.put("water", 0xFF6890F0);
        TYPE_COLORS.put("grass", 0xFF78C850);
        TYPE_COLORS.put("electric", 0xFFF8D030);
        TYPE_COLORS.put("ice", 0xFF98D8D8);
        TYPE_COLORS.put("fighting", 0xFFC03028);
        TYPE_COLORS.put("poison", 0xFFA040A0);
        TYPE_COLORS.put("ground", 0xFFE0C068);
        TYPE_COLORS.put("flying", 0xFFA890F0);
        TYPE_COLORS.put("psychic", 0xFFF85888);
        TYPE_COLORS.put("bug", 0xFFA8B820);
        TYPE_COLORS.put("rock", 0xFFB8A038);
        TYPE_COLORS.put("ghost", 0xFF705898);
        TYPE_COLORS.put("dragon", 0xFF7038F8);
        TYPE_COLORS.put("dark", 0xFF705848);
        TYPE_COLORS.put("steel", 0xFFB8B8D0);
        TYPE_COLORS.put("fairy", 0xFFEE99AC);
    }

    private static int typeColor(String typeName) {
        return TYPE_COLORS.getOrDefault(typeName == null ? "" : typeName.toLowerCase(Locale.ROOT), 0xFF8090A0);
    }
}
