package org.telegram.ui.Components;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.telegram.messenger.R;

import java.util.Random;

/**
 * Paints the vintage cabinet: chrome over a walnut body, a neon marquee, a bulb-lit reel
 * bezel, a paper pay card and a coin tray. Parts that never move are painted once into a
 * single bitmap; moving parts are small sprites. Coordinates are board units.
 */
final class RetroSlotArt {
    static final int BOARD_WIDTH = 304;
    static final int BOARD_HEIGHT = 400;
    private static final float FLOOR = BOARD_HEIGHT - 8;

    static final int CHERRY = 0, LEMON = 1, PLUM = 2, BELL = 3, BAR = 4, SEVEN = 5;
    static final int SYMBOL_COUNT = 6;
    // Three of a kind, by symbol.
    static final int[] TRIPLE_PAYS = {5, 8, 10, 12, 15, 30};
    static final int PAIR_PAY = 2;

    static final float REEL_LEFT = 44;
    static final float REEL_STEP = 64;
    static final float REEL_WIDTH = 52;
    static final float REEL_TOP = 117;
    static final float REEL_BOTTOM = 201;
    static final float PAYLINE = 159;
    static final float SYMBOL_SIZE = 36;
    static final float SYMBOL_PITCH = 40;
    static final float DRUM_RADIUS = 50;

    static final float METER_TOP = 228;
    static final float METER_BOTTOM = 256;
    static final float CREDITS_LEFT = 22;
    static final float PAID_LEFT = 180;
    static final float METER_WIDTH = 66;
    static final float MESSAGE_LEFT = 92;
    static final float MESSAGE_RIGHT = 176;

    static final float TRAY_CENTER = 134;
    static final float TRAY_TOP = 348;
    static final float TRAY_FLOOR = 367;

    static final float PIVOT_X = 279;
    static final float PIVOT_Y = 252;
    static final float LEVER_LENGTH = 122;
    // The handle leans out from the cabinet; the knob swings across it as it comes down.
    static final float LEVER_TILT = 11;
    static final float KNOB_RADIUS = 11.5f;
    static final float HUB_RADIUS = 8;

    static final float BEACON_X = 134;
    static final float BEACON_Y = 12.5f;
    static final float BEACON_RADIUS = 9;
    static final float BEACON_GLOW = 44;

    static final float BULB_RADIUS = 3.4f;
    static final float BULB_GLOW = 9;
    static final int[] BULB_COLORS = {0xffff3b30, 0xff34c759, 0xffffd60a, 0xff2f7cf6, 0xffff8a00};
    static final int BULB_COUNT = 22;
    static final float[] BULB_X = new float[BULB_COUNT];
    static final float[] BULB_Y = new float[BULB_COUNT];

    static final float COIN_WIDTH = 10;
    static final float COIN_HEIGHT = 4.5f;

    static final int CREAM = 0xfff6efdf;
    static final int RED = 0xffb3261e;
    static final int AMBER = 0xffffb347;

    private static final int[] CHROME = {0xff4b4f57, 0xffd9dde3, 0xfff7f9fb, 0xff9ea4ad, 0xffe3e6ea, 0xff6d727b};
    private static final float[] CHROME_STOPS = {0, .18f, .32f, .55f, .8f, 1};
    private static final int[] WALNUT = {0xff2a170d, 0xff4a2c1a, 0xff3a2213, 0xff52321e, 0xff2c180e};
    private static final int[] WALNUT_DARK = {0xff1f1109, 0xff3a2214, 0xff2c190e, 0xff3f2616, 0xff21120a};

    static final Typeface SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD);
    static final Typeface SERIF_ITALIC = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC);
    static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    static {
        // Clockwise around the reels, from the top left.
        int b = 0;
        for (int i = 0; i < 9; ++i, ++b) {
            BULB_X[b] = 40 + 23.5f * i;
            BULB_Y[b] = 108;
        }
        BULB_X[b] = 235;
        BULB_Y[b++] = 132;
        BULB_X[b] = 235;
        BULB_Y[b++] = 190;
        for (int i = 8; i >= 0; --i, ++b) {
            BULB_X[b] = 40 + 23.5f * i;
            BULB_Y[b] = 214;
        }
        BULB_X[b] = 33;
        BULB_Y[b++] = 190;
        BULB_X[b] = 33;
        BULB_Y[b] = 132;
    }

    private RetroSlotArt() {
    }

    // region Cabinet

    /** Everything that never moves, painted at {@code scale} pixels per board unit. */
    static Bitmap paintCabinet(Resources resources, float scale) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int) Math.ceil(BOARD_WIDTH * scale)),
                Math.max(1, (int) Math.ceil(BOARD_HEIGHT * scale)), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.scale(scale, scale);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        RectF r = new RectF();

        // Shadow on the floor.
        c.save();
        c.scale(1, .1f, 134, FLOOR);
        p.setShader(new RadialGradient(134, FLOOR, 140, 0x99000000, 0, Shader.TileMode.CLAMP));
        c.drawCircle(134, FLOOR, 140, p);
        p.setShader(null);
        c.restore();

        // Walnut body behind the chrome front.
        r.set(2, 28, 266, FLOOR);
        wood(c, p, path, r, 4, WALNUT, 11);
        r.set(12, 18, 256, FLOOR - 6);
        chromeFrame(c, p, path, r, 8, 10);
        r.set(20, 24, 248, FLOOR - 12);
        wood(c, p, path, r, 6, WALNUT_DARK, 23);
        stroke(c, p, r, 6, 1.2f, 0x66000000);

        // Beacon base.
        r.set(121, 12, 147, 22);
        chrome(c, p, r, 2, true);
        fill(c, p, 121, 19, 147, 22, 0x55000000);

        paintMarquee(c, p, path, r);
        r.set(18, 92, 250, 97);
        chrome(c, p, r, 1, true);
        paintBezel(c, p, path, r);
        paintMeters(c, p, r, resources);
        paintPayCard(c, p, path, r, resources);

        // Chrome trim under the pay card.
        r.set(18, 332, 250, 337);
        chrome(c, p, r, 1, true);

        // Coin tray.
        r.set(72, TRAY_TOP - 6, 196, TRAY_FLOOR + 11);
        chromeFrame(c, p, path, r, 4, 6);
        r.set(76, TRAY_TOP - 2, 192, TRAY_FLOOR + 7);
        p.setColor(0xff050506);
        c.drawRoundRect(r, 3, 3, p);
        r.set(80, TRAY_TOP, 188, TRAY_FLOOR + 3);
        p.setShader(new LinearGradient(0, TRAY_TOP, 0, TRAY_FLOOR + 3, new int[] {0xff000000, 0xff0c0c0e, 0xff24262b},
                new float[] {0, .55f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 3, 3, p);
        p.setShader(null);
        // Payout chute in the back wall.
        r.set(TRAY_CENTER - 12, TRAY_TOP - .5f, TRAY_CENTER + 12, TRAY_TOP + 4);
        chrome(c, p, r, 1.5f, true);
        fill(c, p, TRAY_CENTER - 10, TRAY_TOP + 1, TRAY_CENTER + 10, TRAY_TOP + 3.5f, 0xff000000);

        // Bracket for the lever.
        r.set(255, PIVOT_Y - 30, 273, PIVOT_Y + 30);
        p.setColor(0x66000000);
        c.drawRoundRect(r.left + 1, r.top + 2, r.right + 1, r.bottom + 2, 5, 5, p);
        chrome(c, p, r, 5, false);
        stroke(c, p, r, 5, .7f, 0x88000000);
        for (float y : new float[] {PIVOT_Y - 23, PIVOT_Y + 23}) {
            p.setShader(new RadialGradient(262.5f, y - .6f, 2.4f, 0xfff7f9fb, 0xff5b6068, Shader.TileMode.CLAMP));
            c.drawCircle(263.5f, y, 2, p);
            p.setShader(null);
        }
        return bitmap;
    }

    private static void paintMarquee(Canvas c, Paint p, Path path, RectF r) {
        r.set(20, 24, 248, 92);
        chromeFrame(c, p, path, r, 5, 6);
        r.set(25, 29, 243, 87);
        p.setShader(new LinearGradient(0, 29, 0, 87, new int[] {0xff0b0e18, 0xff171b2b, 0xff0a0c15},
                new float[] {0, .55f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 4, 4, p);
        p.setShader(null);

        // Neon tube border.
        r.set(30, 34, 238, 82);
        int pink = 0xffff3fd0;
        neonStroke(c, p, r, 7, pink, 0xffffd6f7);

        // Lettering: gold to orange, outlined, with an orange halo.
        p.setTypeface(SERIF);
        p.setTextAlign(Paint.Align.CENTER);
        signText(c, p, "JACKPOT", 134, 59.5f, 26, 40);
        signText(c, p, "SLOTS", 134, 77.5f, 17, 65);

        // Neon lines beside SLOTS, clear of the letters.
        p.setTextSize(17);
        float gap = p.measureText("SLOTS") / 2 + 8;
        neonLine(c, p, 44, 69.5f, 134 - gap, 69.5f, 0xff48e1ff);
        neonLine(c, p, 134 + gap, 69.5f, 224, 69.5f, 0xff48e1ff);
        neonLine(c, p, 54, 73.5f, 134 - gap, 73.5f, 0xffffc94d);
        neonLine(c, p, 134 + gap, 73.5f, 214, 73.5f, 0xffffc94d);

        // Reflection on the glass.
        Path glare = path;
        glare.reset();
        glare.moveTo(60, 29);
        glare.lineTo(104, 29);
        glare.lineTo(70, 87);
        glare.lineTo(26, 87);
        glare.close();
        p.setColor(0x10ffffff);
        c.drawPath(glare, p);
    }

    private static void signText(Canvas c, Paint p, String text, float x, float y, float size, float top) {
        p.setTextSize(size);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(7);
        p.setColor(0x22ff7a1a);
        c.drawText(text, x, y, p);
        p.setStrokeWidth(4);
        p.setColor(0x40ff7a1a);
        c.drawText(text, x, y, p);
        p.setStrokeWidth(2.2f);
        p.setColor(0xff3a0e00);
        c.drawText(text, x, y, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, top, 0, y, new int[] {0xfffff6c2, 0xffffc44d, 0xffff7b22, 0xffe2401b},
                new float[] {0, .45f, .75f, 1}, Shader.TileMode.CLAMP));
        c.drawText(text, x, y, p);
        p.setShader(null);
    }

    private static void neonStroke(Canvas c, Paint p, RectF r, float radius, int color, int core) {
        p.setStyle(Paint.Style.STROKE);
        float[] widths = {7, 4.5f, 2.6f};
        int[] alphas = {0x22, 0x44, 0x99};
        for (int i = 0; i < widths.length; ++i) {
            p.setStrokeWidth(widths[i]);
            p.setColor(alphas[i] << 24 | color & 0xffffff);
            c.drawRoundRect(r, radius, radius, p);
        }
        p.setStrokeWidth(1.2f);
        p.setColor(core);
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void neonLine(Canvas c, Paint p, float x0, float y0, float x1, float y1, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        float[] widths = {5, 3, 1.6f};
        int[] alphas = {0x26, 0x55, 0xcc};
        for (int i = 0; i < widths.length; ++i) {
            p.setStrokeWidth(widths[i]);
            p.setColor(alphas[i] << 24 | color & 0xffffff);
            c.drawLine(x0, y0, x1, y1, p);
        }
        p.setStrokeWidth(.7f);
        p.setColor(0xffffffff);
        c.drawLine(x0, y0, x1, y1, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    private static void paintBezel(Canvas c, Paint p, Path path, RectF r) {
        r.set(22, 99, 246, 223);
        chromeFrame(c, p, path, r, 4, 8);
        r.set(26, 103, 242, 219);
        p.setShader(new LinearGradient(0, 103, 0, 219, 0xff1a1b21, 0xff2b2d35, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 6, 6, p);
        p.setShader(null);
        stroke(c, p, r, 6, 1, 0x99000000);

        for (int i = 0; i < BULB_COUNT; ++i) {
            float x = BULB_X[i], y = BULB_Y[i];
            p.setShader(new RadialGradient(x - 1, y - 1, 5.5f, 0xfff7f9fb, 0xff50555d, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, 4.7f, p);
            p.setShader(null);
            p.setColor(0xff101014);
            c.drawCircle(x, y, 3.7f, p);
        }
        for (int i = 0; i < 3; ++i) {
            float x = REEL_LEFT + REEL_STEP * i;
            r.set(x - 3.5f, REEL_TOP - 3.5f, x + REEL_WIDTH + 3.5f, REEL_BOTTOM + 3.5f);
            chrome(c, p, r, 4, false);
            stroke(c, p, r, 4, .7f, 0xaa000000);
            fill(c, p, x - 1, REEL_TOP - 1, x + REEL_WIDTH + 1, REEL_BOTTOM + 1, 0xff0d0d10);
        }
        // Payline pointers.
        p.setColor(0xffd7261e);
        path.reset();
        path.moveTo(37.5f, PAYLINE - 4);
        path.lineTo(42, PAYLINE);
        path.lineTo(37.5f, PAYLINE + 4);
        path.close();
        path.moveTo(230.5f, PAYLINE - 4);
        path.lineTo(226, PAYLINE);
        path.lineTo(230.5f, PAYLINE + 4);
        path.close();
        c.drawPath(path, p);
    }

    private static void paintMeters(Canvas c, Paint p, RectF r, Resources resources) {
        meterBox(c, p, r, CREDITS_LEFT, CREDITS_LEFT + METER_WIDTH, resources.getString(R.string.WhisperSlotCoins));
        meterBox(c, p, r, PAID_LEFT, PAID_LEFT + METER_WIDTH, resources.getString(R.string.WhisperSlotWin));
        r.set(MESSAGE_LEFT, METER_TOP, MESSAGE_RIGHT, METER_BOTTOM);
        chrome(c, p, r, 3, true);
        r.inset(2.5f, 2.5f);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, new int[] {0xff120a04, 0xff2a1707, 0xff120a04},
                new float[] {0, .5f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 2, 2, p);
        p.setShader(null);
    }

    private static void meterBox(Canvas c, Paint p, RectF r, float left, float right, String label) {
        r.set(left, METER_TOP, right, METER_BOTTOM);
        chrome(c, p, r, 3, true);
        r.inset(2.5f, 2.5f);
        p.setColor(0xff08080a);
        c.drawRoundRect(r, 2, 2, p);
        p.setTypeface(CONDENSED);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(6);
        p.setColor(0xffe8dcc0);
        c.drawText(label, (left + right) / 2, METER_TOP + 8.5f, p);
    }

    private static void paintPayCard(Canvas c, Paint p, Path path, RectF r, Resources resources) {
        // Aged paper.
        r.set(22, 260, 246, 330);
        p.setShader(new LinearGradient(0, 260, 0, 330, 0xfff4e8c8, 0xffe3cf9c, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 3, 3, p);
        float[][] stains = {{58, 272, 20}, {206, 322, 24}, {140, 306, 13}, {30, 322, 10}, {176, 266, 9}};
        for (float[] stain : stains) {
            p.setShader(new RadialGradient(stain[0], stain[1], stain[2], 0x2e8b5a2b, 0, Shader.TileMode.CLAMP));
            c.drawCircle(stain[0], stain[1], stain[2], p);
        }
        p.setShader(null);
        stroke(c, p, r, 3, 1.4f, 0xff6d4c1f);
        r.inset(3, 3);
        stroke(c, p, r, 2, .6f, 0xff9c7a42);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(SERIF);
        p.setTextSize(10.5f);
        p.setColor(RED);
        c.drawText(resources.getString(R.string.WhisperSlotPaytable), 112, 273, p);

        int[][] columns = {{SEVEN, BAR, BELL}, {PLUM, LEMON, CHERRY}};
        for (int col = 0; col < 2; ++col) {
            float x = col == 0 ? 30 : 114;
            for (int row = 0; row < 3; ++row) {
                int symbol = columns[col][row];
                float y = 286 + 14 * row;
                for (int k = 0; k < 3; ++k) {
                    paintSymbol(c, symbol, x + 11.5f * k, y - 5.5f, 11, p, path);
                }
                p.setColor(0xff7a6038);
                for (float dot = x + 38; dot < x + 62; dot += 3.2f) {
                    c.drawCircle(dot, y + 1.5f, .6f, p);
                }
                p.setTypeface(SERIF);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTextSize(9);
                p.setColor(0xff3a2a14);
                c.drawText(Integer.toString(TRIPLE_PAYS[symbol]), x + 80, y + 3.5f, p);
            }
        }

        // Badge, like the coin price card on the original.
        r.set(202, 268, 242, 322);
        p.setColor(0xfffbf3dc);
        c.drawRoundRect(r, 3, 3, p);
        stroke(c, p, r, 3, 1.6f, RED);
        r.inset(2.5f, 2.5f);
        stroke(c, p, r, 2, .5f, RED);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(CONDENSED);
        p.setTextSize(6.5f);
        p.setColor(0xff3a2a14);
        c.drawText(resources.getString(R.string.WhisperSlotRules), 222, 280, p);
        p.setTypeface(SERIF);
        p.setTextSize(28);
        p.setColor(RED);
        c.drawText(Integer.toString(PAIR_PAY), 222, 311, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    // endregion

    // region Symbols

    /** One reel symbol, as a bitmap {@code size} pixels square. */
    static Bitmap paintSymbol(int symbol, int size) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, size), Math.max(1, size), Bitmap.Config.ARGB_8888);
        paintSymbol(new Canvas(bitmap), symbol, 0, 0, size, new Paint(Paint.ANTI_ALIAS_FLAG), new Path());
        return bitmap;
    }

    /** Draws a symbol into the square at ({@code x}, {@code y}) with side {@code size}. */
    static void paintSymbol(Canvas c, int symbol, float x, float y, float size, Paint p, Path path) {
        c.save();
        c.translate(x, y);
        c.scale(size / 100f, size / 100f);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        switch (symbol) {
            case CHERRY: cherries(c, p, path); break;
            case LEMON: lemon(c, p, path); break;
            case PLUM: plum(c, p, path); break;
            case BELL: bell(c, p, path); break;
            case BAR: bar(c, p); break;
            default: seven(c, p); break;
        }
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private static void cherries(Canvas c, Paint p, Path path) {
        path.reset();
        path.moveTo(31, 56);
        path.quadTo(44, 34, 64, 15);
        path.moveTo(69, 50);
        path.quadTo(64, 30, 64, 15);
        line(c, p, path, 7, 0xff1f4a14);
        line(c, p, path, 4, 0xff4f9a2f);
        leaf(c, p, path, 64, 16, 92, 10);
        ball(c, p, 32, 70, 19, 0xffff8a80, 0xffe0242f, 0xff8e0d16, 0xff5a0a0f);
        ball(c, p, 69, 64, 19, 0xffff8a80, 0xffe0242f, 0xff8e0d16, 0xff5a0a0f);
    }

    private static void lemon(Canvas c, Paint p, Path path) {
        c.save();
        c.rotate(-14, 50, 58);
        RectF oval = new RectF(12, 32, 88, 84);
        p.setShader(new RadialGradient(38, 46, 52, new int[] {0xfffffbd0, 0xfffde24a, 0xffe0b000, 0xff9c7400},
                new float[] {0, .35f, .75f, 1}, Shader.TileMode.CLAMP));
        c.drawOval(oval, p);
        c.drawCircle(10, 58, 6, p);
        c.drawCircle(90, 58, 5, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0xff7a5a00);
        c.drawOval(oval, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xaaffffff);
        c.drawOval(new RectF(28, 40, 48, 49), p);
        c.restore();
        leaf(c, p, path, 56, 30, 80, 18);
    }

    private static void plum(Canvas c, Paint p, Path path) {
        path.reset();
        path.moveTo(50, 30);
        path.quadTo(52, 20, 56, 12);
        line(c, p, path, 5, 0xff4e342e);
        leaf(c, p, path, 55, 18, 82, 12);
        ball(c, p, 50, 60, 31, 0xffd9b3ff, 0xff7b2cbf, 0xff3c096c, 0xff240046);
        path.reset();
        path.moveTo(50, 32);
        path.quadTo(38, 58, 52, 89);
        line(c, p, path, 3, 0x553c096c);
    }

    private static void bell(Canvas c, Paint p, Path path) {
        LinearGradient gold = new LinearGradient(14, 0, 86, 0,
                new int[] {0xff8a5a00, 0xffffe680, 0xfff2b705, 0xffb07a00, 0xff6b4400},
                new float[] {0, .32f, .55f, .8f, 1}, Shader.TileMode.CLAMP);
        ball(c, p, 50, 15, 7, 0xfffff3b0, 0xfff2b705, 0xff8a5a00, 0xff5c3d00);
        path.reset();
        path.moveTo(20, 76);
        path.cubicTo(26, 62, 22, 22, 50, 20);
        path.cubicTo(78, 22, 74, 62, 80, 76);
        path.lineTo(88, 82);
        path.lineTo(12, 82);
        path.close();
        p.setShader(gold);
        c.drawPath(path, p);
        RectF rim = new RectF(9, 78, 91, 88);
        c.drawRoundRect(rim, 5, 5, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0xff5c3d00);
        c.drawPath(path, p);
        c.drawRoundRect(rim, 5, 5, p);
        p.setStyle(Paint.Style.FILL);
        ball(c, p, 50, 92, 7, 0xfffff3b0, 0xfff2b705, 0xff8a5a00, 0xff5c3d00);
        path.reset();
        path.moveTo(33, 70);
        path.quadTo(31, 38, 45, 29);
        line(c, p, path, 4.5f, 0x99ffffff);
    }

    private static void bar(Canvas c, Paint p) {
        RectF r = new RectF(8, 33, 92, 67);
        p.setShader(new LinearGradient(0, 33, 0, 67, 0xff3a3a3a, 0xff050505, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 6, 6, p);
        p.setShader(null);
        r.inset(3.5f, 3.5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        p.setColor(0xfff5ecd0);
        c.drawRoundRect(r, 4, 4, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(SERIF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(25);
        c.drawText("BAR", 50, 59, p);
    }

    private static void seven(Canvas c, Paint p) {
        p.setTypeface(SERIF_ITALIC);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(104);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(9);
        p.setColor(0xff4a0000);
        c.drawText("7", 51, 88, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 18, 0, 88, new int[] {0xffff7b6b, 0xffe01b1b, 0xffa30000},
                new float[] {0, .5f, 1}, Shader.TileMode.CLAMP));
        c.drawText("7", 51, 88, p);
        p.setShader(null);
    }

    private static void ball(Canvas c, Paint p, float x, float y, float r, int light, int mid, int dark, int edge) {
        p.setShader(new RadialGradient(x - r * .35f, y - r * .4f, r * 1.35f, new int[] {light, mid, dark},
                new float[] {0, .45f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, r * .1f));
        p.setColor(edge);
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xccffffff);
        c.drawOval(new RectF(x - r * .55f, y - r * .6f, x - r * .1f, y - r * .3f), p);
    }

    private static void leaf(Canvas c, Paint p, Path path, float x0, float y0, float x1, float y1) {
        path.reset();
        path.moveTo(x0, y0);
        path.quadTo((x0 + x1) / 2, y0 - 16, x1, y1);
        path.quadTo((x0 + x1) / 2 + 2, y1 + 12, x0, y0);
        path.close();
        p.setShader(new LinearGradient(x0, y0 - 10, x1, y1 + 10, 0xff8fd14f, 0xff2e7d32, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0xff1b5e20);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void line(Canvas c, Paint p, Path path, float width, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawPath(path, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    // endregion

    // region Sprites

    /** A bulb {@code 2 * BULB_GLOW} units across, lit with its halo or dark. */
    static Bitmap paintBulb(int color, boolean lit, float scale) {
        int size = Math.max(1, Math.round(2 * BULB_GLOW * scale));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.scale(size / (2 * BULB_GLOW), size / (2 * BULB_GLOW));
        c.translate(BULB_GLOW, BULB_GLOW);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int rgb = color & 0xffffff;
        if (lit) {
            p.setShader(new RadialGradient(0, 0, BULB_GLOW, new int[] {0xaa000000 | rgb, 0x44000000 | rgb, 0},
                    new float[] {0, .45f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(0, 0, BULB_GLOW, p);
            p.setShader(new RadialGradient(-.8f, -.8f, BULB_RADIUS * 1.3f,
                    new int[] {0xffffffff, mix(color, 0xffffffff, .45f), color, mix(color, 0xff000000, .35f)},
                    new float[] {0, .3f, .75f, 1}, Shader.TileMode.CLAMP));
        } else {
            p.setShader(new RadialGradient(-.8f, -.8f, BULB_RADIUS * 1.3f,
                    new int[] {mix(color, 0xff000000, .45f), mix(color, 0xff000000, .7f), mix(color, 0xff000000, .85f)},
                    new float[] {0, .6f, 1}, Shader.TileMode.CLAMP));
        }
        c.drawCircle(0, 0, BULB_RADIUS, p);
        p.setShader(null);
        p.setColor(lit ? 0xeeffffff : 0x66ffffff);
        c.drawCircle(-1.2f, -1.2f, .8f, p);
        return bitmap;
    }

    /** The red dome on top, as a half-disc above its base line. */
    static Bitmap paintBeacon(boolean lit, float scale) {
        float w = 2 * BEACON_RADIUS + 2, h = BEACON_RADIUS + 1;
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.scale(scale, scale);
        c.translate(w / 2, h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF dome = new RectF(-BEACON_RADIUS, -BEACON_RADIUS, BEACON_RADIUS, BEACON_RADIUS);
        int[] colors = lit ? new int[] {0xffffe0d8, 0xffff4a3a, 0xffb3120c} : new int[] {0xffc0574f, 0xff7a1410, 0xff3a0705};
        p.setShader(new RadialGradient(-3, -6, BEACON_RADIUS * 1.4f, colors, new float[] {0, .5f, 1}, Shader.TileMode.CLAMP));
        c.drawArc(dome, 180, 180, true, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.6f);
        p.setColor(0x33000000);
        for (float x = -6; x <= 6; x += 3) {
            c.drawLine(x, -(float) Math.sqrt(BEACON_RADIUS * BEACON_RADIUS - x * x) + .5f, x, 0, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x99ffffff);
        c.drawOval(new RectF(-5.5f, -7.5f, -1.5f, -5), p);
        return bitmap;
    }

    /** Soft round light, {@code 2 * radius} units across, in {@code color}. */
    static Bitmap paintGlow(int color, float radius, float scale) {
        int size = Math.max(1, Math.round(2 * radius * scale));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int rgb = color & 0xffffff;
        p.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f, new int[] {0x88000000 | rgb, 0x30000000 | rgb, 0},
                new float[] {0, .35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return bitmap;
    }

    /** A glossy sphere of {@code radius} units: the lever knob or the chrome hub. */
    static Bitmap paintSphere(float radius, boolean knob, float scale) {
        int size = Math.max(1, Math.round(2 * radius * scale));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        float r = size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] colors = knob ? new int[] {0xffffd6d2, 0xffff5a4f, 0xffd61f1f, 0xff6e0505}
                : new int[] {0xffffffff, 0xffc9ced6, 0xff7d838d, 0xff3e4249};
        p.setShader(new RadialGradient(r * .65f, r * .6f, r * 1.35f, colors, new float[] {0, .22f, .6f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r, p);
        p.setShader(null);
        p.setColor(0xddffffff);
        c.drawOval(new RectF(r * .45f, r * .35f, r * .85f, r * .6f), p);
        return bitmap;
    }

    /** A silver coin lying in the tray, seen from the front. */
    static Bitmap paintCoin(float scale) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(COIN_WIDTH * scale)), Math.max(1, Math.round(COIN_HEIGHT * scale)), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.scale(scale, scale);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF(0, 1, COIN_WIDTH, COIN_HEIGHT);
        p.setColor(0xff6b717b);
        c.drawOval(r, p);
        r.set(0, 0, COIN_WIDTH, COIN_HEIGHT - 1);
        p.setShader(new LinearGradient(0, 0, COIN_WIDTH, 0, new int[] {0xff9aa1ab, 0xfff4f6f9, 0xffc3c9d1, 0xff8b929c},
                new float[] {0, .35f, .7f, 1}, Shader.TileMode.CLAMP));
        c.drawOval(r, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.4f);
        p.setColor(0x885a6069);
        r.inset(1.4f, .6f);
        c.drawOval(r, p);
        return bitmap;
    }

    // endregion

    // region Helpers

    private static void chrome(Canvas c, Paint p, RectF r, float radius, boolean vertical) {
        p.setShader(vertical
                ? new LinearGradient(0, r.top, 0, r.bottom, CHROME, CHROME_STOPS, Shader.TileMode.CLAMP)
                : new LinearGradient(r.left, 0, r.right, 0, CHROME, CHROME_STOPS, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);
    }

    /** A polished frame: each side is shaded across its own thickness, so all four read as round bars. */
    private static void chromeFrame(Canvas c, Paint p, Path path, RectF r, float t, float radius) {
        c.save();
        path.reset();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.clipPath(path);
        p.setShader(new LinearGradient(0, r.top, 0, r.top + t, CHROME, CHROME_STOPS, Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.top, r.right, r.top + t, p);
        p.setShader(new LinearGradient(0, r.bottom - t, 0, r.bottom, CHROME, CHROME_STOPS, Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.bottom - t, r.right, r.bottom, p);
        // Mitred sides.
        path.reset();
        path.moveTo(r.left, r.top);
        path.lineTo(r.left + t, r.top + t);
        path.lineTo(r.left + t, r.bottom - t);
        path.lineTo(r.left, r.bottom);
        path.close();
        p.setShader(new LinearGradient(r.left, 0, r.left + t, 0, CHROME, CHROME_STOPS, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        path.reset();
        path.moveTo(r.right, r.top);
        path.lineTo(r.right - t, r.top + t);
        path.lineTo(r.right - t, r.bottom - t);
        path.lineTo(r.right, r.bottom);
        path.close();
        p.setShader(new LinearGradient(r.right - t, 0, r.right, 0, CHROME, CHROME_STOPS, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        c.restore();
        stroke(c, p, r, radius, .8f, 0x99000000);
        RectF inner = new RectF(r.left + t, r.top + t, r.right - t, r.bottom - t);
        stroke(c, p, inner, Math.max(1, radius - t), .8f, 0xaa000000);
    }

    private static void wood(Canvas c, Paint p, Path path, RectF r, float radius, int[] colors, long seed) {
        p.setShader(new LinearGradient(r.left, 0, r.right, 0, colors, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);
        c.save();
        path.reset();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.clipPath(path);
        Random random = new Random(seed);
        p.setStyle(Paint.Style.STROKE);
        for (float x = r.left; x < r.right; x += 1.6f + random.nextFloat() * 1.8f) {
            path.reset();
            float phase = random.nextFloat() * 6.28f;
            float amp = .6f + random.nextFloat() * 1.6f;
            float drift = (random.nextFloat() - .5f) * 6;
            path.moveTo(x, r.top);
            for (float y = r.top; y <= r.bottom; y += 6) {
                float u = (y - r.top) / (r.bottom - r.top);
                path.lineTo(x + drift * u + amp * (float) Math.sin(y * .035f + phase), y);
            }
            p.setStrokeWidth(.4f + random.nextFloat() * .8f);
            p.setColor(random.nextInt(3) == 0 ? 0x10ffd9a0 : 0x22000000);
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, new int[] {0x18ffffff, 0, 0x30000000},
                new float[] {0, .4f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(r, p);
        p.setShader(null);
        c.restore();
    }

    private static void stroke(Canvas c, Paint p, RectF r, float radius, float width, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void fill(Canvas c, Paint p, float l, float t, float r, float b, int color) {
        p.setColor(color);
        c.drawRect(l, t, r, b, p);
    }

    static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xff, ag = a >> 8 & 0xff, ab = a & 0xff;
        int br = b >> 16 & 0xff, bg = b >> 8 & 0xff, bb = b & 0xff;
        return 0xff000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    // endregion
}
