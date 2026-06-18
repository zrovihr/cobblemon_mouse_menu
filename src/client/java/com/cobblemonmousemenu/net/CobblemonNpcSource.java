package com.cobblemonmousemenu.net;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;

/**
 * Cobblemon native NPCs. Their party is server-only, so we ask the server with a packet; the reply
 * ({@link TrainerTeamPayload}) is handled in {@link TrainerTeamClient} and converted to the card.
 */
public final class CobblemonNpcSource implements TrainerSource {

    @Override
    public boolean isTrainer(Entity entity) {
        return entity instanceof NPCEntity;
    }

    @Override
    public boolean canShow(Entity entity) {
        // Only possible if the server also has this mod (it advertises the request channel).
        return ClientPlayNetworking.canSend(RequestTrainerTeamPayload.TYPE);
    }

    @Override
    public void show(Entity entity) {
        if (!canShow(entity)) {
            return;
        }
        ClientPlayNetworking.send(new RequestTrainerTeamPayload(entity.getId()));
    }
}
