package com.cobblemonmousemenu.net;

import com.cobblemonmousemenu.CobblemonMouseMenu;
import com.cobblemonmousemenu.CobblemonMouseMenuClient;
import com.cobblemonmousemenu.client.gui.TrainerTeamScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the trainer-team feature.
 *
 * <p>Flow: while the mouse-menu cursor is free, we project nearby trainers to the screen each frame
 * and find the one under the cursor (free-cursor picking — vanilla's {@code pick} only works off the
 * crosshair). Hovering shows a "click to view team" prompt; clicking opens {@link TrainerTeamScreen}
 * with the team.</p>
 *
 * <p>Multiple trainer systems are supported via {@link TrainerSource}: Cobblemon NPCs (server packet)
 * and RCT TrainerMobs (client-side read). RCT support is added only when {@code rctmod} is loaded.</p>
 */
public final class TrainerTeamClient {

    private static final double PICK_RANGE = 24.0;

    // Flip to false once projection picking is confirmed in-game. Logs only on transitions / throttled.
    private static final boolean DEBUG = true;
    private static int lastLoggedHover = -1;
    private static int frameCounter = 0;

    private static final List<TrainerSource> SOURCES = new ArrayList<>();

    // --- Hover state, written each frame from the world-render pass, read from the HUD pass. ---
    private static volatile int hoveredEntityId = -1;
    private static volatile float hoverX;       // gui-space x of the trainer centre
    private static volatile float hoverTop;     // gui-space y of the trainer's head
    private static volatile boolean hoveredCanShow = false;
    private static TrainerSource hoveredSource = null;

    private TrainerTeamClient() {
    }

    public static void init() {
        // Build the source list. RCT first (client-side, cheap) then Cobblemon NPC (packet).
        // RctTrainerSource references rctmod classes, so only construct it when rctmod is present —
        // otherwise the class is never loaded and the JVM never tries to link those types.
        if (FabricLoader.getInstance().isModLoaded("rctmod")) {
            SOURCES.add(new RctTrainerSource());
            CobblemonMouseMenu.LOGGER.info("[TrainerTeam] RCT detected - RCT trainers enabled");
        }
        SOURCES.add(new CobblemonNpcSource());
        // The player's own sent-out Pokemon (click -> open its summary). Pure client-side.
        SOURCES.add(new OwnPokemonSource());

        // Cobblemon NPC path: the server's answer arrives here (on the network thread). Hop to the
        // main thread before opening a screen.
        ClientPlayNetworking.registerGlobalReceiver(TrainerTeamPayload.TYPE, (payload, context) -> {
            List<TeamEntry> team = payload.team();
            int entityId = payload.entityId();
            String format = payload.battleFormat();
            context.client().execute(() -> showTeam(entityId, entityName(entityId), format, team));
            if (DEBUG) {
                debugLog("RECV(cobblemon) npc=" + entityId + " size=" + team.size());
            }
        });

        // Free-cursor picking happens once entities are placed for the frame.
        WorldRenderEvents.AFTER_ENTITIES.register(TrainerTeamClient::pickHoveredTrainer);

        // Draw the hover prompt.
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> renderHud(graphics));
    }

    /** Open the rich team screen. Called by sources (sync, on click) or the packet receiver (async). */
    public static void showTeam(int entityId, Component name, String battleFormat, List<TeamEntry> team) {
        // Drop menu mode first so the opening click can't leak through to the new screen and so the
        // per-frame tick doesn't grab the cursor out from under it.
        CobblemonMouseMenuClient.closeMouseMenu();
        Minecraft.getInstance().setScreen(new TrainerTeamScreen(name, battleFormat, team));
    }

    /** Called by the mouse mixin on left-click while the menu is active, after the party-slot check. */
    public static boolean onClick() {
        Minecraft client = Minecraft.getInstance();
        if (hoveredEntityId < 0 || hoveredSource == null || client.level == null) {
            return false;
        }
        Entity entity = client.level.getEntity(hoveredEntityId);
        if (entity == null) {
            return false;
        }
        if (!hoveredSource.canShow(entity)) {
            // e.g. Cobblemon NPC but the server lacks this mod. Degrade gracefully.
            if (DEBUG) debugLog("CLICK trainer=" + hoveredEntityId + " but source cannot show (unavailable)");
            return true;
        }
        if (DEBUG) debugLog("CLICK trainer=" + hoveredEntityId + " -> show via " + hoveredSource.getClass().getSimpleName());
        hoveredSource.show(entity);
        return true;
    }

    private static TrainerSource sourceFor(Entity entity) {
        for (TrainerSource source : SOURCES) {
            if (source.isTrainer(entity)) {
                return source;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Picking
    // ---------------------------------------------------------------------------------------------

    private static void pickHoveredTrainer(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();

        // Only meaningful when the cursor is free.
        if (!CobblemonMouseMenuClient.isMouseMenuActive() || client.level == null || client.player == null) {
            hoveredEntityId = -1;
            hoveredSource = null;
            return;
        }

        int guiW = client.getWindow().getGuiScaledWidth();
        int guiH = client.getWindow().getGuiScaledHeight();
        double guiScale = (double) client.getWindow().getWidth() / guiW;
        float cursorX = (float) (client.mouseHandler.xpos() / guiScale);
        float cursorY = (float) (client.mouseHandler.ypos() / guiScale);

        Vec3 cam = context.camera().getPosition();
        Matrix4f modelView = context.positionMatrix();
        Matrix4f projection = context.projectionMatrix();

        AABB box = client.player.getBoundingBox().inflate(PICK_RANGE);
        List<Mob> mobs = client.level.getEntitiesOfClass(Mob.class, box);

        int bestId = -1;
        double bestDepth = Double.MAX_VALUE;
        float bestX = 0, bestTop = 0;
        Mob bestMob = null;
        TrainerSource bestSource = null;

        int trainerCount = 0;
        Mob firstTrainer = null;

        for (Mob mob : mobs) {
            TrainerSource source = sourceFor(mob);
            if (source == null) {
                continue;
            }
            trainerCount++;
            if (firstTrainer == null) {
                firstTrainer = mob;
            }

            float[] feet = project(mob.getX(), mob.getY(), mob.getZ(), cam, modelView, projection, guiW, guiH);
            float[] head = project(mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), cam, modelView, projection, guiW, guiH);
            if (feet == null || head == null) {
                continue; // behind the camera / off-screen
            }

            float top = Math.min(head[1], feet[1]);
            float bottom = Math.max(head[1], feet[1]);
            float centerX = (head[0] + feet[0]) / 2f;
            float screenH = bottom - top;
            float halfW = Math.max(6f, screenH * 0.20f);

            if (cursorX >= centerX - halfW && cursorX <= centerX + halfW
                    && cursorY >= top && cursorY <= bottom) {
                double depth = feet[2];
                if (depth < bestDepth) {
                    bestDepth = depth;
                    bestId = mob.getId();
                    bestX = centerX;
                    bestTop = top;
                    bestMob = mob;
                    bestSource = source;
                }
            }
        }

        hoveredEntityId = bestId;
        hoverX = bestX;
        hoverTop = bestTop;
        hoveredSource = bestSource;
        hoveredCanShow = bestMob != null && bestSource.canShow(bestMob);

        if (DEBUG) {
            frameCounter++;
            if (bestId != lastLoggedHover) {
                if (bestId >= 0) {
                    debugLog(String.format(
                            "HOVER acquired trainer=%d via=%s canShow=%b screen=(%.1f,%.1f) cursor=(%.1f,%.1f)",
                            bestId, bestSource.getClass().getSimpleName(), hoveredCanShow, bestX, bestTop, cursorX, cursorY));
                } else {
                    debugLog("HOVER lost (cursor=(" + (int) cursorX + "," + (int) cursorY + "))");
                }
                lastLoggedHover = bestId;
            }
            if (bestId < 0 && firstTrainer != null && frameCounter % 40 == 0) {
                Mob n = firstTrainer;
                float[] feet = project(n.getX(), n.getY(), n.getZ(), cam, modelView, projection, guiW, guiH);
                float[] head = project(n.getX(), n.getY() + n.getBbHeight(), n.getZ(), cam, modelView, projection, guiW, guiH);
                debugLog(String.format(
                        "MISS gui=%dx%d cursor=(%.1f,%.1f) trainers=%d nearest=%d feet=%s head=%s",
                        guiW, guiH, cursorX, cursorY, trainerCount, n.getId(),
                        feet == null ? "null(behind)" : String.format("(%.1f,%.1f,d=%.1f)", feet[0], feet[1], feet[2]),
                        head == null ? "null(behind)" : String.format("(%.1f,%.1f,d=%.1f)", head[0], head[1], head[2])));
            }
        }
    }

    /**
     * Projects a world point to gui-scaled screen coordinates.
     *
     * @return {@code [screenX, screenY, viewDepth]}, or {@code null} if behind the camera.
     */
    private static float[] project(double wx, double wy, double wz, Vec3 cam,
                                   Matrix4f modelView, Matrix4f projection, int guiW, int guiH) {
        Vector4f v = new Vector4f((float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z), 1f);
        v.mul(modelView);
        float depth = -v.z(); // distance in front of the camera
        v.mul(projection);
        if (v.w() <= 1.0e-4f) {
            return null;
        }
        float ndcX = v.x() / v.w();
        float ndcY = v.y() / v.w();
        float screenX = (ndcX * 0.5f + 0.5f) * guiW;
        float screenY = (0.5f - ndcY * 0.5f) * guiH;
        return new float[]{screenX, screenY, depth};
    }

    // ---------------------------------------------------------------------------------------------
    // HUD prompt
    // ---------------------------------------------------------------------------------------------

    private static void renderHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.screen != null) {
            return;
        }
        if (CobblemonMouseMenuClient.isMouseMenuActive() && hoveredEntityId >= 0) {
            String label = hoveredSource != null ? hoveredSource.hoverLabel() : "Click to view team";
            Component prompt = hoveredCanShow
                    ? Component.literal("▶ " + label)
                    : Component.literal("Trainer info unavailable on this server");
            int color = hoveredCanShow ? 0xFFFFFF55 : 0xFFFF7777;
            Font font = client.font;
            int w = font.width(prompt);
            int px = (int) hoverX - w / 2;
            int py = (int) hoverTop - 14;
            graphics.fill(px - 3, py - 2, px + w + 3, py + font.lineHeight + 1, 0xAA000000);
            graphics.drawString(font, prompt, px, py, color);
        }
    }

    private static Component entityName(int entityId) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            Entity e = client.level.getEntity(entityId);
            if (e != null) {
                return e.getName().copy();
            }
        }
        return Component.literal("Trainer");
    }

    // ---------------------------------------------------------------------------------------------
    // Debug log file
    // ---------------------------------------------------------------------------------------------

    private static Path debugLogPath = null;
    private static boolean debugLogFailed = false;

    /**
     * Appends one line to {@code <instance>/logs/cobblemon-mousemenu-debug.log} so picking can be
     * diagnosed without sifting through latest.log. Disabled in one shot if the file can't be written.
     */
    private static void debugLog(String message) {
        if (debugLogFailed) return;
        try {
            if (debugLogPath == null) {
                Path logs = Minecraft.getInstance().gameDirectory.toPath().resolve("logs");
                Files.createDirectories(logs);
                debugLogPath = logs.resolve("cobblemon-mousemenu-debug.log");
                Files.writeString(debugLogPath,
                        "=== CobblemonMouseMenu debug log (session start) ===" + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            long t = System.currentTimeMillis() % 100000;
            Files.writeString(debugLogPath, t + "  " + message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            debugLogFailed = true;
            CobblemonMouseMenu.LOGGER.warn("[TrainerTeam] could not write debug log: {}", e.toString());
        }
    }
}
