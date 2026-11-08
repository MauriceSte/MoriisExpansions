package net.moriis.moriisexpansions.network;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.moriis.moriisexpansions.MoriisExpansions;
import net.moriis.moriisexpansions.block.custom.PianoBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = MoriisExpansions.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PianoNetworking {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private PianoNetworking() {}

    /** Automatic registration; retained for old constructor calls. */
    @Deprecated public static void register(IEventBus bus) {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Wire format changed: old clients/servers must not decode these as v1 packets.
        var registrar = event.registrar("5");
        registrar.playToServer(PianoNotePayload.TYPE, PianoNotePayload.STREAM_CODEC,
                PianoNetworking::handle);
        registrar.playToClient(PianoSoundPayload.TYPE, PianoSoundPayload.STREAM_CODEC,
                (data, context) -> ClientAccess.handle(data));
        LogUtils.getLogger().info("Registered piano START/STOP payloads, protocol 5");
    }

    private static void handle(PianoNotePayload data, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        UUID owner = player.getUUID();
        State state = STATES.get(owner);
        // Natural local completion is bookkeeping, not a musical release.
        // Listener samples finish independently on their own audio engine.
        if (data.action() == PianoNotePayload.FINISHED) {
            if (state != null) state.voices.remove(data.voice());
            return;
        }
        // Release messages remain valid after walking away: never strand a listener's voice.
        if (data.action() == PianoNotePayload.STOP) {
            if (state != null && data.timeMicros() >= 0) {
                stop(player.getServer(), owner, state, data.voice(), false, data.timeMicros());
            }
            return;
        }
        if (data.action() == PianoNotePayload.ALL_OFF) {
            if (state != null) stopAll(player.getServer(), owner, state);
            return;
        }
        if (data.action() != PianoNotePayload.START || data.voice() <= 0) return;
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || data.note() < 24 || data.note() > 108
                || data.velocity() < 1 || data.velocity() > 127
                || data.timeMicros() < 0 || data.timeMicros() > Long.MAX_VALUE - 60_000_000L
                || !validPiano(player, level, data.pos())) {
            reject(player, data);
            return;
        }
        if (state != null && state.level != level) {
            stopAll(player.getServer(), owner, state);
            STATES.remove(owner);
            state = null;
        }
        if (state == null) {
            state = new State(level);
            STATES.put(owner, state);
        }
        long tick = level.getGameTime();
        if (state.tick != tick) { state.tick = tick; state.starts = 0; }
        if (++state.starts > 128 || state.voices.containsKey(data.voice())) {
            reject(player, data);
            return;
        }
        if (state.voices.size() >= 256) {
            stop(player.getServer(), owner, state, state.voices.keySet().iterator().next(), true);
        }
        Set<UUID> listeners = new HashSet<>();
        PianoSoundPayload sound = new PianoSoundPayload(owner, data.pos(),
                PianoNotePayload.START, data.voice(), data.note(), data.velocity(), data.timeMicros());
        for (ServerPlayer listener : level.players()) {
            // Performer already plays locally. Do not echo it back.
            if (listener == player || listener.distanceToSqr(Vec3.atCenterOf(data.pos())) > 4096.0) continue;
            if (listener.connection.hasChannel(PianoSoundPayload.TYPE)) {
                PacketDistributor.sendToPlayer(listener, sound);
                listeners.add(listener.getUUID());
            }
        }
        state.voices.put(data.voice(), new Voice(data.pos(), tick, data.timeMicros(), Set.copyOf(listeners)));
    }

    private static boolean validPiano(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0
                && PianoBlock.isPlayableAnchor(level, pos);
    }

    private static void reject(ServerPlayer player, PianoNotePayload data) {
        if (player.connection.hasChannel(PianoSoundPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new PianoSoundPayload(player.getUUID(),
                    data.pos(), PianoNotePayload.STOP, data.voice(), 0, 0, -1));
        }
    }

    private static void stop(MinecraftServer server, UUID owner, State state,
            int id, boolean includeOwner) {
        stop(server, owner, state, id, includeOwner, -1);
    }

    private static void stop(MinecraftServer server, UUID owner, State state,
            int id, boolean includeOwner, long sourceStopMicros) {
        Voice voice = state.voices.remove(id);
        if (voice == null || server == null) return;
        // Preserve performer timing; cleanup/rejection uses -1 for immediate cancellation.
        long timeMicros = sourceStopMicros < 0 ? -1 : voice.sourceStartMicros()
                + Math.max(0L, Math.min(60_000_000L, sourceStopMicros - voice.sourceStartMicros()));
        PianoSoundPayload packet = new PianoSoundPayload(owner, voice.pos(),
                PianoNotePayload.STOP, id, 0, 0, timeMicros);
        for (UUID uuid : voice.listeners()) {
            // Send to original listeners even if they moved outside the 64-block radius.
            send(server.getPlayerList().getPlayer(uuid), packet);
        }
        if (includeOwner) send(server.getPlayerList().getPlayer(owner), packet);
    }

    private static void send(ServerPlayer player, PianoSoundPayload packet) {
        if (player != null && player.connection.hasChannel(PianoSoundPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    private static void stopAll(MinecraftServer server, UUID owner, State state) {
        for (int id : new ArrayList<>(state.voices.keySet())) stop(server, owner, state, id, true);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (UUID uuid : new ArrayList<>(STATES.keySet())) {
            State state = STATES.get(uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive() || player.isSpectator()
                    || player.serverLevel() != state.level) {
                stopAll(server, uuid, state);
                STATES.remove(uuid);
                continue;
            }
            for (int id : new ArrayList<>(state.voices.keySet())) {
                Voice voice = state.voices.get(id);
                // One-minute safety timeout; samples are one-shots, not infinite loops.
                if (!validPiano(player, state.level, voice.pos())
                        || state.level.getGameTime() - voice.started() > 1200) {
                    stop(server, uuid, state, id, true);
                }
            }
            if (state.voices.isEmpty()) STATES.remove(uuid);
        }
    }

    public static void clear() { STATES.clear(); }

    private record Voice(BlockPos pos, long started, long sourceStartMicros, Set<UUID> listeners) {}
    private static final class State {
        final ServerLevel level;
        final Map<Integer, Voice> voices = new LinkedHashMap<>();
        long tick = Long.MIN_VALUE;
        int starts;
        State(ServerLevel level) { this.level = level; }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientAccess {
        static void handle(PianoSoundPayload data) {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client.player != null && client.player.getUUID().equals(data.player())) {
                // The performer is predicted locally. Only server cancellation comes back.
                if (data.action() == PianoNotePayload.STOP) {
                    net.moriis.moriisexpansions.client.sound.ClientPianoAudio.stop(data.player(), data.voice());
                }
                return;
            }
            net.moriis.moriisexpansions.client.sound.RemotePianoPlayback.receive(data);
        }
    }
}
