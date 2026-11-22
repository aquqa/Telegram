package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RetroSlotMachineSounds.Cue;

import java.util.Arrays;
import java.util.Random;

/**
 * Full-screen arcade cabinet shown while the local app lock is active. Everything is
 * drawn on a 284 x 372 pixel board that is scaled up to fit the screen.
 */
final class RetroSlotMachineView extends FrameLayout {
    private static final int BOARD_WIDTH = 284;
    private static final int BOARD_HEIGHT = 372;
    private static final int INITIAL_COINS = 20;
    private static final int MAX_COINS = 9999;
    private static final int PAIR_PAY = 2;
    // Three of a kind, by symbol: cherries, coin, bolt, heart, gem, seven.
    private static final int[] TRIPLE_PAYS = {5, 8, 10, 12, 15, 30};
    private static final String[] PAY_LABELS = {"\u00d75", "\u00d78", "\u00d710", "\u00d712", "\u00d715", "\u00d730"};

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

    private static final int NIGHT = 0xff100d25;
    private static final int SHADOW = 0xff08061a;
    private static final int DARK = 0xff0b0718;
    private static final int INK = 0xff1c1538;
    private static final int PANEL = 0xff38244f;
    private static final int PURPLE = 0xff65427c;
    private static final int LILAC = 0xff8a63a3;
    private static final int GOLD = 0xffffd36b;
    private static final int AMBER = 0xffc9803c;
    private static final int MINT = 0xff85f6cb;
    private static final int CREAM = 0xffffedce;
    private static final int PINK = 0xffff7db4;
    private static final int LED = 0xffff5a5f;
    private static final int LED_OFF = 0xff3b1830;
    private static final int CHROME = 0xffe4def0;
    private static final int STEEL = 0xff9c94b3;
    private static final int KNOB = 0xffe23c5a;
    private static final int KNOB_DARK = 0xff8e1f3c;

    // Each symbol is a 9x9 sprite with its own three-color palette.
    private static final String[][] SPRITES = {
            {".....22..", "....233..", "...222...", "..22.2...", ".111.111.", "113111311", "111111111", "111.1111.", ".11..11.."},
            {"..22222..", ".2333332.", "233111332", "231313132", "231313132", "231313132", "233111332", ".2333332.", "..22222.."},
            {".....33..", "....331..", "...331...", "..331....", ".3333332.", "...331...", "..331....", ".331.....", ".1......."},
            {".111.111.", "133313331", "133333331", "133333331", ".1333331.", "..13331..", "...131...", "....1....", "........."},
            {"...333...", "..33222..", ".3322211.", "333222111", ".3322211.", "..32111..", "...211...", "....1....", "........."},
            {".........", ".3333333.", ".3111131.", ".....331.", "....331..", "...331...", "..331....", "..31.....", "........."}
    };
    private static final int[][] PALETTES = {
            {0xffd04066, 0xff42876d, 0xffff9bb1}, {0xffc2724c, 0xffa8534b, GOLD},
            {0xffe98741, 0xffb75a4a, GOLD}, {0xffb7447c, 0xffe15f91, PINK},
            {0xff4297bc, 0xff60dcca, 0xffc2ffe5}, {0xffa43560, 0xffc85a86, 0xffff5680}
    };
    // Every strip holds each symbol once, so every symbol stays equally likely.
    private static final int[][] STRIPS = {{0, 3, 1, 4, 2, 5}, {2, 0, 5, 3, 1, 4}, {1, 5, 0, 2, 4, 3}};
    private static final int[] START_SYMBOLS = {0, 1, 5};

    private static final float REEL_LEFT = 23;
    private static final float REEL_STEP = 72;
    private static final float REEL_WIDTH = 66;
    private static final float REEL_TOP = 126;
    private static final float REEL_BOTTOM = 216;
    private static final float PAYLINE = 171;
    private static final float SYMBOL_SIZE = 40.5f;
    private static final float SYMBOL_PITCH = 46;
    private static final float DRUM_RADIUS = 60;
    private static final int[] DRUM_SHADES = {0x78, 0x58, 0x3c, 0x24, 0x10};

    private static final float PIVOT_X = 266;
    private static final float PIVOT_Y = 172;
    private static final float LEVER_LENGTH = 66;
    private static final float KNOB_RADIUS = 9;
    // A pull must stay close to vertical: an X stroke is at least 0.41 wide per unit of height.
    private static final float LEVER_MAX_SLANT = .35f;

    private static final float TRAY_CENTER = 128;
    private static final float TRAY_TOP = 338;
    private static final float TRAY_FLOOR = 353;
    private static final float GRAVITY = 900;
    private static final int MAX_DROPS = 24;
    private static final int[] PILE_ROWS = {18, 15, 12, 9};
    private static final int PILE_CAP;
    private static final float[] SLOT_X;
    private static final float[] SLOT_Y;
    private static final int[] SLOT_ROW;

    private static final int BULBS_ACROSS = 25;
    private static final int BULBS_DOWN = 5;
    private static final int BULB_COUNT = 2 * (BULBS_ACROSS + BULBS_DOWN);
    private static final float[] BULB_X = new float[BULB_COUNT];
    private static final float[] BULB_Y = new float[BULB_COUNT];

    static {
        // Coins pile into a mound: low and central slots fill first.
        int count = 0;
        for (int row : PILE_ROWS) count += row;
        float[][] slots = new float[count][];
        int k = 0;
        for (int row = 0; row < PILE_ROWS.length; ++row) {
            for (int j = 0; j < PILE_ROWS[row]; ++j) {
                float x = TRAY_CENTER + (j - (PILE_ROWS[row] - 1) / 2f) * 9;
                slots[k++] = new float[] {row * 4.5f + Math.abs(x - TRAY_CENTER) * .5f, x, row};
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
            SLOT_Y[i] = TRAY_FLOOR - 4 - 3 * SLOT_ROW[i];
        }

        // Marquee bulbs, clockwise from the top left corner.
        int b = 0;
        for (int i = 0; i < BULBS_ACROSS; ++i, ++b) {
            BULB_X[b] = 19 + 218f * i / (BULBS_ACROSS - 1);
            BULB_Y[b] = 16;
        }
        for (int i = 1; i <= BULBS_DOWN; ++i, ++b) {
            BULB_X[b] = 237;
            BULB_Y[b] = 16 + 56f * i / (BULBS_DOWN + 1);
        }
        for (int i = BULBS_ACROSS - 1; i >= 0; --i, ++b) {
            BULB_X[b] = 19 + 218f * i / (BULBS_ACROSS - 1);
            BULB_Y[b] = 72;
        }
        for (int i = BULBS_DOWN; i >= 1; --i, ++b) {
            BULB_X[b] = 19;
            BULB_Y[b] = 16 + 56f * i / (BULBS_DOWN + 1);
        }
    }

    private final Paint blocks = new Paint();
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint = new Paint();
    private final RectF spriteRect = new RectF();
    private final Bitmap[] sprites = new Bitmap[SPRITES.length];
    private final Random random = new Random();
    private final LockScreenXGesture gesture = new LockScreenXGesture();
    private final RetroSlotMachineSounds sounds = new RetroSlotMachineSounds();
    private final RetroSlotReel[] reels = new RetroSlotReel[STRIPS.length];
    private final RetroSlotLever lever = new RetroSlotLever();
    private final Runnable unlock;
    private final Runnable unlockAfterCue = this::completeUnlock;
    private final Runnable frame = this::onFrame;
    private final ArcadeButton spinButton;
    private final ArcadeButtonFace buttonFace = new ArcadeButtonFace();
    private final int touchSlop;
    private final int[] result = new int[STRIPS.length];

    private boolean spinning;
    private boolean tease;
    private int landed;
    private long settleUntil;
    private int coins = INITIAL_COINS;
    private int lastWin;
    private int payoutLeft;
    private int winShown;
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
    private float downX, downY;
    private boolean dragging;
    private float scale = 1, left, top;

    RetroSlotMachineView(Context context, Runnable unlock) {
        super(context);
        this.unlock = unlock;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        letters.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        letters.setTextAlign(Paint.Align.CENTER);
        spritePaint.setFilterBitmap(false);
        for (int i = 0; i < SPRITES.length; ++i) {
            sprites[i] = createSprite(i);
        }
        for (int i = 0; i < reels.length; ++i) {
            reels[i] = new RetroSlotReel(STRIPS[i], indexOf(STRIPS[i], START_SYMBOLS[i]));
        }
        setWillNotDraw(false);
        setBackgroundColor(NIGHT);
        setFilterTouchesWhenObscured(true);

        spinButton = new ArcadeButton(context);
        spinButton.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        spinButton.setTextColor(INK);
        spinButton.setLetterSpacing(.1f);
        spinButton.setPadding(0, 0, 0, 0);
        spinButton.setStateListAnimator(null);
        spinButton.setSoundEffectsEnabled(false);
        spinButton.setBackground(buttonFace);
        spinButton.setOnClickListener(v -> play(false));
        addView(spinButton, new LayoutParams(0, 0));
        updateDisplay();
    }

    private static Bitmap createSprite(int symbol) {
        String[] sprite = SPRITES[symbol];
        Bitmap bitmap = Bitmap.createBitmap(sprite[0].length(), sprite.length, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < sprite.length; ++y) {
            for (int x = 0; x < sprite[y].length(); ++x) {
                int color = sprite[y].charAt(x) - '1';
                bitmap.setPixel(x, y, color >= 0 ? PALETTES[symbol][color] : 0);
            }
        }
        return bitmap;
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
            result[i] = random.nextInt(SPRITES.length);
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
            // The button pulls the lever for you; the reels start when it bottoms out.
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
        spinButton.setEnabled(active && !unlockPending && !spinning);
        int label = totalCoins() == 0 && !spinning ? R.string.WhisperSlotNewGame : R.string.WhisperSlotSpin;
        spinButton.setText(label);
        spinButton.setContentDescription(getResources().getString(label) + ". " + displaySummary());
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
                || (!lever.isAtRest() && !lever.isHeld());
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
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                gesture.begin(downX, downY, event.getEventTime());
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getHistorySize(); ++i) {
                    gesture.move(event.getHistoricalX(i), event.getHistoricalY(i));
                }
                gesture.move(event.getX(), event.getY());
                dragging |= Math.abs(event.getX() - downX) > touchSlop || Math.abs(event.getY() - downY) > touchSlop;
                break;
            case MotionEvent.ACTION_UP:
                float minimumSpan = Math.max(dp(20), Math.min(getWidth(), getHeight()) * .075f);
                completed = gesture.end(event.getX(), event.getY(), event.getEventTime(), minimumSpan);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                dragging = true;
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
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // A diagonal that starts over SPIN is a stroke, even if it ends on the button.
        return dragging;
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
        float bx = (x - left) / scale - PIVOT_X;
        float by = (y - top) / scale - knobY();
        return bx * bx + by * by <= reach * reach;
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
        left = insets.left + (spaceWidth - BOARD_WIDTH * scale) / 2;
        top = insets.top + (spaceHeight - BOARD_HEIGHT * scale) / 2;
        spinButton.unit = buttonFace.unit = scale;
        spinButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16 * scale);
        spinButton.measure(MeasureSpec.makeMeasureSpec(Math.round(206 * scale), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(34 * scale), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = Math.round(left + 25 * scale);
        int y = Math.round(top + 259 * scale);
        spinButton.layout(x, y, x + spinButton.getMeasuredWidth(), y + spinButton.getMeasuredHeight());
    }

    // endregion

    // region Drawing

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean lights = lightsAnimate();
        drawStars(canvas, now, lights);
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        drawCabinet(canvas);
        drawMarquee(canvas, now, lights);
        drawCounter(canvas, 19, 121, R.string.WhisperSlotCoins, coins, LED);
        drawCounter(canvas, 135, 237, R.string.WhisperSlotWin, winShown, GOLD);
        drawReels(canvas, now, lights);
        drawMessage(canvas, now, lights);
        // Socket the SPIN button sinks into.
        rect(canvas, 23, 257, 233, 298, DARK);
        rect(canvas, 23, 296, 233, 298, SHADOW);
        drawPaytable(canvas);
        title(canvas, getResources().getString(R.string.WhisperSlotRules), 128, 331, 7, CREAM);
        drawTray(canvas, now);
        drawLever(canvas);
        canvas.restore();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawCrt(canvas);
    }

    private void drawStars(Canvas canvas, long now, boolean lights) {
        long twinkle = lights ? now / 400 : 0;
        for (int i = 0; i < 58; ++i) {
            float x = getWidth() * ((i * 83 + 13) % 151) / 151f;
            float y = getHeight() * ((i * 41 + 7) % 127) / 127f;
            float size = (i % 7 == 0 ? 3 : 2) * scale;
            int color = i % 7 == 0 ? PURPLE : PANEL;
            if (lights && (i * 7 + twinkle) % 23 == 0) color = LILAC;
            rect(canvas, x, y, x + size, y + size, color);
        }
    }

    private void drawCabinet(Canvas canvas) {
        // Stepped cabinet with a lit left edge and a shaded right edge.
        rect(canvas, 12, 10, 250, BOARD_HEIGHT, SHADOW);
        rect(canvas, 10, 0, 246, BOARD_HEIGHT - 7, PURPLE);
        rect(canvas, 4, 8, 252, BOARD_HEIGHT - 15, PURPLE);
        rect(canvas, 10, 8, 246, BOARD_HEIGHT - 15, PANEL);
        rect(canvas, 4, 8, 6, BOARD_HEIGHT - 15, LILAC);
        rect(canvas, 10, 0, 246, 2, LILAC);
        rect(canvas, 250, 8, 252, BOARD_HEIGHT - 15, 0xff4c3163);
    }

    private void drawMarquee(Canvas canvas, long now, boolean lights) {
        rect(canvas, 14, 11, 242, 77, SHADOW);
        rect(canvas, 15, 12, 241, 76, 0xff2a1d45);
        rect(canvas, 23, 21, 233, 67, PINK);
        rect(canvas, 24, 22, 232, 66, INK);
        for (int i = 0; i < BULB_COUNT; ++i) {
            drawBulb(canvas, BULB_X[i], BULB_Y[i], isBulbLit(i, now, lights));
        }
        boolean flash = jackpot && lights && isShowingWin(now) && ((now - winAt) / 300) % 2 == 1;
        title(canvas, "PIXEL SLOTS", 128, 49, 22, 0xff8f437d);
        title(canvas, "PIXEL SLOTS", 128, 47, 22, flash ? PINK : GOLD);
        title(canvas, getResources().getString(R.string.WhisperSlotArcade), 128, 61, 8, MINT);
    }

    private boolean isBulbLit(int i, long now, boolean lights) {
        if (!lights) return i % 2 == 0;
        if (isShowingWin(now)) {
            long since = now - winAt;
            if (jackpot) return mod(i - since / SHOW_STEP_MS, 4) < 2;
            return (i + since / BLINK_MS) % 2 == 0;
        }
        if (spinning) return mod(i - now / 50, 5) < 2;
        return mod(i - now / IDLE_STEP_MS, 3) == 0;
    }

    private static int mod(long value, int divisor) {
        int m = (int) (value % divisor);
        return m < 0 ? m + divisor : m;
    }

    private void drawBulb(Canvas canvas, float x, float y, boolean lit) {
        if (lit) {
            rect(canvas, x - 3.5f, y - 3.5f, x + 3.5f, y + 3.5f, 0x55ffd36b);
            rect(canvas, x - 2, y - 2, x + 2, y + 2, GOLD);
            rect(canvas, x - 1.5f, y - 1.5f, x, y, CREAM);
        } else {
            rect(canvas, x - 2, y - 2, x + 2, y + 2, 0xff5a3747);
            rect(canvas, x - 1.5f, y - 1.5f, x - .5f, y - .5f, 0xff7a5060);
        }
    }

    private void drawCounter(Canvas canvas, float left, float right, int label, int value, int color) {
        rect(canvas, left - 1, 83, right + 1, 116, SHADOW);
        rect(canvas, left, 84, right, 115, INK);
        rect(canvas, left + 3, 95, right - 3, 113, DARK);
        float center = (left + right) / 2f;
        title(canvas, getResources().getString(label), center, 92, 7, MINT);
        float x = center - 23;
        for (int digit = 3, place = 1000; digit >= 0; --digit, place /= 10) {
            int d = value / place % 10;
            boolean lit = value >= place || place == 1;
            drawDigit(canvas, x, 97, lit ? d : 8, lit, color);
            x += 12;
        }
    }

    private static final int[] SEGMENTS = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};

    /** A seven-segment digit; unlit segments still glow faintly behind the glass. */
    private void drawDigit(Canvas canvas, float x, float y, int digit, boolean lit, int color) {
        int mask = SEGMENTS[digit];
        for (int s = 0; s < 7; ++s) {
            boolean on = lit && (mask & (1 << s)) != 0;
            float l, t, r, b;
            switch (s) {
                case 0: l = x + 1; t = y; r = x + 8; b = y + 2; break;
                case 1: l = x + 7; t = y + 1; r = x + 9; b = y + 7; break;
                case 2: l = x + 7; t = y + 7; r = x + 9; b = y + 13; break;
                case 3: l = x + 1; t = y + 12; r = x + 8; b = y + 14; break;
                case 4: l = x; t = y + 7; r = x + 2; b = y + 13; break;
                case 5: l = x; t = y + 1; r = x + 2; b = y + 7; break;
                default: l = x + 1; t = y + 6; r = x + 8; b = y + 8; break;
            }
            if (on) rect(canvas, l - .5f, t - .5f, r + .5f, b + .5f, color & 0x40ffffff);
            rect(canvas, l, t, r, b, on ? color : LED_OFF);
        }
    }

    private void drawReels(Canvas canvas, long now, boolean lights) {
        rect(canvas, 12, 119, 244, 223, SHADOW);
        rect(canvas, 13, 120, 243, 222, GOLD);
        rect(canvas, 13, 120, 243, 121, CREAM);
        rect(canvas, 16, 123, 240, 219, INK);
        boolean blinkOn = !lights || ((now - (winAt >= 0 ? winAt : 0)) / BLINK_MS) % 2 == 0;
        boolean showing = isShowingWin(now);
        for (int i = 0; i < reels.length; ++i) {
            float x = REEL_LEFT + REEL_STEP * i;
            drawReel(canvas, i, x, now);
            if ((winMask & (1 << i)) != 0 && !spinning && (!showing || blinkOn)) {
                outline(canvas, x + 2, PAYLINE - 25, x + REEL_WIDTH - 2, PAYLINE + 25, 2, showing ? GOLD : 0xaaffd36b);
            }
            boolean teasing = spinning && tease && i == reels.length - 1 && landed == reels.length - 1;
            if (teasing && (!lights || (now / BLINK_MS) % 2 == 0)) {
                outline(canvas, x - 2, REEL_TOP - 2, x + REEL_WIDTH + 2, REEL_BOTTOM + 2, 2, MINT);
            }
        }
        // Payline painted on the glass, with arrows on both sides.
        boolean markers = !showing || blinkOn;
        rect(canvas, 18, PAYLINE - .5f, 238, PAYLINE + .5f, 0x66ff7db4);
        int arrow = markers ? PINK : 0xff8f437d;
        rect(canvas, 16, PAYLINE - 4, 18, PAYLINE + 4, arrow);
        rect(canvas, 18, PAYLINE - 3, 20, PAYLINE + 3, arrow);
        rect(canvas, 20, PAYLINE - 1.5f, 22, PAYLINE + 1.5f, arrow);
        rect(canvas, 238, PAYLINE - 4, 240, PAYLINE + 4, arrow);
        rect(canvas, 236, PAYLINE - 3, 238, PAYLINE + 3, arrow);
        rect(canvas, 234, PAYLINE - 1.5f, 236, PAYLINE + 1.5f, arrow);
        // Glare on the glass, in stair-stepped pixels.
        canvas.save();
        canvas.clipRect(16, 123, 240, 219);
        for (int j = 0; j < 33; ++j) {
            rect(canvas, 92 - j * 2, 123 + j * 3, 97 - j * 2, 126 + j * 3, 0x16ffffff);
            rect(canvas, 104 - j * 2, 123 + j * 3, 106 - j * 2, 126 + j * 3, 0x10ffffff);
        }
        canvas.restore();
    }

    private void drawReel(Canvas canvas, int index, float x, long now) {
        RetroSlotReel reel = reels[index];
        rect(canvas, x, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM, CREAM);
        canvas.save();
        canvas.clipRect(x, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM);
        float position = reel.position(now);
        float velocity = reel.speed(now) / RetroSlotReel.SPEED;
        float blur = Math.min(1, Math.abs(velocity));
        float center = x + REEL_WIDTH / 2;
        int base = (int) Math.floor(position);
        for (int k = base - 2; k <= base + 2; ++k) {
            // Wrap the strip around a drum: symbols squash and slide away near the edges.
            float angle = (position - k) * SYMBOL_PITCH / DRUM_RADIUS;
            if (Math.abs(angle) > 1.45f) continue;
            float y = PAYLINE + DRUM_RADIUS * (float) Math.sin(angle);
            float height = SYMBOL_SIZE * (float) Math.cos(angle) * (1 + .45f * blur);
            int symbol = reel.symbolAt(k);
            if (blur > .15f) {
                // Motion blur: fading copies trail behind the direction of travel.
                float trail = Math.signum(velocity) * 9 * blur;
                for (int ghost = 3; ghost >= 1; --ghost) {
                    drawSprite(canvas, symbol, center, y - trail * ghost, SYMBOL_SIZE, height, Math.round(110 * blur / ghost));
                }
            }
            drawSprite(canvas, symbol, center, y, SYMBOL_SIZE, height, Math.round(255 * (1 - .35f * blur)));
        }
        // Curved drum shading: darker toward the top and bottom of the window.
        for (int band = 0; band < DRUM_SHADES.length; ++band) {
            int color = DRUM_SHADES[band] << 24 | (INK & 0xffffff);
            rect(canvas, x, REEL_TOP + band * 5, x + REEL_WIDTH, REEL_TOP + band * 5 + 5, color);
            rect(canvas, x, REEL_BOTTOM - band * 5 - 5, x + REEL_WIDTH, REEL_BOTTOM - band * 5, color);
        }
        rect(canvas, x, PAYLINE - 34, x + REEL_WIDTH, PAYLINE - 30, 0x1cffffff);
        rect(canvas, x, REEL_TOP, x + 2, REEL_BOTTOM, 0x30000000);
        rect(canvas, x + REEL_WIDTH - 2, REEL_TOP, x + REEL_WIDTH, REEL_BOTTOM, 0x30000000);
        canvas.restore();
    }

    private void drawSprite(Canvas canvas, int symbol, float cx, float cy, float width, float height, int alpha) {
        spriteRect.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2);
        spritePaint.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawBitmap(sprites[symbol], null, spriteRect, spritePaint);
    }

    private void drawMessage(Canvas canvas, long now, boolean lights) {
        rect(canvas, 21, 227, 235, 253, SHADOW);
        rect(canvas, 22, 228, 234, 252, PURPLE);
        rect(canvas, 24, 230, 232, 250, DARK);
        int text = message;
        int color = lastWin > 0 ? GOLD : message == R.string.WhisperSlotEmpty ? PINK : MINT;
        boolean visible = true;
        if (isShowingWin(now)) {
            visible = !lights || ((now - winAt) / BLINK_MS) % 2 == 0;
        } else if (lights && !spinning && totalCoins() > 0 && (message == R.string.WhisperSlotReady
                || message == R.string.WhisperSlotMiss || lastWin > 0)
                && ((now - messageAt) / HINT_MS) % 2 == 1) {
            text = R.string.WhisperSlotPull;
            color = CREAM;
        }
        if (visible) title(canvas, getResources().getString(text), 128, 244, 11, color);
        // Dot-matrix grid over the text.
        for (float gx = 24; gx < 232; gx += 1.5f) {
            rect(canvas, gx + 1.1f, 230, gx + 1.5f, 250, 0xa00b0718);
        }
        for (float gy = 230; gy < 250; gy += 1.5f) {
            rect(canvas, 24, gy + 1.1f, 232, gy + 1.5f, 0xa00b0718);
        }
    }

    private void drawPaytable(Canvas canvas) {
        rect(canvas, 14, 303, 242, 323, INK);
        for (int i = 0; i < TRIPLE_PAYS.length; ++i) {
            float cx = 14 + 228 * (i + .5f) / TRIPLE_PAYS.length;
            drawSprite(canvas, i, cx - 9, 313, 10.8f, 10.8f, 255);
            title(canvas, PAY_LABELS[i], cx + 8, 316.5f, 8, GOLD);
        }
    }

    private void drawTray(Canvas canvas, long now) {
        rect(canvas, 34, TRAY_TOP - 2, 222, TRAY_FLOOR + 4, SHADOW);
        rect(canvas, 36, TRAY_TOP, 220, TRAY_FLOOR + 2, DARK);
        rect(canvas, 36, TRAY_TOP, 220, TRAY_TOP + 3, 0xff05030d);
        // Payout chute.
        rect(canvas, TRAY_CENTER - 9, TRAY_TOP, TRAY_CENTER + 9, TRAY_TOP + 4, 0xff000000);
        rect(canvas, TRAY_CENTER - 10, TRAY_TOP - 2, TRAY_CENTER + 10, TRAY_TOP, STEEL);
        for (int row = 0; row < PILE_ROWS.length; ++row) {
            for (int i = 0; i < pile; ++i) {
                if (SLOT_ROW[i] == row) drawPileCoin(canvas, SLOT_X[i] - 4, SLOT_Y[i]);
            }
        }
        for (int i = 0; i < drops; ++i) {
            // Coins tumble as they fly.
            float width = 1.5f + 4.5f * Math.abs((float) Math.cos(now / 45.0 + i * 1.7));
            float x = dropX[i];
            float y = dropY[i];
            rect(canvas, x - width / 2, y - 3, x + width / 2, y + 3, AMBER);
            rect(canvas, x - width / 2, y - 3, x + width / 2 - .5f, y + 2, GOLD);
            rect(canvas, x - width / 2 + .5f, y - 2.5f, x, y - 1, CREAM);
        }
        // Front lip of the tray.
        rect(canvas, 34, TRAY_FLOOR + 1, 222, TRAY_FLOOR + 5, STEEL);
        rect(canvas, 34, TRAY_FLOOR + 1, 222, TRAY_FLOOR + 2, CHROME);
    }

    private void drawPileCoin(Canvas canvas, float x, float y) {
        rect(canvas, x + 1, y, x + 7, y + 1, CREAM);
        rect(canvas, x, y + 1, x + 8, y + 3, GOLD);
        rect(canvas, x + 1, y + 3, x + 7, y + 4, AMBER);
    }

    private void drawLever(Canvas canvas) {
        float angle = lever.angle();
        float knobY = knobY();
        float depth = (float) Math.sin(angle);
        // Bracket bolted to the side of the cabinet.
        rect(canvas, 250, 145, 262, 201, SHADOW);
        rect(canvas, 251, 146, 260, 198, PURPLE);
        rect(canvas, 251, 146, 260, 148, LILAC);
        rect(canvas, 253, 151, 256, 154, STEEL);
        rect(canvas, 253, 190, 256, 193, STEEL);
        // Hub.
        circle(canvas, PIVOT_X + 1, PIVOT_Y + 1, 8, SHADOW);
        circle(canvas, PIVOT_X, PIVOT_Y, 8, STEEL);
        circle(canvas, PIVOT_X - 1, PIVOT_Y - 1, 6, CHROME);
        // Shaft: wider while it points at the viewer.
        float half = 2 + 1.2f * Math.max(0, depth);
        float shaftTop = Math.min(PIVOT_Y, knobY);
        float shaftBottom = Math.max(PIVOT_Y, knobY);
        rect(canvas, PIVOT_X - half, shaftTop, PIVOT_X + half, shaftBottom, STEEL);
        rect(canvas, PIVOT_X - half + .8f, shaftTop, PIVOT_X - half + 2, shaftBottom, CHROME);
        rect(canvas, PIVOT_X + half - 1, shaftTop, PIVOT_X + half, shaftBottom, 0xff6c6485);
        circle(canvas, PIVOT_X, PIVOT_Y, 3, 0xff6c6485);
        // Ball knob, larger as it swings toward the viewer.
        float radius = KNOB_RADIUS * (1 + .3f * depth);
        circle(canvas, PIVOT_X + 1, knobY + 1.5f, radius, SHADOW);
        circle(canvas, PIVOT_X, knobY, radius, KNOB_DARK);
        circle(canvas, PIVOT_X - radius * .12f, knobY - radius * .12f, radius * .85f, KNOB);
        circle(canvas, PIVOT_X - radius * .35f, knobY - radius * .35f, radius * .32f, 0xffff9bb1);
        rect(canvas, PIVOT_X - radius * .5f, knobY - radius * .55f, PIVOT_X - radius * .5f + 1.5f, knobY - radius * .55f + 1.5f, CREAM);
    }

    /** CRT tube: scanlines on the board's pixel rows and a darkened rim. */
    private void drawCrt(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float pitch = 2 * scale;
        float line = Math.max(1, .8f * scale);
        float y = top + scale;
        y -= (float) Math.ceil(y / pitch) * pitch;
        for (; y < height; y += pitch) {
            rect(canvas, 0, y, width, y + line, 0x1e000000);
        }
        float band = Math.min(width, height) * .025f;
        for (int i = 0; i < 5; ++i) {
            float inset = i * band;
            rect(canvas, inset, inset, width - inset, inset + band, 0x14000000);
            rect(canvas, inset, height - inset - band, width - inset, height - inset, 0x14000000);
            rect(canvas, inset, inset + band, inset + band, height - inset - band, 0x14000000);
            rect(canvas, width - inset - band, inset + band, width - inset, height - inset - band, 0x14000000);
        }
    }

    private void outline(Canvas canvas, float l, float t, float r, float b, float w, int color) {
        rect(canvas, l, t, r, t + w, color);
        rect(canvas, l, b - w, r, b, color);
        rect(canvas, l, t + w, l + w, b - w, color);
        rect(canvas, r - w, t + w, r, b - w, color);
    }

    /** A circle built from one-pixel rows, like a sprite. */
    private void circle(Canvas canvas, float cx, float cy, float radius, int color) {
        for (float dy = -radius; dy < radius; dy += 1) {
            float mid = Math.min(radius, Math.abs(dy + .5f));
            float half = (float) Math.sqrt(radius * radius - mid * mid);
            rect(canvas, cx - half, cy + dy, cx + half, cy + Math.min(radius, dy + 1), color);
        }
    }

    private void rect(Canvas canvas, float left, float top, float right, float bottom, int color) {
        blocks.setColor(color);
        canvas.drawRect(left, top, right, bottom, blocks);
    }

    private void title(Canvas canvas, String text, float x, float y, float size, int color) {
        letters.setColor(color);
        letters.setTextSize(size);
        canvas.drawText(text, x, y, letters);
    }

    // endregion

    /** Chunky push button that sinks into its socket while pressed. */
    private static final class ArcadeButton extends Button {
        float unit;

        ArcadeButton(Context context) {
            super(context);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            setTranslationY(isPressed() ? 3 * unit : 0);
        }
    }

    private static final class ArcadeButtonFace extends Drawable {
        private final Paint paint = new Paint();
        private boolean enabled = true;
        private boolean pressed;
        private boolean focused;
        float unit = 1;

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float l = bounds.left, t = bounds.top, r = bounds.right, b = bounds.bottom;
            float u = unit;
            int face = !enabled ? 0xff8e82a5 : pressed ? 0xfff0bb55 : GOLD;
            int light = !enabled ? 0xffb2a8c4 : CREAM;
            int dark = !enabled ? 0xff5f5476 : 0xffb5652f;
            fill(canvas, l, t, r, b, INK);
            fill(canvas, l + u, t + u, r - u, b - u, dark);
            fill(canvas, l + u, t + u, r - u, b - (pressed ? 1.5f : 4) * u, face);
            if (!pressed) {
                fill(canvas, l + 2 * u, t + u, r - 2 * u, t + 2.5f * u, light);
                fill(canvas, l + u, t + 2 * u, l + 2.5f * u, b - 5 * u, light);
            }
            if (focused) {
                fill(canvas, l + 3 * u, t + 3.5f * u, r - 3 * u, t + 4.5f * u, MINT);
                fill(canvas, l + 3 * u, b - 6 * u, r - 3 * u, b - 5 * u, MINT);
            }
        }

        private void fill(Canvas canvas, float l, float t, float r, float b, int color) {
            paint.setColor(color);
            canvas.drawRect(l, t, r, b, paint);
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean e = false, p = false, f = false;
            for (int s : state) {
                if (s == android.R.attr.state_enabled) e = true;
                else if (s == android.R.attr.state_pressed) p = true;
                else if (s == android.R.attr.state_focused) f = true;
            }
            if (e == enabled && p == pressed && f == focused) return false;
            enabled = e;
            pressed = p;
            focused = f;
            invalidateSelf();
            return true;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
