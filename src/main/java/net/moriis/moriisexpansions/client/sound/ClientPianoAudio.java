package net.moriis.moriisexpansions.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.moriis.moriisexpansions.sound.ModPianoSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** All methods run on the Minecraft client thread; never use a global stop by sound ID. */
public final class ClientPianoAudio {
    private record Key(UUID player, int voice) {}
    private record Playing(PianoVoice sound, long started) {}
    private static final Map<Key, Playing> PLAYING = new HashMap<>();
    private static int nextId;

    private ClientPianoAudio() {}

    public static int nextVoice() {
        if (++nextId <= 0) nextId = 1;
        return nextId;
    }

    public static void start(UUID player, int voice, BlockPos pos, int note, int velocity) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || note < 24 || note > 108 || voice <= 0) return;
        stop(player, voice);
        float pitch = (float) Math.pow(2.0, (note % 12) / 12.0);
        PianoVoice sound = new PianoVoice(note, velocity, pitch, pos);
        PLAYING.put(new Key(player, voice), new Playing(sound, System.nanoTime()));
        client.getSoundManager().play(sound);
    }

    public static void stop(UUID player, int voice) {
        Playing playing = PLAYING.remove(new Key(player, voice));
        if (playing != null) {
            playing.sound().release();
            Minecraft.getInstance().getSoundManager().stop(playing.sound());
        }
    }

    public static boolean isAlive(UUID player, int voice) {
        return PLAYING.containsKey(new Key(player, voice));
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) { clear(); return; }
        long now = System.nanoTime();
        // Allow sound-channel creation to finish before checking isActive.
        PLAYING.entrySet().removeIf(entry -> now - entry.getValue().started() > 1_000_000_000L
                && !client.getSoundManager().isActive(entry.getValue().sound()));
    }

    public static void clear() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (Playing playing : PLAYING.values()) {
            playing.sound().release();
            manager.stop(playing.sound());
        }
        PLAYING.clear();
    }

    private static final class PianoVoice extends AbstractTickableSoundInstance {
        PianoVoice(int note, int velocity, float notePitch, BlockPos pos) {
            super(ModPianoSounds.NOTES.get(note / 12 - 2), SoundSource.RECORDS, RandomSource.create());
            volume = Math.max(1, Math.min(127, velocity)) / 127.0F;
            pitch = notePitch;
            x = pos.getX() + 0.5;
            y = pos.getY() + 0.5;
            z = pos.getZ() + 0.5;
            looping = false;
            relative = false;
            attenuation = Attenuation.LINEAR;
        }

        void release() { stop(); }
        @Override public boolean canPlaySound() { return !isStopped(); }
        @Override public void tick() { }
    }
}
