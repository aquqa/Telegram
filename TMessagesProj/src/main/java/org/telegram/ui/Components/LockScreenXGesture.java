package org.telegram.ui.Components;

/** Recognizes one X as two crossing diagonal strokes, in either order and direction. */
final class LockScreenXGesture {
    private static final long MAX_STROKE_MS = 2500;
    private static final long MAX_STROKE_GAP_MS = 2000;
    private static final long MAX_X_GAP_MS = 4500;

    private float startX, startY, endX, endY, length;
    private long startedAt;
    private boolean drawing;
    private Stroke first;
    private int consecutive;
    private long lastXAt;

    void begin(float x, float y, long time) {
        if ((first != null && time - first.endedAt > MAX_STROKE_GAP_MS)
                || (consecutive != 0 && time - lastXAt > MAX_X_GAP_MS)) {
            reset();
        }
        startX = endX = x;
        startY = endY = y;
        length = 0;
        startedAt = time;
        drawing = true;
    }

    void move(float x, float y) {
        if (!drawing) return;
        length += (float) Math.hypot(x - endX, y - endY);
        endX = x;
        endY = y;
    }

    /** Returns the X streak only when this stroke completes an X. */
    int end(float x, float y, long time, float minimumSpan) {
        if (!drawing) return 0;
        move(x, y);
        drawing = false;
        float dx = x - startX;
        float dy = y - startY;
        float sx = Math.abs(dx);
        float sy = Math.abs(dy);
        if (time - startedAt > MAX_STROKE_MS || Math.min(sx, sy) < minimumSpan
                || Math.max(sx, sy) > 2.2f * Math.min(sx, sy)
                || length > 1.26f * Math.hypot(dx, dy)) {
            reset();
            return 0;
        }

        Stroke stroke = new Stroke(startX, startY, dx, dy, time);
        if (first != null && first.crosses(stroke)) {
            first = null;
            lastXAt = time;
            return ++consecutive;
        }
        if (first != null) consecutive = 0;
        first = stroke;
        return 0;
    }

    void reset() {
        drawing = false;
        first = null;
        consecutive = 0;
    }

    private static final class Stroke {
        final float x, y, dx, dy;
        final long endedAt;

        Stroke(float x, float y, float dx, float dy, long endedAt) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.endedAt = endedAt;
        }

        boolean crosses(Stroke other) {
            if ((dx * dy > 0) == (other.dx * other.dy > 0)) return false;
            float determinant = cross(dx, dy, other.dx, other.dy);
            float ax = other.x - x;
            float ay = other.y - y;
            float firstPosition = cross(ax, ay, other.dx, other.dy) / determinant;
            float secondPosition = cross(ax, ay, dx, dy) / determinant;
            return firstPosition >= .18f && firstPosition <= .82f
                    && secondPosition >= .18f && secondPosition <= .82f;
        }

        private static float cross(float ax, float ay, float bx, float by) {
            return ax * by - ay * bx;
        }
    }
}
