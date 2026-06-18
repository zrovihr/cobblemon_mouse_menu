package com.cobblemonmousemenu.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> Client: the resolved team for the Cobblemon NPC the client asked about.
 *
 * <p>The server flattens each {@code Pokemon} into a client-safe {@link TeamEntry} (name, level,
 * moves ids, etc.) so the client doesn't need the full Pokemon and both trainer sources share one
 * card model.</p>
 */
public record TrainerTeamPayload(int entityId, String battleFormat, List<TeamEntry> team)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TrainerTeamPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("cobblemon-mousemenu", "trainer_team"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<TeamEntry>> TEAM_CODEC =
            TeamEntry.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, TrainerTeamPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TrainerTeamPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, TrainerTeamPayload::battleFormat,
                    TEAM_CODEC, TrainerTeamPayload::team,
                    TrainerTeamPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
