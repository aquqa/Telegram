package org.telegram.ui.Components;

/**
 * Spring-loaded pull handle. The angle is 0 when the handle stands upright and
 * {@link #MAX} when it is pulled all the way down.
 */
final class RetroSlotLever {
    static final float MAX = (float) (Math.PI * .8);
    /** How far a handle that is locked by a running spin still gives. */
    static final float LOCKED_MAX = .16f;
    private static final int NOTCHES = 7;
    private static final float STIFFNESS = 230f;
    private static final float DAMPING = 16f;
    /** The handle swings back past upright until it hits this stop pin. */
    private static final float STOP_PIN = -.22f;
    private static final float PIN_BOUNCE = .35f;
    /** How long {@link #autoPull} takes to reach the bottom. */
    static final long AUTO_PULL_MS = 170;
    private static final long AUTO_HOLD_MS = 60;

    private float angle;
    private float velocity;
    private boolean held;
    private long autoAt = -1;
    private int notch;

    void reset() {
        angle = velocity = 0;
        held = false;
        autoAt = -1;
        notch = 0;
    }

    /** Moves the handle with the hand. Returns true when it clicks past a notch on the way down. */
    boolean hold(float target, boolean locked) {
        held = true;
        autoAt = -1;
        velocity = 0;
        angle = Math.max(0, Math.min(locked ? LOCKED_MAX : MAX, target));
        return updateNotch();
    }

    void release() {
        held = false;
    }

    /** Pulls the handle down by itself and lets it spring back, as the SPIN button does. */
    void autoPull(long now) {
        held = false;
        autoAt = now;
    }

    /** Advances the spring. Returns true while the handle is still moving. */
    boolean update(long now, float dt) {
        if (autoAt >= 0) {
            long t = now - autoAt;
            if (t < AUTO_PULL_MS) {
                float u = t / (float) AUTO_PULL_MS;
                angle = Math.max(angle, MAX * u * u);
                updateNotch();
                return true;
            }
            if (t < AUTO_PULL_MS + AUTO_HOLD_MS) {
                angle = MAX;
                updateNotch();
                return true;
            }
            autoAt = -1;
            velocity = 0;
        }
        if (held) return false;
        for (float left = dt; left > 0; left -= .004f) {
            float step = Math.min(.004f, left);
            velocity += (-STIFFNESS * angle - DAMPING * velocity) * step;
            angle += velocity * step;
            if (angle < STOP_PIN) {
                angle = STOP_PIN;
                velocity = -velocity * PIN_BOUNCE;
            }
        }
        updateNotch();
        if (Math.abs(angle) < .002f && Math.abs(velocity) < .03f) {
            angle = velocity = 0;
            return false;
        }
        return true;
    }

    float angle() {
        return angle;
    }

    boolean isHeld() {
        return held;
    }

    boolean isAtRest() {
        return !held && autoAt < 0 && angle == 0 && velocity == 0;
    }

    private boolean updateNotch() {
        int current = (int) (Math.max(0, angle) / (MAX / NOTCHES));
        boolean clicked = current > notch;
        notch = current;
        return clicked;
    }
}
