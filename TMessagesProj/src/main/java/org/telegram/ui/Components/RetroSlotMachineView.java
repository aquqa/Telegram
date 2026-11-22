package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Components.RetroSlotArt.*;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RetroSlotMachineSounds.Cue;

import java.util.Arrays;
import java.util.Random;

/**
 * Full-screen vintage slot machine shown while the local app lock is active. The cabinet
 * is drawn on a board of {@link RetroSlotArt#BOARD_WIDTH} units scaled to fit the screen.
 */
final class RetroSlotMachineView extends View {
    private static final int INITIAL_COINS = 20;
    private static final int MAX_COINS = 9999;

    private static final long FIRST_STOP_MS = 760;
    private static final long REEL_GAP_MS = 250;
    private static final long REEL_STAGGER_MS = 35;
    private static final long MIN_LAND_GAP_MS = 170;
    // An opening pair keeps the last reel spinning a little longer.
    private static final long TEASE_MS = 650;
    private static final long PAYOUT_DELAY_MS = 260;
    private static final long PAYOUT_TICK_MS = 75;
    private static final long WIN_SHOW_MS = 2600;
    private static final long JACKPOT_SHOW_MS = 4200;
    private static final long BLINK_MS = 200;
    private static final long HINT_MS = 2000;
    private static final long IDLE_STEP_MS = 200;
    private static final long SHOW_STEP_MS = 100;
    // The lock view releases its sounds as soon as the unlock starts.
    private static final long FINAL_CUE_DELAY_MS = 110;

    private static final int ROOM = 0xff0c0a10;
    private static final int GOLD = 0xffffd36b;
    private static final int CYAN = 0xff48e1ff;
    private static final int ROSE = 0xffff6b6b;

    // Every strip holds each symbol once, so every symbol stays equally likely.
    private static final int[][] STRIPS = {
            {CHERRY, BELL, LEMON, BAR, PLUM, SEVEN},
            {PLUM, CHERRY, SEVEN, BELL, LEMON, BAR},
            {LEMON, SEVEN, CHERRY, PLUM, BAR, BELL}
    };
    private static final int[] START_SYMBOLS = {CHERRY, BELL, SEVEN};
    private static final String[] DIGITS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    // Soft lights of the arcade around the cabinet: x, y, radius as fractions of the room.
    private static final float[][] ROOM_LIGHTS = {
            {.1f, .12f, .38f}, {.9f, .08f, .32f}, {.04f, .6f, .3f}, {.96f, .52f, .34f},
            {.18f, .96f, .32f}, {.86f, .94f, .28f}, {.5f, 0, .42f}
    };
    private static final int[] ROOM_COLORS = {0x40ff3fd0, 0x3048e1ff, 0x38ffb347, 0x2c2f7cf6, 0x2cff3b30, 0x30ffb347, 0x1cffe0a0};

    // A pull must stay close to vertical: an X stroke is at least 0.41 wide per unit of height.
    private static final float LEVER_MAX_SLANT = .35f;

    private static final float GRAVITY = 900;
    private static final int MAX_DROPS = 24;
    private static final int[] PILE_ROWS = {9, 7, 5, 3};
    private static final int PILE_CAP;
    private static final float[] SLOT_X;
    private static final float[] SLOT_Y;
    private static final int[] SLOT_ROW;

    static {
        // Coins pile into a mound: low and central slots fill first.
        int count = 0;
        for (int row : PILE_ROWS) count += row;
        float[][] slots = new float[count][];
        int k = 0;
        for (int row = 0; row < PILE_ROWS.length; ++row) {
            for (int j = 0; j < PILE_ROWS[row]; ++j) {
                float x = TRAY_CENTER + (j - (PILE_ROWS[row] - 1) / 2f) * COIN_WIDTH;
                slots[k++] = new float[] {row * 5 + Math.abs(x - TRAY_CENTER) * .5f, x, row};
            }
        }
        Arrays.sort(slots, (a, b) -> Float.compare(a[0], b[0]));
        PILE_CAP = count;
        SLOT_X = new float[count];
        SLOT_Y = new float[count];
        SLOT_ROW = new int[count];
        for (int i = 0; i < count; ++i) {
            SLOT_X[i] = slots[i][1];
            SLOT_ROW[i] = (int) slots[i][2];
            SLOT_Y[i] = TRAY_FLOOR - COIN_HEIGHT - 3 * SLOT_ROW[i];
        }
    }

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reelShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reelGlare = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drumShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shaftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Random random = new Random();
    private final LockScreenXGesture gesture = new LockScreenXGesture();
    private final RetroSlotMachineSounds sounds = new RetroSlotMachineSounds();
    private final RetroSlotReel[] reels = new RetroSlotReel[STRIPS.length];
    private final RetroSlotLever lever = new RetroSlotLever();
    private final Runnable unlock;
    private final Runnable unlockAfterCue = this::completeUnlock;
    private final Runnable frame = this::onFrame;
    private final int touchSlop;
    private final int[] result = new int[STRIPS.length];

    // Artwork, painted for the current scale.
    private float artScale;
    private Bitmap cabinet;
    private final Bitmap[] symbols = new Bitmap[SYMBOL_COUNT];
    private final Bitmap[][] bulbs = new Bitmap[BULB_COLORS.length][2];
    private Bitmap beaconOn, beaconOff, beaconGlow, knob, hub, coin;
    private Bitmap room;
    private int roomWidth, roomHeight;

    private boolean spinning;
    private boolean tease;
    private int landed;
    private long settleUntil;
    private int coins = INITIAL_COINS;
    private int lastWin;
    private int payoutLeft;
    private int winShown;
    private float creditsShown = INITIAL_COINS;
    private float paidShown;
    private long nextPayoutAt;
    private long winAt = -1;
    private int winMask;
    private boolean jackpot;
    private int message = R.string.WhisperSlotReady;
    private long messageAt;

    private int pile;
    private int drops;
    private final float[] dropX = new float[MAX_DROPS];
    private final float[] dropY = new float[MAX_DROPS];
    private final float[] dropVx = new float[MAX_DROPS];
    private final float[] dropVy = new float[MAX_DROPS];
    private final float[] dropFloor = new float[MAX_DROPS];

    private boolean leverGrabbed;
    private boolean leverLocked;
    private boolean leverEngaged;
    private float leverDownX, leverDownY, leverGrabOffset;

    private boolean active;
    private boolean unlockPending;
    private boolean motion = true;
    private boolean idleLights = true;
    private boolean frameScheduled;
    private long lastFrameAt;
    private float scale = 1, left, top;

    RetroSlotMachineView(Context context, Runnable unlock) {
        super(context);
        this.unlock = unlock;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        for (int i = 0; i < reels.length; ++i) {
            reels[i] = new RetroSlotReel(STRIPS[i], indexOf(STRIPS[i], START_SYMBOLS[i]));
        }
        textPaint.setTypeface(CONDENSED);
        textPaint.setTextAlign(Paint.Align.CENTER);
        // Curved drum: the strip darkens as it turns away at the top and bottom.
        reelShade.setShader(new LinearGradient(0, REEL_TOP, 0, REEL_BOTTOM,
                new int[] {0xb0000000, 0x30000000, 0, 0x14ffffff, 0, 0x38000000, 0xc0000000},
                new float[] {0, .2f, .38f, .44f, .55f, .8f, 1}, Shader.TileMode.CLAMP));
        reelGlare.setShader(new LinearGradient(REEL_LEFT, REEL_TOP, REEL_LEFT + 3 * REEL_STEP, REEL_BOTTOM,
                new int[] {0, 0x22ffffff, 0, 0, 0x14ffffff, 0},
                new float[] {.12f, .2f, .28f, .62f, .68f, .74f}, Shader.TileMode.CLAMP));
        float drumTop = METER_TOP + 11, drumBottom = METER_BOTTOM - 4;
        drumShade.setShader(new LinearGradient(0, drumTop, 0, drumBottom,
                new int[] {0xcc000000, 0, 0, 0xcc000000}, new float[] {0, .3f, .7f, 1}, Shader.TileMode.CLAMP));
        shaftPaint.setShader(new LinearGradient(PIVOT_X - 4, 0, PIVOT_X + 4, 0,
                new int[] {0xff4b4f57, 0xfff7f9fb, 0xffc5cad2, 0xff5c6069}, new float[] {0, .3f, .6f, 1}, Shader.TileMode.CLAMP));
        lipPaint.setShader(new LinearGradient(0, TRAY_FLOOR - 2, 0, TRAY_FLOOR + 7,
                new int[] {0xfff7f9fb, 0xff9ea4ad, 0xffe3e6ea, 0xff5c6069}, new float[] {0, .35f, .6f, 1}, Shader.TileMode.CLAMP));
        setFilterTouchesWhenObscured(true);
        updateDisplay();
    }

    private static int indexOf(int[] strip, int symbol) {
        for (int i = 0; i < strip.length; ++i) {
            if (strip[i] == symbol) return i;
        }
        return 0;
    }

    void startSession() {
        setActive(false);
        resetGame();
        creditsShown = coins;
        paidShown = 0;
        setActive(true);
    }

    private void resetGame() {
        for (int i = 0; i < reels.length; ++i) {
            reels[i].reset(indexOf(STRIPS[i], START_SYMBOLS[i]));
        }
        spinning = tease = false;
        coins = INITIAL_COINS;
        lastWin = payoutLeft = winShown = winMask = 0;
        winAt = -1;
        jackpot = false;
        pile = drops = 0;
        message = R.string.WhisperSlotReady;
        messageAt = SystemClock.uptimeMillis();
    }

    void setActive(boolean active) {
        this.active = active;
        if (active) {
            if (isAttachedToWindow()) sounds.preload(getContext());
            refreshMotion();
        } else {
            gesture.reset();
            cancelPendingUnlock();
            sounds.release();
            settle();
        }
        updateDisplay();
        kick();
    }

    /** Fast-forwards everything in flight, silently. */
    private void settle() {
        if (spinning) {
            for (RetroSlotReel reel : reels) reel.finish();
            finishSpin(SystemClock.uptimeMillis());
        }
        settlePayout();
        pile = Math.min(PILE_CAP, pile + drops);
        drops = 0;
        creditsShown = coins;
        paidShown = winShown;
        lever.reset();
        leverGrabbed = leverEngaged = false;
    }

    private void refreshMotion() {
        motion = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
        PowerManager power = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        idleLights = motion && (power == null || !power.isPowerSaveMode());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) {
            sounds.preload(getContext());
            refreshMotion();
        }
        kick();
    }

    @Override
    protected void onDetachedFromWindow() {
        setActive(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        kick();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            gesture.reset();
            sounds.release();
            if (leverGrabbed) releaseLever();
            if (unlockPending) {
                cancelPendingUnlock();
                updateDisplay();
            }
        } else if (active) {
            sounds.preload(getContext());
            if (spinning) sounds.startLoop(Cue.REEL);
            refreshMotion();
        }
        kick();
    }

    // region Game

    private void play(boolean fromLever) {
        if (!active || unlockPending || spinning) return;
        gesture.reset();
        long now = SystemClock.uptimeMillis();
        settlePayout();
        if (coins == 0) {
            resetGame();
            updateDisplay();
            playSound(Cue.SPIN, 1.12f);
            kick();
            return;
        }
        coins--;
        lastWin = winShown = winMask = 0;
        winAt = -1;
        jackpot = false;
        message = R.string.WhisperSlotRolling;
        messageAt = now;
        for (int i = 0; i < result.length; ++i) {
            result[i] = random.nextInt(SYMBOL_COUNT);
        }
        tease = result[0] == result[1];
        spinning = true;
        landed = 0;
        if (!motion) {
            for (int i = 0; i < reels.length; ++i) {
                reels[i].reset(indexOf(STRIPS[i], result[i]));
            }
            settleUntil = now;
            finishSpin(now);
            return;
        }

        long start = now;
        if (!fromLever) {
            // Pulled by an accessibility action: the reels start when the handle bottoms out.
            lever.autoPull(now);
            start += RetroSlotLever.AUTO_PULL_MS;
        }
        long previousLanding = 0;
        for (int i = 0; i < reels.length; ++i) {
            reels[i].start(start + i * REEL_STAGGER_MS);
            long brake = start + FIRST_STOP_MS + i * REEL_GAP_MS + (tease && i == reels.length - 1 ? TEASE_MS : 0);
            brake = Math.max(brake, previousLanding + MIN_LAND_GAP_MS - RetroSlotReel.BRAKE_MS);
            previousLanding = reels[i].stop(brake, result[i]);
        }
        settleUntil = reels[reels.length - 1].settlesAt();
        updateDisplay();
        playSound(Cue.SPIN, 1f);
        if (canPlaySound()) sounds.startLoop(Cue.REEL);
        kick();
    }

    private void finishSpin(long now) {
        spinning = false;
        landed = reels.length;
        sounds.stopLoop();
        int a = result[0], b = result[1], c = result[2];
        jackpot = a == b && b == c;
        winMask = jackpot ? 7 : a == b ? 3 : a == c ? 5 : b == c ? 6 : 0;
        if (jackpot) {
            lastWin = TRIPLE_PAYS[a];
            message = R.string.WhisperSlotJackpot;
        } else if (winMask != 0) {
            lastWin = PAIR_PAY;
            message = R.string.WhisperSlotPair;
        } else {
            lastWin = 0;
            message = coins == 0 ? R.string.WhisperSlotEmpty : R.string.WhisperSlotMiss;
        }
        messageAt = now;
        winAt = lastWin > 0 ? now : -1;
        winShown = 0;
        payoutLeft = lastWin;
        nextPayoutAt = now + PAYOUT_DELAY_MS;
        if (!motion) settlePayout();
        updateDisplay();
        playSound(jackpot ? Cue.JACKPOT : lastWin > 0 ? Cue.WIN : Cue.MISS, 1f);
        if (active && hasWindowFocus()) announceForAccessibility(displaySummary());
    }

    /** Pays out what is left of the last win at once. */
    private void settlePayout() {
        if (payoutLeft == 0) return;
        coins = Math.min(MAX_COINS, coins + payoutLeft);
        winShown += payoutLeft;
        pile = Math.min(PILE_CAP, pile + payoutLeft);
        payoutLeft = 0;
    }

    private int totalCoins() {
        return Math.min(MAX_COINS, coins + payoutLeft);
    }

    private void step(long now, float dt) {
        lever.update(now, dt);
        if (spinning) {
            int last = reels.length - 1;
            while (landed < last && now >= reels[landed].landsAt()) {
                playSound(Cue.STOP, 1f + .1f * landed);
                haptic(HapticFeedbackConstants.CLOCK_TICK);
                landed++;
            }
            if (now >= reels[last].landsAt()) {
                haptic(HapticFeedbackConstants.CLOCK_TICK);
                finishSpin(now);
            }
        }
        if (payoutLeft > 0 && now >= nextPayoutAt) {
            while (payoutLeft > 0 && now >= nextPayoutAt) {
                payoutLeft--;
                winShown++;
                coins = Math.min(MAX_COINS, coins + 1);
                dropCoin();
                nextPayoutAt += PAYOUT_TICK_MS;
            }
            playSound(Cue.COIN, (winShown & 1) == 0 ? 1f : 1.06f);
            if (payoutLeft == 0) updateDisplay();
        }
        stepDrops(dt);
        if (motion) {
            creditsShown = approach(creditsShown, coins, dt);
            paidShown = approach(paidShown, winShown, dt);
        } else {
            creditsShown = coins;
            paidShown = winShown;
        }
    }

    /** Turns a counter toward its value, faster when it has far to go. */
    private static float approach(float shown, int target, float dt) {
        float distance = target - shown;
        float step = Math.max(16, Math.abs(distance) * 5) * dt;
        return Math.abs(distance) <= step ? target : shown + Math.signum(distance) * step;
    }

    private void dropCoin() {
        if (drops == MAX_DROPS) {
            pile = Math.min(PILE_CAP, pile + 1);
            return;
        }
        int slot = Math.min(pile + drops, PILE_CAP - 1);
        float x = TRAY_CENTER + (random.nextFloat() - .5f) * 6;
        float y = TRAY_TOP + 1;
        float vy = -70 - 40 * random.nextFloat();
        float floor = SLOT_Y[slot];
        float flight = (float) ((-vy + Math.sqrt(vy * vy + 2 * GRAVITY * (floor - y))) / GRAVITY);
        dropX[drops] = x;
        dropY[drops] = y;
        dropVx[drops] = (SLOT_X[slot] - x) / flight;
        dropVy[drops] = vy;
        dropFloor[drops] = floor;
        drops++;
    }

    private void stepDrops(float dt) {
        for (int i = 0; i < drops; ) {
            dropVy[i] += GRAVITY * dt;
            dropX[i] += dropVx[i] * dt;
            dropY[i] += dropVy[i] * dt;
            if (dropVy[i] > 0 && dropY[i] >= dropFloor[i]) {
                pile = Math.min(PILE_CAP, pile + 1);
                drops--;
                dropX[i] = dropX[drops];
                dropY[i] = dropY[drops];
                dropVx[i] = dropVx[drops];
                dropVy[i] = dropVy[drops];
                dropFloor[i] = dropFloor[drops];
            } else {
                i++;
            }
        }
    }

    private boolean canPlaySound() {
        return active && !unlockPending && hasWindowFocus();
    }

    private void playSound(Cue cue, float rate) {
        if (canPlaySound()) sounds.play(cue, rate);
    }

    private void haptic(int feedback) {
        if (canPlaySound()) performHapticFeedback(feedback);
    }

    private String displaySummary() {
        return getResources().getString(R.string.WhisperSlotSummary, totalCoins(), lastWin, getResources().getString(message));
    }

    private void updateDisplay() {
        setContentDescription(displaySummary());
        invalidate();
    }

    // endregion

    // region Frames

    private void onFrame() {
        frameScheduled = false;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrameAt == 0 ? 0 : Math.min(.05f, (now - lastFrameAt) / 1000f);
        lastFrameAt = now;
        step(now, dt);
        invalidate();
        scheduleFrame();
    }

    /** Restarts the frame loop, e.g. after a state change that needs smooth frames. */
    private void kick() {
        removeCallbacks(frame);
        frameScheduled = false;
        scheduleFrame();
    }

    private void scheduleFrame() {
        if (frameScheduled || !active || !isAttachedToWindow()) return;
        long now = SystemClock.uptimeMillis();
        if (isBusy(now)) {
            frameScheduled = true;
            postOnAnimation(frame);
        } else if (lightsAnimate()) {
            // Idle lights only change on whole steps, so wake up just for those.
            long step = isShowingWin(now) ? SHOW_STEP_MS : IDLE_STEP_MS;
            frameScheduled = true;
            postDelayed(frame, step - now % step);
        } else {
            lastFrameAt = 0;
        }
    }

    private boolean isBusy(long now) {
        return spinning || now < settleUntil || payoutLeft > 0 || drops > 0
                || (!lever.isAtRest() && !lever.isHeld())
                || creditsShown != coins || paidShown != winShown
                || (jackpot && isShowingWin(now) && lightsAnimate());
    }

    private boolean lightsAnimate() {
        return idleLights && hasWindowFocus() && isShown();
    }

    private boolean isShowingWin(long now) {
        return winAt >= 0 && now - winAt < (jackpot ? JACKPOT_SHOW_MS : WIN_SHOW_MS);
    }

    // endregion

    // region Touch

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (unlockPending) return true;
        if (!active || !onFilterTouchEventForSecurity(event)) {
            gesture.reset();
            if (leverGrabbed) releaseLever();
            return true;
        }
        int completed = 0;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gesture.begin(event.getX(), event.getY(), event.getEventTime());
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getHistorySize(); ++i) {
                    gesture.move(event.getHistoricalX(i), event.getHistoricalY(i));
                }
                gesture.move(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_UP:
                float minimumSpan = Math.max(dp(20), Math.min(getWidth(), getHeight()) * .075f);
                completed = gesture.end(event.getX(), event.getY(), event.getEventTime(), minimumSpan);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                gesture.reset();
                break;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (completed > 0) {
            sounds.play(Cue.GESTURE, 1f);
            vibrateForGesture();
            if (completed == 3) {
                unlockPending = true;
                if (leverGrabbed) releaseLever();
                updateDisplay();
                postDelayed(unlockAfterCue, FINAL_CUE_DELAY_MS);
            }
        }
        return handled;
    }

    private void completeUnlock() {
        if (!active || !unlockPending) return;
        setActive(false);
        unlock.run();
    }

    private void cancelPendingUnlock() {
        if (unlockPending) {
            unlockPending = false;
            removeCallbacks(unlockAfterCue);
        }
    }

    private void vibrateForGesture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && performHapticFeedback(HapticFeedbackConstants.CONFIRM)) {
            return;
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isOverKnob(event.getX(), event.getY())) {
                    leverGrabbed = true;
                    leverEngaged = false;
                    leverLocked = spinning;
                    leverDownX = event.getX();
                    leverDownY = event.getY();
                    leverGrabOffset = event.getY() - (top + knobY() * scale);
                    dragLever(event.getY());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!leverGrabbed) break;
                float dx = Math.abs(event.getX() - leverDownX);
                if (dx > touchSlop && dx > LEVER_MAX_SLANT * Math.abs(event.getY() - leverDownY)) {
                    // A slanted stroke slips off the handle; it may be half of an X.
                    releaseLever();
                } else {
                    dragLever(event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (leverGrabbed) releaseLever();
                break;
        }
        return true;
    }

    private boolean isOverKnob(float x, float y) {
        float reach = Math.max(KNOB_RADIUS + 10, dp(26) / scale);
        float bx = (x - left) / scale - knobX();
        float by = (y - top) / scale - knobY();
        return bx * bx + by * by <= reach * reach;
    }

    private float knobX() {
        return PIVOT_X + LEVER_TILT * (float) Math.cos(lever.angle());
    }

    private float knobY() {
        return PIVOT_Y - LEVER_LENGTH * (float) Math.cos(lever.angle());
    }

    private void dragLever(float y) {
        float knob = (y - leverGrabOffset - top) / scale;
        float cos = Math.max(-1, Math.min(1, (PIVOT_Y - knob) / LEVER_LENGTH));
        if (lever.hold((float) Math.acos(cos), leverLocked)) {
            playSound(Cue.LEVER, 1f + .05f * lever.angle());
            haptic(HapticFeedbackConstants.CLOCK_TICK);
        }
        if (!leverEngaged && !leverLocked && lever.angle() >= RetroSlotLever.MAX - .02f) {
            leverEngaged = true;
            if (active && !unlockPending) {
                playSound(Cue.CLUNK, 1f);
                haptic(HapticFeedbackConstants.KEYBOARD_TAP);
                play(true);
            }
        }
        invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (active && !unlockPending && !spinning) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,
                    getResources().getString(R.string.WhisperSlotPull)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            if (!active || unlockPending || spinning) return false;
            play(false);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private void releaseLever() {
        leverGrabbed = leverEngaged = false;
        lever.release();
        if (!motion) lever.reset();
        kick();
        invalidate();
    }

    // endregion

    // region Layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(this);
        Insets insets = rootInsets == null ? Insets.NONE
                : rootInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        float spaceWidth = width - insets.left - insets.right;
        float spaceHeight = height - insets.top - insets.bottom;
        scale = Math.max(.1f, Math.min((spaceWidth - dp(24)) / BOARD_WIDTH, (spaceHeight - dp(24)) / BOARD_HEIGHT));
        // Whole pixels keep the painted cabinet bitmap crisp.
        left = Math.round(insets.left + (spaceWidth - BOARD_WIDTH * scale) / 2);
        top = Math.round(insets.top + (spaceHeight - BOARD_HEIGHT * scale) / 2);
        setMeasuredDimension(width, height);
    }

    // endregion

    // region Drawing

    /** Paints the artwork once per scale; everything after that is cheap sprite work. */
    private void ensureArt() {
        if (cabinet != null && artScale == scale) return;
        artScale = scale;
        cabinet = paintCabinet(getResources(), scale);
        int symbolSize = Math.round(SYMBOL_SIZE * scale);
        for (int i = 0; i < SYMBOL_COUNT; ++i) {
            symbols[i] = paintSymbol(i, symbolSize);
        }
        for (int i = 0; i < BULB_COLORS.length; ++i) {
            bulbs[i][0] = paintBulb(BULB_COLORS[i], false, scale);
            bulbs[i][1] = paintBulb(BULB_COLORS[i], true, scale);
        }
        beaconOn = paintBeacon(true, scale);
        beaconOff = paintBeacon(false, scale);
        beaconGlow = paintGlow(0xffff3b30, BEACON_GLOW, scale);
        knob = paintSphere(KNOB_RADIUS * 1.3f, true, scale);
        hub = paintSphere(HUB_RADIUS, false, scale);
        coin = paintCoin(scale);
    }

    /** The dim arcade around the cabinet: soft light only, so a quarter-size bitmap stretched over the screen is enough. */
    private void ensureRoom() {
        int width = getWidth(), height = getHeight();
        if ((room != null && width == roomWidth && height == roomHeight) || width == 0 || height == 0) return;
        roomWidth = width;
        roomHeight = height;
        room = Bitmap.createBitmap(Math.max(1, width / 4), Math.max(1, height / 4), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(room);
        c.scale(room.getWidth() / (float) width, room.getHeight() / (float) height);
        c.drawColor(ROOM);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float size = Math.max(width, height);
        for (int i = 0; i < ROOM_LIGHTS.length; ++i) {
            float[] light = ROOM_LIGHTS[i];
            p.setShader(new RadialGradient(light[0] * width, light[1] * height, light[2] * size,
                    ROOM_COLORS[i], 0, Shader.TileMode.CLAMP));
            c.drawCircle(light[0] * width, light[1] * height, light[2] * size, p);
        }
        p.setShader(new RadialGradient(width / 2f, height / 2f, (float) Math.hypot(width, height) / 2,
                new int[] {0, 0, 0xb0000000}, new float[] {0, .5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean lights = lightsAnimate();
        ensureArt();
        ensureRoom();
        rect.set(0, 0, roomWidth, roomHeight);
        canvas.drawBitmap(room, null, rect, bitmapPaint);
        canvas.save();
        canvas.translate(left, top);
        canvas.drawBitmap(cabinet, 0, 0, bitmapPaint);
        canvas.scale(scale, scale);
        drawReels(canvas, now, lights);
        drawBulbs(canvas, now, lights);
        drawOdometer(canvas, CREDITS_LEFT, creditsShown);
        drawOdometer(canvas, PAID_LEFT, paidShown);
        drawMessage(canvas, now, lights);
        drawTray(canvas, now);
        drawLever(canvas);
        drawBeacon(canvas, now, lights);
        canvas.restore();
    }

    private boolean isBulbLit(int i, long now, boolean lights) {
        if (!lights) return true;
        if (isShowingWin(now)) {
            long since = now - winAt;
            if (jackpot) return mod(i - since / SHOW_STEP_MS, 4) < 2;
            return (i + since / BLINK_MS) % 2 == 0;
        }
        if (spinning) return mod(i - now / 60, 5) < 2;
        return mod(i - now / IDLE_STEP_MS, 3) == 0;
    }

    private static int mod(long value, int divisor) {
        int m = (int) (value % divisor);
        return m < 0 ? m + divisor : m;
    }

    private void drawBulbs(Canvas canvas, long now, boolean lights) {
        for (int i = 0; i < BULB_COUNT; ++i) {
            Bitmap bulb = bulbs[i % BULB_COLORS.length][isBulbLit(i, now, lights) ? 1 : 0];
            drawSprite(canvas, bulb, BULB_X[i], BULB_Y[i], 2 * BULB_GLOW, 2 * BULB_GLOW, 255);
        }
    }

    private void drawBeacon(Canvas canvas, long now, boolean lights) {
        boolean on = jackpot && isShowingWin(now);
        if (on) {
            // A slow pulse, like a lamp turning inside the dome.
            float glow = lights ? .55f + .45f * (float) Math.sin(2 * Math.PI * (now - winAt) / 700.0) : 1;
            drawSprite(canvas, beaconGlow, BEACON_X, BEACON_Y - 4, 2 * BEACON_GLOW, 2 * BEACON_GLOW, Math.round(255 * glow));
        }
        rect.set(BEACON_X - BEACON_RADIUS - 1, BEACON_Y - BEACON_RADIUS - 1, BEACON_X + BEACON_RADIUS + 1, BEACON_Y);
        canvas.drawBitmap(on ? beaconOn : beaconOff, null, rect, bitmapPaint);
    }

    private void drawReels(Canvas canvas, long now, boolean lights) {
        boolean showing = isShowingWin(now);
        boolean blinkOn = !lights || ((now - Math.max(0, winAt)) / BLINK_MS) % 2 == 0;
        for (int i = 0; i < reels.length; ++i) {
            drawReel(canvas, i, REEL_LEFT + REEL_STEP * i, now);
        }
        for (int i = 0; i < reels.length; ++i) {
            float x = REEL_LEFT + REEL_STEP * i;
            if ((winMask & (1 << i)) != 0 && !spinning && (!showing || blinkOn)) {
                glowFrame(canvas, x + 1.5f, PAYLINE - 21, x + REEL_WIDTH - 1.5f, PAYLINE + 21, showing ? GOLD : 0x99ffd36b);
            }
            boolean teasing = spinning && tease && i == reels.length - 1 && landed == reels.length - 1;
            if (teasing && (!lights || (now / BLINK_MS) % 2 == 0)) {
                glowFrame(canvas, x - 2.5f, REEL_TOP - 2.5f, x + REEL_WIDTH + 2.5f, REEL_BOTTOM + 2.5f, CYAN);
            }
        }
    }

    private void drawReel(Canvas canvas, int index, float x, long now) {
        RetroSlotReel reel = reels[index];
        canvas.save();
        canvas.clipRect(x, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM);
        fillPaint.setColor(CREAM);
        canvas.drawRect(x, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM, fillPaint);
        float position = reel.position(now);
        float velocity = reel.speed(now) / RetroSlotReel.SPEED;
        float blur = Math.min(1, Math.abs(velocity));
        float center = x + REEL_WIDTH / 2;
        int base = (int) Math.floor(position);
        for (int k = base - 2; k <= base + 2; ++k) {
            // Wrap the strip around the drum: symbols squash and slide away near the edges.
            float angle = (position - k) * SYMBOL_PITCH / DRUM_RADIUS;
            if (Math.abs(angle) > 1.45f) continue;
            float y = PAYLINE + DRUM_RADIUS * (float) Math.sin(angle);
            float height = SYMBOL_SIZE * (float) Math.cos(angle) * (1 + .45f * blur);
            Bitmap symbol = symbols[reel.symbolAt(k)];
            if (blur > .15f) {
                // Motion blur: fading copies trail behind the direction of travel.
                float trail = Math.signum(velocity) * 8 * blur;
                for (int ghost = 3; ghost >= 1; --ghost) {
                    drawSprite(canvas, symbol, center, y - trail * ghost, SYMBOL_SIZE, height, Math.round(110 * blur / ghost));
                }
            }
            drawSprite(canvas, symbol, center, y, SYMBOL_SIZE, height, Math.round(255 * (1 - .35f * blur)));
        }
        canvas.drawRect(x, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM, reelShade);
        canvas.drawRect(x, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM, reelGlare);
        fillPaint.setColor(0x38000000);
        canvas.drawRect(x, REEL_TOP, x + 2.5f, REEL_BOTTOM, fillPaint);
        canvas.drawRect(x + REEL_WIDTH - 2.5f, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM, fillPaint);
        canvas.restore();
    }

    private void glowFrame(Canvas canvas, float l, float t, float r, float b, int color) {
        rect.set(l, t, r, b);
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeWidth(4.5f);
        fillPaint.setColor(0x50000000 | color & 0xffffff);
        canvas.drawRoundRect(rect, 3, 3, fillPaint);
        fillPaint.setStrokeWidth(1.6f);
        fillPaint.setColor(color);
        canvas.drawRoundRect(rect, 3, 3, fillPaint);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    /** Mechanical counter: each drum turns into the next digit as the one below wraps. */
    private void drawOdometer(Canvas canvas, float left, float value) {
        float top = METER_TOP + 11, bottom = METER_BOTTOM - 4, height = bottom - top;
        float width = 11, gap = 1.4f;
        float x = left + (METER_WIDTH - 4 * width - 3 * gap) / 2;
        value = Math.max(0, Math.min(MAX_COINS, value));
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(11);
        textPaint.setColor(0xfff2efe6);
        for (int place = 1000; place >= 1; place /= 10, x += width + gap) {
            float turns = value / place;
            int digit = (int) turns % 10;
            float below = value - (float) Math.floor(turns) * place;
            float roll = place == 1 ? turns - (float) Math.floor(turns) : Math.max(0, below - (place - 1));
            fillPaint.setColor(0xff101013);
            canvas.drawRect(x, top, x + width, bottom, fillPaint);
            canvas.save();
            canvas.clipRect(x, top, x + width, bottom);
            float baseline = top + height / 2 + 4 - roll * height;
            canvas.drawText(DIGITS[digit], x + width / 2, baseline, textPaint);
            if (roll > 0) canvas.drawText(DIGITS[(digit + 1) % 10], x + width / 2, baseline + height, textPaint);
            canvas.drawRect(x, top, x + width, bottom, drumShade);
            canvas.restore();
        }
    }

    private void drawMessage(Canvas canvas, long now, boolean lights) {
        int text = message;
        int color = lastWin > 0 ? GOLD : message == R.string.WhisperSlotEmpty ? ROSE : AMBER;
        if (isShowingWin(now)) {
            if (lights && ((now - winAt) / BLINK_MS) % 2 == 1) return;
        } else if (!spinning) {
            boolean hint = !lights || ((now - messageAt) / HINT_MS) % 2 == 1;
            if (totalCoins() == 0) {
                // Out of coins: the handle starts a new game.
                if (hint) text = R.string.WhisperSlotNewGame;
            } else if (lights && hint && (message == R.string.WhisperSlotReady
                    || message == R.string.WhisperSlotMiss || lastWin > 0)) {
                text = R.string.WhisperSlotPull;
                color = 0xffffe2b8;
            }
        }
        String string = getResources().getString(text);
        float maxWidth = MESSAGE_RIGHT - MESSAGE_LEFT - 9;
        textPaint.setTextSize(10);
        float width = textPaint.measureText(string);
        if (width > maxWidth) textPaint.setTextSize(10 * maxWidth / width);
        float x = (MESSAGE_LEFT + MESSAGE_RIGHT) / 2, y = (METER_TOP + METER_BOTTOM) / 2 + 3.6f;
        // Lamp-lit letters behind smoked glass.
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(3);
        textPaint.setColor(0x44000000 | color & 0xffffff);
        canvas.drawText(string, x, y, textPaint);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(color);
        canvas.drawText(string, x, y, textPaint);
    }

    private void drawTray(Canvas canvas, long now) {
        for (int row = 0; row < PILE_ROWS.length; ++row) {
            for (int i = 0; i < pile; ++i) {
                if (SLOT_ROW[i] != row) continue;
                rect.set(SLOT_X[i] - COIN_WIDTH / 2, SLOT_Y[i], SLOT_X[i] + COIN_WIDTH / 2, SLOT_Y[i] + COIN_HEIGHT);
                canvas.drawBitmap(coin, null, rect, bitmapPaint);
            }
        }
        for (int i = 0; i < drops; ++i) {
            // Coins tumble as they fly.
            float flip = Math.abs((float) Math.cos(now / 45.0 + i * 1.7));
            drawSprite(canvas, coin, dropX[i], dropY[i], COIN_WIDTH * .8f, Math.max(1.2f, COIN_WIDTH * .8f * flip), 255);
        }
        // Rolled front lip of the tray, in front of the coins.
        fillPaint.setColor(0x99000000);
        canvas.drawRect(76, TRAY_FLOOR - .5f, 192, TRAY_FLOOR + 1, fillPaint);
        rect.set(74, TRAY_FLOOR, 194, TRAY_FLOOR + 8.5f);
        canvas.drawRoundRect(rect, 4, 4, lipPaint);
    }

    private void drawLever(Canvas canvas) {
        float knobX = knobX(), knobY = knobY();
        float depth = (float) Math.sin(lever.angle());
        drawSprite(canvas, hub, PIVOT_X, PIVOT_Y, 2 * HUB_RADIUS, 2 * HUB_RADIUS, 255);
        // The shaft looks thicker while it points at the viewer.
        float half = 2.2f + 1.3f * Math.max(0, depth);
        float length = (float) Math.hypot(knobX - PIVOT_X, knobY - PIVOT_Y);
        canvas.save();
        canvas.rotate((float) Math.toDegrees(Math.atan2(knobX - PIVOT_X, PIVOT_Y - knobY)), PIVOT_X, PIVOT_Y);
        fillPaint.setColor(0x40000000);
        rect.set(PIVOT_X - half + 1.5f, PIVOT_Y - length + 1, PIVOT_X + half + 1.5f, PIVOT_Y);
        canvas.drawRoundRect(rect, half, half, fillPaint);
        rect.set(PIVOT_X - half, PIVOT_Y - length, PIVOT_X + half, PIVOT_Y);
        canvas.drawRoundRect(rect, half, half, shaftPaint);
        canvas.restore();
        fillPaint.setColor(0xff5c6069);
        canvas.drawCircle(PIVOT_X, PIVOT_Y, 2.6f, fillPaint);
        // The knob grows as it swings toward the viewer.
        float radius = KNOB_RADIUS * (1 + .3f * depth);
        fillPaint.setColor(0x50000000);
        canvas.drawCircle(knobX + 1.5f, knobY + 2, radius, fillPaint);
        drawSprite(canvas, knob, knobX, knobY, 2 * radius, 2 * radius, 255);
    }

    private void drawSprite(Canvas canvas, Bitmap bitmap, float cx, float cy, float width, float height, int alpha) {
        rect.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2);
        bitmapPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint);
        bitmapPaint.setAlpha(255);
    }

    // endregion
}
