package net.moriis.moriisexpansions.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.moriis.moriisexpansions.MoriisExpansions;

public record OrganNotePayload(BlockPos pos, int action, int voice, int note, int velocity, long timeMicros, int instrument)
        implements CustomPacketPayload {
    public static final int START = 0;
    public static final int STOP = 1;
    public static final int ALL_OFF = 2;
    public static final int FINISHED = 3;

    public OrganNotePayload(BlockPos pos, int action, int voice, int note, int velocity, long timeMicros) {
        this(pos, action, voice, note, velocity, timeMicros, 0);
    }

    public static final Type<OrganNotePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoriisExpansions.MOD_ID, "organ_note"));

    public static final StreamCodec<FriendlyByteBuf, OrganNotePayload> STREAM_CODEC =
            StreamCodec.of((buf, data) -> {
                buf.writeBlockPos(data.pos());
                buf.writeVarInt(data.action());
                buf.writeVarInt(data.voice());
                buf.writeVarInt(data.note());
                buf.writeVarInt(data.velocity());
                buf.writeLong(data.timeMicros());
                buf.writeVarInt(data.instrument());
            }, buf -> new OrganNotePayload(
                    buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt()));

    @Override
    public Type<OrganNotePayload> type() {
        return TYPE;
    }
}
