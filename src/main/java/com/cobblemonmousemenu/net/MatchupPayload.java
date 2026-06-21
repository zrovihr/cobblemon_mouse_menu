package com.cobblemonmousemenu.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> Client: the player's roster ranked as counters against the requested enemy team.
 *
 * <p>Already sorted best-lead-first by the server; the client just renders the list. {@code entityId}
 * echoes the request so the client can confirm it still matches the open screen.</p>
 */
public record MatchupPayload(int entityId, List<MatchupEntry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MatchupPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("cobblemon-mousemenu", "matchup"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<MatchupEntry>> ENTRIES_CODEC =
            MatchupEntry.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, MatchupPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MatchupPayload::entityId,
                    ENTRIES_CODEC, MatchupPayload::entries,
                    MatchupPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
