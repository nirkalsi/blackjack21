package nir.com.blackjack21;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Blackjack 21 - free social blackjack. Virtual coins only, no real money, no prizes. */
public class BlackjackView extends View {
    public interface Listener {
        void onOutOfCoins();
        void onSpinComplete(int handsDone, long profit, long bet);
        void onBigWin(long win, long bet);
        void onLevelUp(int level, long reward);
        void saveState();
    }

    private static final int[] BETS = {5, 10, 25, 50};
    private static final String[] SUITS = {"\u2660", "\u2665", "\u2666", "\u2663"};
    private static final String[] RANKS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private long balance = 500;
    private long lastWin = 0;
    private int betIdx = 1;
    private long handsDone = 0;
    private long lastRegenAt = System.currentTimeMillis();
    private long xp = 0;
    private int level = 1;
    private long levelFlashUntil = 0;

    // phases: 0=betting, 1=player turn, 2=dealer turn, 3=result
    private int phase = 0;
    private final List<Integer> player = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private boolean doubled = false;
    private String message = "Place your bet";
    private long dealerNextAt = 0;

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

    private int draw() { int r = rng.nextInt(13); return r; } // 0..12 rank index
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
        if (doubled) doubled = false;
        player.clear(); dealer.clear();
        player.add(draw()); dealer.add(draw()); player.add(draw()); dealer.add(draw());
        message = "Your move";
        phase = 1;
        if (score(player) == 21) { finishHand(); }
        invalidate(); if (listener != null) listener.saveState();
    }

    private void hit() {
        player.add(draw());
        if (score(player) > 21) finishHand();
        else if (doubled) stand();
        invalidate();
    }

    private void stand() {
        phase = 2;
        dealerNextAt = System.currentTimeMillis() + 500;
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
        dealerNextAt = System.currentTimeMillis() + 400;
        message = "Dealer plays";
        invalidate();
    }

    private void settle() {
        long bet = BETS[betIdx] * (doubled ? 2 : 1);
        int ps = score(player), ds = score(dealer);
        boolean pBJ = player.size() == 2 && ps == 21;
        boolean dBJ = dealer.size() == 2 && ds == 21;
        if (ps > 21) { message = "Bust! Dealer wins"; lastWin = 0; }
        else if (pBJ && !dBJ) { long w = bet + bet * 3 / 2; balance += w; message = "Blackjack! +" + (w - bet); lastWin = w - bet; }
        else if (dBJ && !pBJ) { message = "Dealer blackjack"; lastWin = 0; }
        else if (ds > 21) { balance += bet * 2; message = "Dealer busts! +" + bet; lastWin = bet; }
        else if (ps > ds) { balance += bet * 2; message = "You win! +" + bet; lastWin = bet; }
        else if (ps == ds) { balance += bet; message = "Push - bet returned"; lastWin = 0; }
        else { message = "Dealer wins"; lastWin = 0; }
        phase = 3;
        handsDone++;
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            long reward = 150L * level;
            balance += reward;
            levelFlashUntil = System.currentTimeMillis() + 2000;
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

    private void drawCard(Canvas cv, int rankIdx, float x, float y, float w, float h, boolean faceDown) {
        rf.set(x, y, x + w, y + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(faceDown ? Color.rgb(30, 60, 120) : Color.WHITE);
        cv.drawRoundRect(rf, w * 0.08f, w * 0.08f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.rgb(200, 200, 200));
        cv.drawRoundRect(rf, w * 0.08f, w * 0.08f, p);
        p.setStyle(Paint.Style.FILL);
        if (!faceDown) {
            int suit = rankIdx % 4;
            boolean red = suit == 1 || suit == 2;
            p.setColor(red ? Color.rgb(198, 40, 40) : Color.rgb(27, 27, 27));
            p.setTextSize(w * 0.3f); p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true);
            cv.drawText(RANKS[rankIdx], x + w * 0.08f, y + h * 0.32f, p);
            p.setTextSize(w * 0.36f);
            cv.drawText(SUITS[suit], x + w * 0.08f, y + h * 0.68f, p);
            p.setFakeBoldText(false);
        } else {
            p.setStyle(Paint.Style.STROKE); p.setColor(Color.rgb(80, 120, 200)); p.setStrokeWidth(3);
            cv.drawRoundRect(rf, w * 0.08f, w * 0.08f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawButton(Canvas cv, float x, float y, float w, float h, String label, int bg, boolean enabled) {
        rf.set(x, y, x + w, y + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(enabled ? bg : Color.rgb(40, 50, 45));
        cv.drawRoundRect(rf, h * 0.3f, h * 0.3f, p);
        p.setColor(enabled ? Color.WHITE : Color.rgb(120, 130, 125));
        p.setTextSize(h * 0.42f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(label, x + w / 2, y + h * 0.64f, p);
        p.setFakeBoldText(false);
    }

    @Override protected void onDraw(Canvas cv) {
        regen();
        int w = getWidth(), h = getHeight();
        cv.drawColor(Color.rgb(11, 61, 46));
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setColor(Color.rgb(255, 215, 0));
        p.setTextSize(h * 0.042f);
        cv.drawText("BLACKJACK 21", w / 2, h * 0.07f, p);
        p.setFakeBoldText(false);
        p.setColor(Color.WHITE);
        p.setTextSize(h * 0.032f);
        cv.drawText("COINS  " + balance, w / 2, h * 0.108f, p);
        // level + xp bar
        p.setTextSize(h * 0.022f);
        p.setColor(Color.rgb(168, 203, 184));
        cv.drawText("LV " + level, w * 0.14f, h * 0.142f, p);
        float barL = w * 0.24f, barR = w * 0.76f, barY = h * 0.130f, barH = w * 0.022f;
        rf.set(barL, barY, barR, barY + barH);
        p.setColor(Color.rgb(18, 80, 60)); cv.drawRoundRect(rf, 8, 8, p);
        float frac = Math.min(1f, xp / (float) xpNeeded());
        rf.set(barL, barY, barL + (barR - barL) * frac, barY + barH);
        p.setColor(Color.rgb(255, 215, 0)); cv.drawRoundRect(rf, 8, 8, p);
        if (System.currentTimeMillis() < levelFlashUntil) {
            p.setTextSize(h * 0.035f);
            p.setColor(Color.rgb(255, 215, 0));
            p.setFakeBoldText(true);
            cv.drawText("LEVEL UP!", w / 2, h * 0.19f, p);
            p.setFakeBoldText(false);
        }

        float cw = w * 0.15f, ch = cw * 1.45f;
        // dealer row
        p.setColor(Color.rgb(224, 201, 166)); p.setTextSize(h * 0.026f);
        cv.drawText(phase >= 3 || phase == 2 ? "DEALER  " + score(dealer) : "DEALER", w / 2, h * 0.175f, p);
        float dx = w / 2 - (dealer.size() * (cw + 8)) / 2f;
        for (int i = 0; i < dealer.size(); i++) {
            boolean hide = (phase == 1 && i == 1);
            drawCard(cv, dealer.get(i), dx + i * (cw + 8), h * 0.19f, cw, ch, hide);
        }
        // player row
        p.setColor(Color.rgb(224, 201, 166)); p.setTextSize(h * 0.026f);
        cv.drawText(player.isEmpty() ? "YOU" : "YOU  " + score(player), w / 2, h * 0.47f, p);
        float px = w / 2 - (player.size() * (cw + 8)) / 2f;
        for (int i = 0; i < player.size(); i++) drawCard(cv, player.get(i), px + i * (cw + 8), h * 0.49f, cw, ch, false);

        // message
        p.setColor(lastWin > 0 ? Color.rgb(124, 252, 0) : Color.WHITE);
        p.setTextSize(h * 0.034f); p.setFakeBoldText(true);
        cv.drawText(message, w / 2, h * 0.665f, p);
        p.setFakeBoldText(false);

        float bh = h * 0.062f, bw = w * 0.28f, gap = w * 0.04f;
        if (phase == 0 || phase == 3) {
            // bet selector
            p.setColor(Color.WHITE); p.setTextSize(h * 0.03f);
            cv.drawText("BET  " + BETS[betIdx], w / 2, h * 0.715f, p);
            drawButton(cv, w / 2 - bw - gap, h * 0.75f, bw, bh, "- BET", Color.rgb(20, 80, 60), true);
            drawButton(cv, w / 2 + gap, h * 0.75f, bw, bh, "+ BET", Color.rgb(20, 80, 60), true);
            drawButton(cv, w / 2 - bw * 1.2f, h * 0.83f, bw * 2.4f, bh, "DEAL", Color.rgb(198, 40, 40), true);
        } else if (phase == 1) {
            drawButton(cv, w / 2 - bw * 1.5f - gap, h * 0.76f, bw, bh, "HIT", Color.rgb(20, 80, 60), true);
            drawButton(cv, w / 2 - bw / 2, h * 0.76f, bw, bh, "STAND", Color.rgb(198, 40, 40), true);
            drawButton(cv, w / 2 + bw / 2 + gap, h * 0.76f, bw, bh, "DOUBLE", Color.rgb(20, 80, 60), player.size() == 2 && balance >= BETS[betIdx]);
        } else {
            p.setColor(Color.WHITE); p.setTextSize(h * 0.03f);
            cv.drawText("...", w / 2, h * 0.78f, p);
        }
        if (phase == 2 && System.currentTimeMillis() >= dealerNextAt) {
            if (score(dealer) < 17) { dealer.add(draw()); dealerNextAt = System.currentTimeMillis() + 600; invalidate(); }
            else settle();
        } else if (phase == 2) {
            postInvalidateDelayed(200);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        int w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        float bh = h * 0.062f, bw = w * 0.28f, gap = w * 0.04f;
        if (phase == 0 || phase == 3) {
            if (y >= h * 0.75f && y <= h * 0.75f + bh) {
                if (x >= w / 2 - bw - gap && x <= w / 2 - gap && betIdx > 0) { betIdx--; invalidate(); if (listener != null) listener.saveState(); }
                if (x >= w / 2 + gap && x <= w / 2 + gap + bw && betIdx < BETS.length - 1) { betIdx++; invalidate(); if (listener != null) listener.saveState(); }
            }
            if (y >= h * 0.83f && y <= h * 0.83f + bh && x >= w / 2 - bw * 1.2f && x <= w / 2 + bw * 1.2f) deal();
        } else if (phase == 1) {
            if (y >= h * 0.76f && y <= h * 0.76f + bh) {
                if (x >= w / 2 - bw * 1.5f - gap && x <= w / 2 - bw * 0.5f - gap) hit();
                else if (x >= w / 2 - bw / 2 && x <= w / 2 + bw / 2) stand();
                else if (x >= w / 2 + bw / 2 + gap && x <= w / 2 + bw * 1.5f + gap && player.size() == 2 && balance >= BETS[betIdx]) doubleDown();
            }
        }
        return true;
    }
}
