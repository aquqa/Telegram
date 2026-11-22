package org.telegram.ui.Components;

/**
 * One mechanical reel. Its motion is a pure function of time, so a dropped frame
 * never changes where or when it lands.
 *
 * <p>Positions are measured in symbols along an endless strip. Growing positions
 * move the strip down; the symbol at whole position {@code k} sits on the payline
 * when the reel rests at {@code k}.
 */
final class RetroSlotReel {
    /** Top speed in symbols per millisecond. */
    static final float SPEED = .016f;
    /** How far the reel winds backwards before it spins up. */
    private static final float KICK = .14f;
    private static final long KICK_MS = 90;
    private static final long ACCEL_MS = 260;
    /** Distance travelled while braking; braking ends just past the stop. */
    private static final float BRAKE_DISTANCE = 2.6f;
    private static final float OVERSHOOT = .13f;
    // Braking starts at top speed: the initial slope of the curve below is 1.5 * distance.
    static final long BRAKE_MS = Math.round(1.5f * BRAKE_DISTANCE / SPEED);
    private static final long SETTLE_MS = 190;

    private final int[] strip;
    private float rest;
    private long startAt = Long.MAX_VALUE;
    private long brakeAt = Long.MAX_VALUE;
    private float brakeFrom;
    private int target;

    RetroSlotReel(int[] strip, int index) {
        this.strip = strip;
        rest = index;
    }

    /** Puts the reel at rest with the strip entry {@code index} on the payline. */
    void reset(int index) {
        rest = wrap(index);
        startAt = brakeAt = Long.MAX_VALUE;
    }

    /** Starts spinning at {@code time} from wherever the last spin stopped. */
    void start(long time) {
        finish();
        rest = wrap(Math.round(rest));
        startAt = time;
    }

    /**
     * Plans the stop so that {@code symbol} lands on the payline, braking no earlier than
     * {@code notBefore}. Returns the time the reel hits its stop.
     */
    long stop(long notBefore, int symbol) {
        long fullSpeedAt = startAt + KICK_MS + ACCEL_MS;
        float earliest = free(Math.max(notBefore, fullSpeedAt));
        int stop = (int) Math.ceil(earliest + BRAKE_DISTANCE - OVERSHOOT);
        for (int i = 0; i < strip.length && symbolAt(stop) != symbol; ++i) stop++;
        target = stop;
        brakeFrom = target + OVERSHOOT - BRAKE_DISTANCE;
        brakeAt = fullSpeedAt + (long) Math.ceil((brakeFrom - free(fullSpeedAt)) / SPEED);
        // Rounding the brake time up moves the stop by under a fiftieth of a symbol.
        brakeFrom = free(brakeAt);
        return landsAt();
    }

    /** Jumps to the planned stop, or stays put when idle. */
    void finish() {
        if (brakeAt != Long.MAX_VALUE) rest = wrap(target);
        startAt = brakeAt = Long.MAX_VALUE;
    }

    long landsAt() {
        return brakeAt == Long.MAX_VALUE ? Long.MAX_VALUE : brakeAt + BRAKE_MS;
    }

    long settlesAt() {
        return brakeAt == Long.MAX_VALUE ? Long.MAX_VALUE : brakeAt + BRAKE_MS + SETTLE_MS;
    }

    int symbolAt(int position) {
        return strip[wrap(position)];
    }

    float position(long time) {
        if (time < brakeAt) return free(time);
        long t = time - brakeAt;
        float end = target + OVERSHOOT;
        if (t < BRAKE_MS) {
            // Cubic Hermite from top speed to a standstill just past the stop.
            float u = t / (float) BRAKE_MS;
            float u2 = u * u;
            float u3 = u2 * u;
            return brakeFrom + (u3 - 2 * u2 + u) * SPEED * BRAKE_MS + (3 * u2 - 2 * u3) * (end - brakeFrom);
        }
        t -= BRAKE_MS;
        if (t < SETTLE_MS) {
            // The detent pulls the reel back and lets it wobble once.
            float s = t / (float) SETTLE_MS;
            return target + OVERSHOOT * (float) (Math.exp(-3.2 * s) * Math.cos(2.5 * Math.PI * s));
        }
        return target;
    }

    /** Signed speed in symbols per millisecond. */
    float speed(long time) {
        return (position(time) - position(time - 12)) / 12f;
    }

    private int wrap(int position) {
        int index = position % strip.length;
        return index < 0 ? index + strip.length : index;
    }

    private float free(long time) {
        long t = time - startAt;
        if (t <= 0) return rest;
        if (t < KICK_MS) return rest - KICK * (float) Math.sin(Math.PI / 2 * t / KICK_MS);
        t -= KICK_MS;
        float base = rest - KICK;
        if (t < ACCEL_MS) return base + .5f * SPEED / ACCEL_MS * t * t;
        return base + SPEED * (ACCEL_MS / 2f + (t - ACCEL_MS));
    }
}
