package nir.com.blackjack21;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Blackjack 21 - free social blackjack. Virtual coins only, no real money, no prizes.
 *  Casino-table redesign: felt, animated cards, chip betting, win celebrations. */
public class BlackjackView extends View {
    public interface Listener {
        void onOutOfCoins();
        void onSpinComplete(int handsDone, long profit, long bet);
        void onBigWin(long win, long bet);
        void onLevelUp(int level, long reward);
        void saveState();
    }

    private static final int[] BETS = {5, 10, 25, 50};
    private static final int[] CHIP_COLORS = {0xFFC62828, 0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A};
    private static final String[] SUITS = {"\u2660", "\u2665", "\u2666", "\u2663"};
    private static final String[] RANKS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private long balance = 500;
    private long shownBalance = 500;
    private long lastWin = 0;
    private int betIdx = 1;
    private long handsDone = 0;
    private long lastRegenAt = System.currentTimeMillis();
    private long xp = 0;
    private int level = 1;

    // phases: 0=betting, 1=player turn, 2=dealer turn, 3=result
    private int phase = 0;
    private final List<Integer> player = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private final List<Long> playerCardT = new ArrayList<>();
    private final List<Long> dealerCardT = new ArrayList<>();
    private boolean doubled = false;
    private String message = "Place your bet";
    private long dealerNextAt = 0;
    private long flipStartAt = 0;

    // celebration state
    private int resultKind = -1; // -1 none, 0 lose, 1 win, 2 push, 3 blackjack
    private long resultAt = 0;
    private long resultAmount = 0;
    private final float[] px = new float[18], py = new float[18], pvx = new float[18], pvy = new float[18];
    private boolean particlesOn = false;
    private int levelUpPending = 0;      // level number waiting for celebration
    private long levelUpReward = 0;
    private boolean levelUpShowing = false;
    private long levelUpAt = 0;
    private int pressedBtn = -1;         // 0..n index into current buttons
    private long pressedAt = 0;

    public BlackjackView(Context c) { super(c); }

    public void setListener(Listener l) { listener = l; }

    public void addCoins(long n) { balance += n; invalidate(); if (listener != null) listener.saveState(); }

    public int getLevel() { return level; }
    public long getSpinsDone() { return handsDone; }
    private long xpNeeded() { return level * 500L; }

    public String serialize() { return balance + ";" + betIdx + ";" + handsDone + ";" + lastRegenAt + ";" + xp + ";" + level; }

    public void restore(String s) {
        try {
            String[] a = s.split(";");
            balance = Long.parseLong(a[0]);
            shownBalance = balance;
            betIdx = Integer.parseInt(a[1]);
            handsDone = Long.parseLong(a[2]);
            lastRegenAt = Long.parseLong(a[3]);
            if (a.length > 4) xp = Long.parseLong(a[4]);
            if (a.length > 5) level = Math.max(1, Integer.parseInt(a[5]));
        } catch (Exception ignored) {}
    }

    private void regen() {
        long now = System.currentTimeMillis();
        long ticks = (now - lastRegenAt) / 60000;
        if (ticks > 0) { lastRegenAt += ticks * 60000; if (balance < 100) balance = Math.min(100, balance + ticks * 5); }
    }

    private int draw() { return rng.nextInt(13); }
    private int val(int rankIdx) { return rankIdx == 0 ? 11 : Math.min(10, rankIdx + 1); }

    private int score(List<Integer> hand) {
        int s = 0, aces = 0;
        for (int r : hand) { s += val(r); if (r == 0) aces++; }
        while (s > 21 && aces > 0) { s -= 10; aces--; }
        return s;
    }

    private void deal() {
        long bet = BETS[betIdx];
        if (balance < bet) { if (listener != null) listener.onOutOfCoins(); return; }
        balance -= bet;
        xp += BETS[betIdx];
        doubled = false;
        resultKind = -1; particlesOn = false; levelUpShowing = false;
        player.clear(); dealer.clear(); playerCardT.clear(); dealerCardT.clear();
        long now = System.currentTimeMillis();
        player.add(draw()); dealer.add(draw()); player.add(draw()); dealer.add(draw());
        playerCardT.add(now); dealerCardT.add(now + 130);
        playerCardT.add(now + 260); dealerCardT.add(now + 390);
        message = "Your move";
        phase = 1;
        if (score(player) == 21) finishHand();
        invalidate(); if (listener != null) listener.saveState();
    }

    private void hit() {
        player.add(draw());
        playerCardT.add(System.currentTimeMillis());
        if (score(player) > 21) finishHand();
        else if (doubled) stand();
        invalidate();
    }

    private void stand() {
        phase = 2;
        flipStartAt = System.currentTimeMillis();
        dealerNextAt = flipStartAt + 700;
        message = "Dealer plays";
        invalidate();
    }

    private void doubleDown() {
        long bet = BETS[betIdx];
        if (balance < bet) { doubled = false; hit(); return; }
        balance -= bet; doubled = true; hit();
    }

    private void finishHand() {
        phase = 2;
        flipStartAt = System.currentTimeMillis();
        dealerNextAt = flipStartAt + 700;
        message = "Dealer plays";
        invalidate();
    }

    private void settle() {
        long bet = BETS[betIdx] * (doubled ? 2 : 1);
        int ps = score(player), ds = score(dealer);
        boolean pBJ = player.size() == 2 && ps == 21;
        boolean dBJ = dealer.size() == 2 && ds == 21;
        if (ps > 21) { message = "Bust - dealer wins"; lastWin = 0; resultKind = 0; resultAmount = bet; }
        else if (pBJ && !dBJ) { long w = bet + bet * 3 / 2; balance += w; message = "Blackjack!"; lastWin = w - bet; resultKind = 3; resultAmount = lastWin; }
        else if (dBJ && !pBJ) { message = "Dealer blackjack"; lastWin = 0; resultKind = 0; resultAmount = bet; }
        else if (ds > 21) { balance += bet * 2; message = "Dealer busts!"; lastWin = bet; resultKind = 1; resultAmount = bet; }
        else if (ps > ds) { balance += bet * 2; message = "You win!"; lastWin = bet; resultKind = 1; resultAmount = bet; }
        else if (ps == ds) { balance += bet; message = "Push - bet returned"; lastWin = 0; resultKind = 2; resultAmount = 0; }
        else { message = "Dealer wins"; lastWin = 0; resultKind = 0; resultAmount = bet; }
        resultAt = System.currentTimeMillis();
        if (resultKind == 1 || resultKind == 3) spawnParticles();
        phase = 3;
        handsDone++;
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            long reward = 150L * level;
            balance += reward;
            levelUpPending = level; levelUpReward += reward;
            if (listener != null) listener.onLevelUp(level, reward);
        }
        if (listener != null) {
            if (pBJ && !dBJ) listener.onBigWin(lastWin, bet);
            listener.onSpinComplete((int) handsDone, lastWin, bet);
            listener.saveState();
        }
        if (balance < BETS[0] && listener != null) listener.onOutOfCoins();
        invalidate();
    }

    private void spawnParticles() {
        particlesOn = true;
        for (int i = 0; i < 18; i++) {
            px[i] = 0.5f + (rng.nextFloat() - 0.5f) * 0.1f;
            py[i] = 0.44f;
            pvx[i] = (rng.nextFloat() - 0.5f) * 0.0016f;
            pvy[i] = -(0.0006f + rng.nextFloat() * 0.0016f);
        }
    }

    // ---------- drawing helpers ----------
    private float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }
    private float easeOutBack(float t) { float c = 1.70158f; t -= 1f; return 1f + (c + 1f) * t * t * t + c * t * t; }

    private void drawCard(Canvas cv, int rankIdx, float x, float y, float w, float h, boolean faceDown, float scaleX) {
        float cx = x + w / 2f;
        float dw = w * Math.abs(scaleX);
        if (dw < 2) return;
        float dx = cx - dw / 2f;
        boolean showBack = faceDown ^ (scaleX < 0);
        // shadow
        rf.set(dx + 3, y + 5, dx + dw + 3, y + h + 5);
        p.setStyle(Paint.Style.FILL); p.setColor(0x55000000);
        cv.drawRoundRect(rf, dw * 0.09f, dw * 0.09f, p);
        // face
        rf.set(dx, y, dx + dw, y + h);
        p.setColor(showBack ? 0xFF1E3C78 : 0xFFFCFCFC);
        cv.drawRoundRect(rf, dw * 0.09f, dw * 0.09f, p);
        if (showBack) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2, dw * 0.02f)); p.setColor(0xFF4A6FB5);
            rf.set(dx + dw * 0.08f, y + h * 0.05f, dx + dw * 0.92f, y + h * 0.95f);
            cv.drawRoundRect(rf, dw * 0.06f, dw * 0.06f, p);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF4A6FB5);
            p.setTextSize(h * 0.30f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("\u2740", cx, y + h * 0.62f, p);
            return;
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xFFB9B9B9);
        cv.drawRoundRect(rf, dw * 0.09f, dw * 0.09f, p);
        p.setStyle(Paint.Style.FILL);
        int suit = rankIdx % 4;
        boolean red = suit == 1 || suit == 2;
        p.setColor(red ? 0xFFC62828 : 0xFF1B1B1B);
        float ts = w * 0.26f;
        p.setTextSize(ts); p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true);
        if (scaleX >= 0) {
            cv.drawText(RANKS[rankIdx], dx + dw * 0.07f, y + h * 0.26f, p);
            p.setTextSize(w * 0.24f);
            cv.drawText(SUITS[suit], dx + dw * 0.07f, y + h * 0.47f, p);
            // center suit
            p.setTextSize(w * 0.55f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText(SUITS[suit], cx, y + h * 0.92f, p);
        }
        p.setFakeBoldText(false);
    }

    private void drawChip(Canvas cv, float cx, float cy, float r, int color, String label, boolean selected) {
        // shadow
        p.setStyle(Paint.Style.FILL); p.setColor(0x66000000);
        cv.drawCircle(cx + 2, cy + 4, r, p);
        // body
        p.setColor(color);
        cv.drawCircle(cx, cy, r, p);
        // edge stripes
        p.setColor(0xFFF5F5F5);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i + Math.PI / 6;
            float sx = cx + (float) Math.cos(a) * r * 0.86f;
            float sy = cy + (float) Math.sin(a) * r * 0.86f;
            float ex = cx + (float) Math.cos(a) * r * 1.0f;
            float ey = cy + (float) Math.sin(a) * r * 1.0f;
            p.setStrokeWidth(r * 0.16f);
            cv.drawLine(sx, sy, ex, ey, p);
        }
        // inner ring
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.06f); p.setColor(0xCCFFFFFF);
        cv.drawCircle(cx, cy, r * 0.66f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF5F5F5); cv.drawCircle(cx, cy, r * 0.55f, p);
        p.setColor(0xFF202020); p.setTextSize(r * 0.52f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(label, cx, cy + r * 0.19f, p);
        p.setFakeBoldText(false);
        if (selected) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.09f); p.setColor(0xFFFFD700);
            cv.drawCircle(cx, cy, r * 1.12f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawPillButton(Canvas cv, float x, float y, float w, float h, String label,
                                int bgTop, int bgBottom, boolean enabled, boolean pressed, float pulse) {
        float sc = pressed ? 0.94f : 1f;
        float cx = x + w / 2f, cy = y + h / 2f;
        float dw = w * sc, dh = h * sc;
        rf.set(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2);
        if (pulse > 0f && enabled) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h * 0.06f);
            p.setColor(0x44FFD700);
            float grow = pulse * h * 0.22f;
            RectF glow = new RectF(rf.left - grow, rf.top - grow, rf.right + grow, rf.bottom + grow);
            cv.drawRoundRect(glow, dh * 0.5f + grow, dh * 0.5f + grow, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (enabled) {
            p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, bgTop, bgBottom, Shader.TileMode.CLAMP));
        } else {
            p.setShader(null); p.setColor(0xFF37474F);
        }
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        p.setShader(null);
        // top highlight
        if (enabled) {
            p.setColor(0x33FFFFFF);
            rf.set(cx - dw / 2 + 6, cy - dh / 2 + 4, cx + dw / 2 - 6, cy - dh * 0.18f);
            cv.drawRoundRect(rf, dh * 0.4f, dh * 0.4f, p);
        }
        p.setColor(enabled ? 0xFFFFFFFF : 0xFF90A4AE);
        p.setTextSize(dh * 0.44f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        p.setShadowLayer(3, 0, 2, 0x66000000);
        cv.drawText(label, cx, cy + dh * 0.16f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    private void drawFancyText(Canvas cv, String text, float cx, float cy, float size, int topColor, int bottomColor) {
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size); p.setFakeBoldText(true);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.10f); p.setColor(0xAA000000);
        cv.drawText(text, cx, cy, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, cy - size, 0, cy + size * 0.2f, topColor, bottomColor, Shader.TileMode.CLAMP));
        cv.drawText(text, cx, cy, p);
        p.setShader(null); p.setFakeBoldText(false);
    }

    private void drawScoreBubble(Canvas cv, String text, float cx, float cy, float r, int bg) {
        p.setStyle(Paint.Style.FILL); p.setColor(0x55000000);
        cv.drawCircle(cx + 2, cy + 3, r, p);
        p.setColor(bg);
        cv.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0xFFFFD700);
        cv.drawCircle(cx, cy, r - 2, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        p.setTextSize(r * 0.85f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(text, cx, cy + r * 0.32f, p);
        p.setFakeBoldText(false);
    }

    // ---------- main draw ----------
    @Override protected void onDraw(Canvas cv) {
        regen();
        long now = System.currentTimeMillis();
        int w = getWidth(), h = getHeight();
        boolean animating = false;

        // felt background
        p.setShader(new RadialGradient(w / 2f, h * 0.42f, h * 0.55f, 0xFF1E7A58, 0xFF06281E, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // table arc
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w * 0.008f); p.setColor(0x55FFD700);
        float arcR = w * 0.40f;
        cv.drawArc(w / 2f - arcR, h * 0.34f, w / 2f + arcR, h * 0.34f + arcR * 1.1f, 20, 140, false, p);
        p.setStyle(Paint.Style.FILL);
        // felt markings
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0x66FFFFFF); p.setTextSize(h * 0.016f); p.setFakeBoldText(true);
        cv.drawText("BLACKJACK PAYS 3 TO 2", w / 2f, h * 0.335f, p);
        p.setTextSize(h * 0.013f); p.setColor(0x44FFFFFF);
        cv.drawText("DEALER MUST STAND ON ALL 17", w / 2f, h * 0.355f, p);
        p.setFakeBoldText(false);

        // ---- HUD ----
        // level badge + xp ring
        float lvR = w * 0.055f, lvX = w * 0.10f, lvY = h * 0.045f;
        p.setStyle(Paint.Style.FILL); p.setColor(0xFF0D47A1);
        cv.drawCircle(lvX, lvY, lvR, p);
        float xpFrac = Math.min(1f, xp / (float) xpNeeded());
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(lvR * 0.18f); p.setColor(0x33FFFFFF);
        cv.drawCircle(lvX, lvY, lvR * 0.82f, p);
        p.setColor(0xFFFFD700);
        cv.drawArc(lvX - lvR * 0.82f, lvY - lvR * 0.82f, lvX + lvR * 0.82f, lvY + lvR * 0.82f, -90, xpFrac * 360f, false, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        p.setTextSize(lvR * 0.62f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText("" + level, lvX, lvY + lvR * 0.24f, p);
        p.setFakeBoldText(false);
        p.setTextSize(h * 0.011f); p.setColor(0x99FFFFFF);
        cv.drawText("LEVEL", lvX, lvY + lvR * 1.55f, p);

        // coin pill
        float pillW = w * 0.34f, pillH = h * 0.034f, pillX = w / 2f - pillW / 2f + w * 0.06f, pillY = h * 0.028f;
        rf.set(pillX, pillY, pillX + pillW, pillY + pillH);
        p.setColor(0xCC000000);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x66FFD700);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStyle(Paint.Style.FILL);
        // coin icon
        float coinR = pillH * 0.36f, coinX = pillX + pillH * 0.55f, coinY = pillY + pillH / 2f;
        p.setColor(0xFFFFD700); cv.drawCircle(coinX, coinY, coinR, p);
        p.setColor(0xFFB8860B); p.setTextSize(coinR * 1.2f); p.setFakeBoldText(true);
        cv.drawText("$", coinX, coinY + coinR * 0.42f, p);
        p.setFakeBoldText(false);
        // count-up
        if (shownBalance != balance) {
            long diff = balance - shownBalance;
            long step = Math.max(1, Math.abs(diff) / 6);
            shownBalance += diff > 0 ? step : -step;
            if ((diff > 0 && shownBalance > balance) || (diff < 0 && shownBalance < balance)) shownBalance = balance;
            animating = true;
        }
        p.setColor(0xFFFFFFFF); p.setTextSize(pillH * 0.52f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(String.format("%,d", shownBalance), pillX + pillW * 0.58f, pillY + pillH * 0.68f, p);
        p.setFakeBoldText(false);
        // refill timer
        if (balance < 100) {
            long nextIn = 60000 - (now - lastRegenAt) % 60000;
            p.setTextSize(h * 0.011f); p.setColor(0x99FFFFFF); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("+5 free in " + (nextIn / 1000 + 1) + "s", pillX + pillW / 2f, pillY + pillH + h * 0.016f, p);
            animating = true;
        }

        float cw = w * 0.155f, ch = cw * 1.42f;
        float cardGap = Math.min(w * 0.062f, cw * 0.45f);

        // ---- dealer ----
        p.setColor(0xFFE0C9A6); p.setTextSize(h * 0.016f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText("DEALER", w / 2f, h * 0.105f, p);
        int dShown = dealer.size();
        float dTotal = cw + (dShown - 1) * cardGap;
        float dx0 = w / 2f - dTotal / 2f;
        for (int i = 0; i < dShown; i++) {
            long t0 = dealerCardT.get(i);
            float t = Math.min(1f, (now - t0) / 320f);
            if (t < 1f) animating = true;
            if (t <= 0f) continue;
            float te = easeOut(t);
            float tx = dx0 + i * cardGap;
            float sx = w * 0.88f, sy = -ch;
            float x = sx + (tx - sx) * te, y = sy + (h * 0.12f - sy) * te;
            boolean hide = (phase == 1 && i == 1);
            float scaleX = 1f;
            if (i == 1 && flipStartAt > 0 && now - flipStartAt < 420) {
                float ft = (now - flipStartAt) / 420f;
                scaleX = (float) Math.cos(ft * Math.PI);
                animating = true;
                hide = ft < 0.5f;
            }
            drawCard(cv, dealer.get(i), x, y, cw, ch, hide, scaleX);
        }
        if (!dealer.isEmpty()) {
            int ds = phase == 1 ? val(dealer.get(0)) : score(dealer);
            drawScoreBubble(cv, "" + ds, dx0 - w * 0.045f, h * 0.155f, w * 0.036f, 0xFF263238);
        }

        // ---- player ----
        if (!player.isEmpty()) {
            p.setColor(0xFFE0C9A6); p.setTextSize(h * 0.016f);
            cv.drawText("YOU", w / 2f, h * 0.50f, p);
            int pShown = player.size();
            float pTotal = cw + (pShown - 1) * cardGap;
            float px0 = w / 2f - pTotal / 2f;
            for (int i = 0; i < pShown; i++) {
                long t0 = playerCardT.get(i);
                float t = Math.min(1f, (now - t0) / 320f);
                if (t < 1f) animating = true;
                if (t <= 0f) continue;
                float te = easeOut(t);
                float tx = px0 + i * cardGap;
                float sx = w * 0.88f, sy = -ch;
                float x = sx + (tx - sx) * te, y = sy + (h * 0.52f - sy) * te;
                drawCard(cv, player.get(i), x, y, cw, ch, false, 1f);
            }
            int ps = score(player);
            int bubbleColor = ps > 21 ? 0xFFB71C1C : (ps == 21 ? 0xFF2E7D32 : 0xFF263238);
            drawScoreBubble(cv, "" + ps, px0 + pTotal + w * 0.045f, h * 0.565f, w * 0.036f, bubbleColor);
        }

        // ---- bet spot ----
        float spotR = w * 0.075f, spotX = w / 2f, spotY = h * 0.415f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0x44FFD700);
        cv.drawCircle(spotX, spotY, spotR, p);
        p.setStyle(Paint.Style.FILL);
        long currentBet = BETS[betIdx] * (doubled ? 2 : 1);
        if (phase != 0) {
            // stacked chips representing current bet
            long remaining = currentBet;
            java.util.List<Integer> stackChips = new java.util.ArrayList<>();
            for (int c = BETS.length - 1; c >= 0 && stackChips.size() < 5; c--) {
                while (remaining >= BETS[c] && stackChips.size() < 5) { remaining -= BETS[c]; stackChips.add(c); }
            }
            for (int i = 0; i < stackChips.size(); i++)
                drawChip(cv, spotX, spotY - i * spotR * 0.22f, spotR * 0.42f, CHIP_COLORS[stackChips.get(i)], "", false);
            p.setColor(0xFFFFFFFF); p.setTextSize(h * 0.016f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("" + currentBet, spotX, spotY + spotR * 1.35f, p);
            p.setFakeBoldText(false);
        } else {
            p.setColor(0x55FFFFFF); p.setTextSize(h * 0.014f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("BET SPOT", spotX, spotY + h * 0.005f, p);
        }

        // ---- result banner ----
        if (resultKind >= 0 && now - resultAt < 2100 && !levelUpShowing) {
            float t = Math.min(1f, (now - resultAt) / 380f);
            float sc = easeOutBack(t);
            animating = true;
            String text; int topC, botC;
            switch (resultKind) {
                case 3: text = "BLACKJACK!  +" + resultAmount; topC = 0xFFFFE082; botC = 0xFFFF8F00; break;
                case 1: text = "YOU WIN  +" + resultAmount; topC = 0xFFA5D6A7; botC = 0xFF2E7D32; break;
                case 2: text = "PUSH"; topC = 0xFFEEEEEE; botC = 0xFF9E9E9E; break;
                default: text = message.toUpperCase(); topC = 0xFFEF9A9A; botC = 0xFFC62828;
            }
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.45f);
            drawFancyText(cv, text, w / 2f, h * 0.45f, h * 0.034f * (resultKind == 3 ? 1.25f : 1f), topC, botC);
            cv.restore();
            if (particlesOn) {
                float pt = (now - resultAt) / 1000f;
                for (int i = 0; i < 18; i++) {
                    float xx = (px[i] + pvx[i] * (now - resultAt)) * w;
                    float yy = (py[i] + pvy[i] * (now - resultAt) + 0.0000009f * (now - resultAt) * (now - resultAt)) * h;
                    if (pt < 1.2f) drawChip(cv, xx, yy, w * 0.016f, CHIP_COLORS[i % 4], "", false);
                }
            }
        }

        // ---- controls ----
        float bh = h * 0.058f;
        long idleMs = now - Math.max(resultAt, Math.max(flipStartAt, lastTouchAt));
        float pulse = (phase == 0 && idleMs > 3000) ? (float) (0.5 + 0.5 * Math.sin(now / 450.0)) : 0f;
        if (pulse > 0) animating = true;
        if (phase == 0 || phase == 3) {
            // chip selector row
            float chipR = w * 0.068f, chipGap = w * 0.045f;
            float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
            float cx0 = w / 2f - rowW / 2f + chipR;
            for (int i = 0; i < BETS.length; i++) {
                boolean sel = i == betIdx;
                float cy = h * 0.735f + (sel ? -h * 0.008f : 0);
                drawChip(cv, cx0 + i * (chipR * 2 + chipGap), cy, chipR, CHIP_COLORS[i], "" + BETS[i], sel);
            }
            p.setColor(0x99FFFFFF); p.setTextSize(h * 0.012f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("TAP A CHIP TO SET YOUR BET", w / 2f, h * 0.677f, p);
            drawPillButton(cv, w * 0.14f, h * 0.80f, w * 0.72f, bh, "DEAL   \u25B6", 0xFF43A047, 0xFF1B5E20,
                    balance >= BETS[betIdx], pressedBtn == 0, pulse);
        } else if (phase == 1) {
            boolean canDouble = player.size() == 2 && balance >= BETS[betIdx];
            drawPillButton(cv, w * 0.05f, h * 0.76f, w * 0.28f, bh, "HIT", 0xFF43A047, 0xFF1B5E20, true, pressedBtn == 1, 0);
            drawPillButton(cv, w * 0.36f, h * 0.76f, w * 0.28f, bh, "STAND", 0xFFE53935, 0xFF8E0000, true, pressedBtn == 2, 0);
            drawPillButton(cv, w * 0.67f, h * 0.76f, w * 0.28f, bh, "DOUBLE", 0xFF1E88E5, 0xFF0D47A1, canDouble, pressedBtn == 3, 0);
        } else {
            p.setColor(0x99FFFFFF); p.setTextSize(h * 0.02f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("...", w / 2f, h * 0.79f, p);
        }

        // ---- level-up overlay ----
        if (levelUpPending > 0 && !levelUpShowing && now - resultAt > 1400) {
            levelUpShowing = true; levelUpAt = now;
        }
        if (levelUpShowing) {
            animating = true;
            float t = Math.min(1f, (now - levelUpAt) / 420f);
            cv.drawColor(0xB3000000);
            float sc = easeOutBack(t);
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.42f);
            // badge
            float badgeR = w * 0.15f;
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD700);
            cv.drawCircle(w / 2f, h * 0.36f, badgeR, p);
            p.setColor(0xFFB8860B); p.setTextSize(badgeR * 0.9f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("" + levelUpPending, w / 2f, h * 0.36f + badgeR * 0.32f, p);
            p.setFakeBoldText(false);
            // rays
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w * 0.012f); p.setColor(0x55FFD700);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI / 6 * i + now / 900.0;
                cv.drawLine(w / 2f + (float) Math.cos(a) * badgeR * 1.25f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.25f,
                        w / 2f + (float) Math.cos(a) * badgeR * 1.6f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.6f, p);
            }
            p.setStyle(Paint.Style.FILL);
            drawFancyText(cv, "LEVEL UP!", w / 2f, h * 0.53f, h * 0.045f, 0xFFFFE082, 0xFFFF8F00);
            p.setColor(0xFFFFFFFF); p.setTextSize(h * 0.022f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("Reward: +" + levelUpReward + " coins", w / 2f, h * 0.575f, p);
            cv.restore();
            if (t >= 1f)
                drawPillButton(cv, w * 0.3f, h * 0.62f, w * 0.4f, h * 0.052f, "COLLECT", 0xFF43A047, 0xFF1B5E20, true, pressedBtn == 9, 0);
        }

        // ---- state machine tick ----
        if (phase == 2 && now >= dealerNextAt) {
            if (score(dealer) < 17) {
                dealer.add(draw());
                dealerCardT.add(now);
                dealerNextAt = now + 650;
            } else settle();
            invalidate();
        } else if (phase == 2 || animating) {
            postInvalidateDelayed(32);
        }
    }

    private long lastTouchAt = System.currentTimeMillis();

    @Override public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        float bh = h * 0.058f;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            lastTouchAt = System.currentTimeMillis();
            pressedBtn = hitButton(w, h, bh, x, y);
            pressedAt = System.currentTimeMillis();
            if (pressedBtn >= 0) { invalidate(); return true; }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int btn = hitButton(w, h, bh, x, y);
            int was = pressedBtn; pressedBtn = -1; invalidate();
            if (btn != was || btn < 0) {
                // chip selection happens on up outside buttons
                if ((phase == 0 || phase == 3) && !levelUpShowing) selectChipAt(w, h, x, y);
                return true;
            }
            if (levelUpShowing) {
                if (btn == 9) { levelUpShowing = false; levelUpPending = 0; levelUpReward = 0; invalidate(); }
                return true;
            }
            if (phase == 0 || phase == 3) {
                if (btn == 0 && balance >= BETS[betIdx]) deal();
            } else if (phase == 1) {
                if (btn == 1) hit();
                else if (btn == 2) stand();
                else if (btn == 3 && player.size() == 2 && balance >= BETS[betIdx]) doubleDown();
            }
            return true;
        }
        return true;
    }

    private int hitButton(int w, int h, float bh, float x, float y) {
        if (levelUpShowing) {
            if (x >= w * 0.3f && x <= w * 0.7f && y >= h * 0.62f && y <= h * 0.62f + h * 0.052f) return 9;
            return -1;
        }
        if (phase == 0 || phase == 3) {
            if (x >= w * 0.14f && x <= w * 0.86f && y >= h * 0.80f && y <= h * 0.80f + bh) return 0;
        } else if (phase == 1) {
            if (y >= h * 0.76f && y <= h * 0.76f + bh) {
                if (x >= w * 0.05f && x <= w * 0.33f) return 1;
                if (x >= w * 0.36f && x <= w * 0.64f) return 2;
                if (x >= w * 0.67f && x <= w * 0.95f) return 3;
            }
        }
        return -1;
    }

    private void selectChipAt(int w, int h, float x, float y) {
        float chipR = w * 0.068f, chipGap = w * 0.045f;
        float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
        float cx0 = w / 2f - rowW / 2f + chipR;
        for (int i = 0; i < BETS.length; i++) {
            float cx = cx0 + i * (chipR * 2 + chipGap);
            float ddx = x - cx, ddy = y - h * 0.735f;
            if (ddx * ddx + ddy * ddy <= chipR * chipR * 1.44f && i != betIdx) {
                betIdx = i;
                invalidate();
                if (listener != null) listener.saveState();
                return;
            }
        }
    }
}
