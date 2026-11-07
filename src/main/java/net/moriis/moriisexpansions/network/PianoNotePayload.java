package net.moriis.moriisexpansions.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.moriis.moriisexpansions.MoriisExpansions;

public record PianoNotePayload(BlockPos pos, int note, int velocity)
        implements CustomPacketPayload {
    public static final Type<PianoNotePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoriisExpansions.MOD_ID, "piano_note"));

    public static final StreamCodec<FriendlyByteBuf, PianoNotePayload> STREAM_CODEC =
            StreamCodec.of((buf, data) -> {
                buf.writeBlockPos(data.pos());
                buf.writeVarInt(data.note());
                buf.writeVarInt(data.velocity());
            }, buf -> new PianoNotePayload(
                    buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<PianoNotePayload> type() {
        return TYPE;
    }
}
