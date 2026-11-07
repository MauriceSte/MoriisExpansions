package net.moriis.moriisexpansions.network;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.moriis.moriisexpansions.block.custom.PianoBlock;
import net.moriis.moriisexpansions.sound.ModPianoSounds;
import net.moriis.moriisexpansions.MoriisExpansions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = MoriisExpansions.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PianoNetworking {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Only accessed by the main server thread. Weak keys avoid retaining disconnected players.
    private static final Map<ServerPlayer, Rate> RATES = new WeakHashMap<>();

    private PianoNetworking() {}

    /** Compatibility with the previous package: registration is now automatic. */
    @Deprecated
    public static void register(IEventBus bus) {
        // Do not add another listener: @EventBusSubscriber owns registration.
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // NeoForge 1.21.1: main-thread handlers are the default.
        event.registrar("1").playToServer(
                PianoNotePayload.TYPE, PianoNotePayload.STREAM_CODEC,
                PianoNetworking::handle);
        LOGGER.info("Registered piano serverbound payload: {}", PianoNotePayload.TYPE.id());
    }

    private static void handle(PianoNotePayload data, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.isAlive() || player.isSpectator()) return;
        if (data.note() < 24 || data.note() > 108) return;
        if (data.velocity() < 1 || data.velocity() > 127) return;

        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(data.pos())) return;
        if (player.distanceToSqr(Vec3.atCenterOf(data.pos())) > 64.0) return;
        if (!(level.getBlockState(data.pos()).getBlock() instanceof PianoBlock)) return;

        long tick = level.getGameTime();
        Rate rate = RATES.computeIfAbsent(player, ignored -> new Rate());
        if (rate.tick != tick) {
            rate.tick = tick;
            rate.notes = 0;
        }
        // Allow chords and fast playing; bound work from invalid/flooding clients.
        if (++rate.notes > 32) return;

        int octave = data.note() / 12 - 1;
        int semitone = data.note() % 12;
        float pitch = (float) Math.pow(2.0, semitone / 12.0);
        float volume = data.velocity() / 127.0F;

        // null means that the playing player also receives the sound.
        // Position is the piano, not the player's position. No extra local playback.
        level.playSound(null,
                data.pos().getX() + 0.5,
                data.pos().getY() + 0.5,
                data.pos().getZ() + 0.5,
                ModPianoSounds.NOTES.get(octave - 1),
                SoundSource.RECORDS, volume, pitch);
    }

    private static final class Rate {
        private long tick = Long.MIN_VALUE;
        private int notes;
    }
}
