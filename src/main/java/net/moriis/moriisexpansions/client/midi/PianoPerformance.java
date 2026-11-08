package net.moriis.moriisexpansions.client.midi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/** Client-main-thread note/pedal state, independent of Minecraft's sound classes. */
public final class PianoPerformance {
    public static final int MOUSE_CHANNEL = 16;

    public interface Output {
        void start(int voice, int note, int velocity);
        void stop(int voice);
        default void finished(int voice) { stop(voice); }
    }

    private final IntSupplier ids;
    private final Output output;
    private final Map<Integer, Voice> voices = new LinkedHashMap<>();
    private final boolean[] pedal = new boolean[16];
    private boolean letRing;

    public PianoPerformance(IntSupplier ids, Output output) {
        this.ids = ids;
        this.output = output;
    }

    public int noteOn(int channel, int rawNote, int transpose, int velocity) {
        int note = rawNote + transpose;
        if (channel < 0 || channel > MOUSE_CHANNEL || rawNote < 0 || rawNote > 127
                || note < 24 || note > 108 || velocity <= 0) return -1;
        if (voices.size() >= 256) stop(voices.keySet().iterator().next());
        int id = ids.getAsInt();
        voices.put(id, new Voice(channel, rawNote));
        output.start(id, note, Math.min(127, velocity));
        return id;
    }

    public void noteOff(int channel, int rawNote) {
        // Match the original input note, not the current transpose setting.
        for (var entry : voices.entrySet()) {
            Voice voice = entry.getValue();
            if (voice.channel == channel && voice.rawNote == rawNote && voice.down) {
                voice.down = false;
                if (!letRing && !pedalFor(channel)) stop(entry.getKey());
                return;
            }
        }
    }

    public void pedal(int channel, boolean down) {
        if (channel < 0 || channel >= 16) return;
        pedal[channel] = down;
        flushReleased();
    }

    public boolean pedalDown() {
        for (boolean value : pedal) if (value) return true;
        return false;
    }

    private boolean pedalFor(int channel) {
        // MIDI follows channel-specific CC64; mouse notes use any connected pedal.
        return channel == MOUSE_CHANNEL ? pedalDown() : pedal[channel];
    }

    public boolean letRing() {
        return letRing;
    }

    public void setLetRing(boolean enabled) {
        letRing = enabled;
        flushReleased();
    }

    private void flushReleased() {
        if (letRing) return;
        for (int id : new ArrayList<>(voices.keySet())) {
            Voice voice = voices.get(id);
            if (!voice.down && !pedalFor(voice.channel)) stop(id);
        }
    }

    public void allNotesOff(int channel) {
        for (Voice voice : voices.values()) if (voice.channel == channel) voice.down = false;
        flushReleased();
    }

    public void allSoundOff(int channel) {
        if (channel >= 0 && channel < 16) pedal[channel] = false;
        for (int id : new ArrayList<>(voices.keySet())) {
            if (voices.get(id).channel == channel) stop(id);
        }
        flushReleased();
    }

    public void disconnectMidi() {
        for (int channel = 0; channel < 16; channel++) allSoundOff(channel);
    }

    public boolean isHeld(int rawNote) {
        for (Voice voice : voices.values()) if (voice.rawNote == rawNote && voice.down) return true;
        return false;
    }

    public void discardFinished(IntPredicate stillAlive) {
        for (int id : new ArrayList<>(voices.keySet())) {
            Voice voice = voices.get(id);

            if (voice.down) {
                continue;
            }

            // Sustain weiterhin verwalten, selbst wenn das lokale
            // Sample bereits beendet ist. Zuhörer können noch spielen.
            if (letRing || pedalFor(voice.channel)) {
                continue;
            }

            if (!stillAlive.test(id)) {
                stop(id);
            }
        }
    }

    public void stopAll() {
        for (int id : new ArrayList<>(voices.keySet())) stop(id);
        java.util.Arrays.fill(pedal, false);
    }

    private void stop(int id) {
        if (voices.remove(id) != null) output.stop(id);
    }

    private static final class Voice {
        final int channel;
        final int rawNote;
        boolean down = true;
        Voice(int channel, int rawNote) {
            this.channel = channel;
            this.rawNote = rawNote;
        }
    }
}
