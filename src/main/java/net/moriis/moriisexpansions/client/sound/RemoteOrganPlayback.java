package net.moriis.moriisexpansions.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.moriis.moriisexpansions.network.OrganNotePayload;
import net.moriis.moriisexpansions.network.OrganSoundPayload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Render-thread playout buffer for listeners, independent from local prediction. */
public final class RemoteOrganPlayback {
    // Covers normal server-tick batching and moderate packet jitter; increase for unstable links.
    public static final long LISTENER_BUFFER_MILLIS = 150;
    private static final long BUFFER_NANOS = LISTENER_BUFFER_MILLIS * 1_000_000L;
    private static final Map<UUID, Clock> CLOCKS = new HashMap<>();
    private static final Map<Key, Voice> VOICES = new HashMap<>();
    private static ClientLevel world;

    private record Key(UUID player, int voice) {}
    private static final class Clock {
        final long sourceMicros;
        final long localNanos;
        long lastArrivalNanos;
        Clock(long sourceMicros, long now) {
            this.sourceMicros = sourceMicros;
            this.localNanos = now;
            this.lastArrivalNanos = now;
        }
    }
    private static final class Voice {
        final OrganSoundPayload attack;
        final long deadline;
        boolean started;
        long actualStart;
        Long sourceRelease;
        Voice(OrganSoundPayload attack, long deadline) {
            this.attack = attack;
            this.deadline = deadline;
        }
    }
    private RemoteOrganPlayback() {}

    public static void receive(OrganSoundPayload data) {
        if (!checkWorld()) return;
        Key key = new Key(data.player(), data.voice());
        if (data.action() == OrganNotePayload.STOP) {
            if (data.timeMicros() < 0) {
                // Block removal, logout, timeout or rejection: cancel queued attacks too.
                VOICES.remove(key);
                ClientOrganAudio.stop(data.player(), data.voice());
            } else {
                Voice voice = VOICES.get(key);
                if (voice != null) voice.sourceRelease = data.timeMicros();
            }
            pump();
            return;
        }
        if (data.action() != OrganNotePayload.START || data.timeMicros() < 0) return;
        long now = System.nanoTime();
        Clock clock = CLOCKS.computeIfAbsent(data.player(), ignored -> new Clock(data.timeMicros(), now));
        clock.lastArrivalNanos = now;
        long deadline = RemoteNoteTiming.attackDeadline(clock.sourceMicros,
                clock.localNanos, data.timeMicros(), BUFFER_NANOS);
        VOICES.put(key, new Voice(data, deadline));
        pump();
    }

    public static void pump() {
        if (!checkWorld()) return;
        long now = System.nanoTime();
        Iterator<Map.Entry<Key, Voice>> iterator = VOICES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Key key = entry.getKey();
            Voice voice = entry.getValue();
            if (!voice.started && now >= voice.deadline) {
                // Record the actual playback request, including any late packet/frame delay.
                voice.started = true;
                voice.actualStart = now;
                var attack = voice.attack;
                ClientOrganAudio.start(key.player(), key.voice(), attack.pos(), attack.note(), attack.velocity(), attack.instrument());
            }
            if (!voice.started) continue;
            if (voice.sourceRelease != null && now >= RemoteNoteTiming.releaseDeadline(
                    voice.actualStart, voice.attack.timeMicros(), voice.sourceRelease)) {
                ClientOrganAudio.stop(key.player(), key.voice());
                iterator.remove();
            } else if (now - voice.actualStart > 1_000_000_000L
                    && !ClientOrganAudio.isAlive(key.player(), key.voice())) {
                // The recording finished naturally; a much later pedal-up need not resurrect it.
                iterator.remove();
            }
        }
        CLOCKS.entrySet().removeIf(entry -> now - entry.getValue().lastArrivalNanos > 5_000_000_000L
                && VOICES.keySet().stream().noneMatch(key -> key.player().equals(entry.getKey())));
    }

    private static boolean checkWorld() {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            clear();
            world = current;
        }
        return current != null;
    }

    public static void clear() {
        for (Key key : VOICES.keySet()) ClientOrganAudio.stop(key.player(), key.voice());
        VOICES.clear();
        CLOCKS.clear();
        world = null;
    }
}
