package com.cobblemonmousemenu.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: "tell me the team of the trainer NPC with this network entity id".
 *
 * The entity id is known on both sides (every spawned entity has one), so the client can
 * name the trainer it's pointing at and the server can resolve the actual entity from it.
 */
public record RequestTrainerTeamPayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestTrainerTeamPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("cobblemon-mousemenu", "request_trainer_team"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTrainerTeamPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RequestTrainerTeamPayload::entityId,
                    RequestTrainerTeamPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
