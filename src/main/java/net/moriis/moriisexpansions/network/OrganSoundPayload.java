package net.moriis.moriisexpansions.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.moriis.moriisexpansions.MoriisExpansions;
import java.util.UUID;

public record OrganSoundPayload(UUID player, BlockPos pos, int action, int voice,
                                int note, int velocity, long timeMicros) implements CustomPacketPayload {
    public static final Type<OrganSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoriisExpansions.MOD_ID, "organ_sound"));
    public static final StreamCodec<FriendlyByteBuf, OrganSoundPayload> STREAM_CODEC =
            StreamCodec.of((buf, data) -> {
                buf.writeUUID(data.player());
                buf.writeBlockPos(data.pos());
                buf.writeVarInt(data.action());
                buf.writeVarInt(data.voice());
                buf.writeVarInt(data.note());
                buf.writeVarInt(data.velocity());
                buf.writeLong(data.timeMicros());
            }, buf -> new OrganSoundPayload(buf.readUUID(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong()));
    @Override public Type<OrganSoundPayload> type() { return TYPE; }
}
