package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.R;

import java.util.Random;

/** Full-screen arcade cabinet shown while the local app lock is active. */
final class RetroSlotMachineView extends FrameLayout {
    private static final int BOARD_WIDTH = 256;
    private static final int BOARD_HEIGHT = 304;
    private static final int INITIAL_COINS = 20;
    private static final int REEL_INTERVAL_MS = 155;
    private static final int FIRST_STOP_MS = 540;
    private static final int SPIN_MS = FIRST_STOP_MS + 2 * REEL_INTERVAL_MS;

    private static final int NIGHT = 0xff100d25;
    private static final int INK = 0xff1c1538;
    private static final int PANEL = 0xff38244f;
    private static final int PURPLE = 0xff65427c;
    private static final int GOLD = 0xffffd36b;
    private static final int MINT = 0xff85f6cb;
    private static final int CREAM = 0xffffedce;
    private static final int PINK = 0xffff7db4;

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

    private final Paint blocks = new Paint();
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final LockScreenXGesture gesture = new LockScreenXGesture();
    private final Runnable unlock;
    private final Button spinButton;
    private final int touchSlop;
    private final int[] reels = {0, 1, 5};
    private final int[] rolling = new int[3];
    private final int[] result = new int[3];

    private ValueAnimator spin;
    private int coins = INITIAL_COINS;
    private int lastWin;
    private int message = R.string.WhisperSlotReady;
    private boolean active;
    private float downX, downY;
    private boolean dragging;
    private float scale, left, top;

    RetroSlotMachineView(Context context, Runnable unlock) {
        super(context);
        this.unlock = unlock;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        letters.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        letters.setTextAlign(Paint.Align.CENTER);
        setWillNotDraw(false);
        setBackgroundColor(NIGHT);
        setFilterTouchesWhenObscured(true);

        spinButton = new Button(context);
        spinButton.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        spinButton.setTextColor(INK);
        spinButton.setLetterSpacing(.1f);
        spinButton.setPadding(0, 0, 0, 0);
        spinButton.setStateListAnimator(null);
        spinButton.setBackground(buttonBackground());
        spinButton.setOnClickListener(v -> play());
        addView(spinButton, new LayoutParams(0, 0));
        updateDisplay();
    }

    private static StateListDrawable buttonBackground() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[] {-android.R.attr.state_enabled}, new ColorDrawable(0xff8e82a5));
        drawable.addState(new int[] {android.R.attr.state_pressed}, new ColorDrawable(CREAM));
        drawable.addState(new int[] {android.R.attr.state_focused}, new ColorDrawable(MINT));
        drawable.addState(new int[] {}, new ColorDrawable(GOLD));
        return drawable;
    }

    void startSession() {
        setActive(false);
        reels[0] = 0;
        reels[1] = 1;
        reels[2] = 5;
        coins = INITIAL_COINS;
        lastWin = 0;
        message = R.string.WhisperSlotReady;
        setActive(true);
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active) {
            gesture.reset();
            if (spin != null) spin.end();
        }
        updateDisplay();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) gesture.reset();
    }

    private void play() {
        if (!active || spin != null) return;
        gesture.reset();
        if (coins == 0) {
            startSession();
            return;
        }
        coins--;
        lastWin = 0;
        message = R.string.WhisperSlotRolling;
        for (int i = 0; i < result.length; ++i) {
            result[i] = random.nextInt(SPRITES.length);
            rolling[i] = reels[i];
        }
        spin = ValueAnimator.ofInt(0, SPIN_MS);
        spin.setDuration(SPIN_MS);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(animator -> {
            int time = (int) animator.getAnimatedValue();
            for (int i = 0; i < reels.length; ++i) {
                reels[i] = time >= FIRST_STOP_MS + i * REEL_INTERVAL_MS
                        ? result[i] : (rolling[i] + time / 65) % SPRITES.length;
            }
            invalidate();
        });
        spin.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                spin = null;
                finishSpin();
            }
        });
        updateDisplay();
        spin.start();
    }

    private void finishSpin() {
        System.arraycopy(result, 0, reels, 0, reels.length);
        if (reels[0] == reels[1] && reels[1] == reels[2]) {
            lastWin = reels[0] == 5 ? 25 : 10;
            message = R.string.WhisperSlotJackpot;
        } else if (reels[0] == reels[1] || reels[0] == reels[2] || reels[1] == reels[2]) {
            lastWin = 2;
            message = R.string.WhisperSlotPair;
        } else {
            message = coins == 0 ? R.string.WhisperSlotEmpty : R.string.WhisperSlotMiss;
        }
        coins += lastWin;
        updateDisplay();
        if (active && hasWindowFocus()) announceForAccessibility(displaySummary());
    }

    private String displaySummary() {
        return getResources().getString(R.string.WhisperSlotSummary, coins, lastWin, getResources().getString(message));
    }

    private void updateDisplay() {
        spinButton.setEnabled(active && spin == null);
        int label = coins == 0 && spin == null ? R.string.WhisperSlotNewGame : R.string.WhisperSlotSpin;
        spinButton.setText(label);
        spinButton.setContentDescription(getResources().getString(label) + ". " + displaySummary());
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!active || !onFilterTouchEventForSecurity(event)) {
            gesture.reset();
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
                float minimumSpan = Math.max(dp(24), Math.min(getWidth(), getHeight()) * .09f);
                completed = gesture.end(event.getX(), event.getY(), event.getEventTime(), minimumSpan);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                dragging = true;
                gesture.reset();
                break;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (completed > 0 && hasWindowFocus()) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (completed == 3) {
                setActive(false);
                unlock.run();
            }
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // A diagonal that starts over SPIN is a stroke, even if it ends on the button.
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

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
        spinButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17 * scale);
        spinButton.measure(MeasureSpec.makeMeasureSpec(Math.round(206 * scale), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(40 * scale), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = Math.round(left + 25 * scale);
        int y = Math.round(top + 235 * scale);
        spinButton.layout(x, y, x + spinButton.getMeasuredWidth(), y + spinButton.getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < 58; ++i) {
            float x = getWidth() * ((i * 83 + 13) % 151) / 151f;
            float y = getHeight() * ((i * 41 + 7) % 127) / 127f;
            float size = (i % 7 == 0 ? 3 : 2) * scale;
            rect(canvas, x, y, x + size, y + size, i % 7 == 0 ? PURPLE : PANEL);
        }
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        // Stepped cabinet, inset marquee, and coin door.
        rect(canvas, 12, 10, 250, 304, 0xff08061a);
        rect(canvas, 10, 0, 246, 297, PURPLE);
        rect(canvas, 4, 8, 252, 289, PURPLE);
        rect(canvas, 10, 8, 246, 289, PANEL);
        rect(canvas, 15, 13, 241, 70, PINK);
        rect(canvas, 18, 16, 238, 67, INK);
        title(canvas, "PIXEL SLOTS", 128, 43, 23, 0xff8f437d);
        title(canvas, "PIXEL SLOTS", 128, 41, 23, GOLD);
        title(canvas, getResources().getString(R.string.WhisperSlotArcade), 128, 58, 8, MINT);

        counter(canvas, 19, 121, R.string.WhisperSlotCoins, coins);
        counter(canvas, 135, 237, R.string.WhisperSlotWin, lastWin);
        rect(canvas, 13, 119, 243, 200, GOLD);
        rect(canvas, 16, 122, 240, 197, INK);
        for (int i = 0; i < reels.length; ++i) {
            int x = 23 + 72 * i;
            rect(canvas, x, 127, x + 66, 192, CREAM);
            rect(canvas, x, 127, x + 66, 132, 0xffbe90a7);
            rect(canvas, x, 187, x + 66, 192, 0xffd6acc0);
            drawSymbol(canvas, reels[i], x + 12.75f, 138);
        }
        rect(canvas, 16, 156, 22, 163, PINK);
        rect(canvas, 233, 156, 240, 163, PINK);
        title(canvas, getResources().getString(message), 128, 222, 11, lastWin > 0 ? GOLD : MINT);
        rect(canvas, 22, 237, 234, 280, INK);
        title(canvas, getResources().getString(R.string.WhisperSlotRules), 128, 289, 8, CREAM);
        canvas.restore();
    }

    private void counter(Canvas canvas, int left, int right, int label, int value) {
        rect(canvas, left, 81, right, 111, INK);
        float center = (left + right) / 2f;
        title(canvas, getResources().getString(label), center, 91, 8, MINT);
        title(canvas, Integer.toString(value), center, 106, 14, CREAM);
    }

    private void drawSymbol(Canvas canvas, int symbol, float left, float top) {
        float pixel = 4.5f;
        String[] sprite = SPRITES[symbol];
        int[] palette = PALETTES[symbol];
        for (int y = 0; y < sprite.length; ++y) {
            for (int x = 0; x < sprite[y].length(); ++x) {
                int color = sprite[y].charAt(x) - '1';
                if (color >= 0) {
                    rect(canvas, left + x * pixel, top + y * pixel,
                            left + (x + 1) * pixel, top + (y + 1) * pixel, palette[color]);
                }
            }
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
}
