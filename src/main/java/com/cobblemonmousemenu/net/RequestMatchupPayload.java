package com.cobblemonmousemenu.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client -> Server: "rank my own roster against this enemy team".
 *
 * <p>The client sends the enemy team it is already viewing (resolved from either the Cobblemon NPC
 * packet or RCT client data) back up to the server. The server then reads the player's own party AND
 * PC — which only exist server-side — runs the matchup, and replies with a {@link MatchupPayload}.
 * Sending the small (&le;6) enemy team up keeps the (potentially huge) PC server-side.</p>
 *
 * <p>{@code entityId} is echoed back in the reply so the client can match it to the open screen.
 * {@code maxLevel} filters the player's roster to mons at or below a level cap (e.g. an RCT/Cobbleverse
 * battle restriction); {@code 0} means no cap.</p>
 */
public record RequestMatchupPayload(int entityId, List<TeamEntry> enemyTeam, int maxLevel)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestMatchupPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("cobblemon-mousemenu", "request_matchup"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<TeamEntry>> TEAM_CODEC =
            TeamEntry.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMatchupPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RequestMatchupPayload::entityId,
                    TEAM_CODEC, RequestMatchupPayload::enemyTeam,
                    ByteBufCodecs.VAR_INT, RequestMatchupPayload::maxLevel,
                    RequestMatchupPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
