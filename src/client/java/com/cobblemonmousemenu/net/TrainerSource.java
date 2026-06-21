package com.cobblemonmousemenu.net;

import net.minecraft.world.entity.Entity;

/**
 * A way to recognise a kind of "trainer" entity and reveal its team. One implementation per trainer
 * system (Cobblemon NPCs, RCT TrainerMobs, ...).
 *
 * <p>IMPORTANT for soft dependencies: an implementation that references a third-party mod's classes
 * (e.g. {@link RctTrainerSource}) must only ever be instantiated when that mod is loaded. The JVM
 * links referenced classes lazily, so keeping the references inside such a guarded implementation
 * means the rest of the mod runs fine when the mod is absent.</p>
 */
public interface TrainerSource {

    /** True if this entity is a trainer this source understands. */
    boolean isTrainer(Entity entity);

    /** True if this source can produce the team right now (e.g. server has the mod / data is synced). */
    boolean canShow(Entity entity);

    /**
     * Reveal the team. May be synchronous (RCT: reads client data and calls
     * {@link TrainerTeamClient#setCard}) or asynchronous (Cobblemon: sends a packet whose reply
     * calls setCard later).
     */
    void show(Entity entity);

    /** The hover prompt shown over a pickable entity of this source. */
    default String hoverLabel() {
        return "Click to view team";
    }

    /**
     * Right-click action over a hovered entity of this source. Returns true if it was handled.
     * Default: nothing (most trainer sources only support left-click to view the team).
     */
    default boolean onRightClick(Entity entity) {
        return false;
    }
}
