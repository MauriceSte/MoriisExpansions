package net.moriis.moriisexpansions.client.sound;

/** Pure timing math; does not compare clocks on different computers directly. */
public final class RemoteNoteTiming {
    private RemoteNoteTiming() {}

    public static long attackDeadline(long anchorSourceMicros, long anchorLocalNanos,
            long sourceMicros, long bufferNanos) {
        return anchorLocalNanos + (sourceMicros - anchorSourceMicros) * 1000L + bufferNanos;
    }

    public static long releaseDeadline(long actualLocalAttackNanos,
            long sourceAttackMicros, long sourceReleaseMicros) {
        long durationMicros = Math.max(0L, Math.min(60_000_000L,
                sourceReleaseMicros - sourceAttackMicros));
        return actualLocalAttackNanos + durationMicros * 1000L;
    }
}
