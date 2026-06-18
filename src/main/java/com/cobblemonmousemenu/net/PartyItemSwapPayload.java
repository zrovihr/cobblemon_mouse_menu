package com.cobblemonmousemenu.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: unified take/give held item for the party Pokemon in {@code slot}.
 *
 * <p>The server reads the player's main-hand stack: if it's non-empty it becomes the Pokemon's new
 * held item (consuming one) and the old held item is returned to the player; if the hand is empty
 * the held item is simply taken back. This is the classic "swap item in hand" behavior, unified.</p>
 */
public record PartyItemSwapPayload(int slot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PartyItemSwapPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("cobblemon-mousemenu", "party_item_swap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyItemSwapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PartyItemSwapPayload::slot,
                    PartyItemSwapPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
