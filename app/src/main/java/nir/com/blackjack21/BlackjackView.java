package nir.com.blackjack21;

import android.content.Context;
import android.content.SharedPreferences;
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
    // independent per-card suits (approved by Nir 01.11, WhatsApp). Cards are still ranks 0..12
    // drawn independently (infinite-shoe); each card also gets an independent suit 0..3.
    // 0=spades 1=hearts 2=diamonds 3=clubs. Display + side bets only; 21 rules unchanged.
    private final List<Integer> playerSuits = new ArrayList<>();
    private final List<Integer> dealerSuits = new ArrayList<>();
    // side bets (approved 01.11): Perfect Pairs + 21+3, TOTAL side stake <= main bet.
    private boolean ppOn = false, tfOn = false;
    private long ppStake = 0, tfStake = 0;
    private int ppKind = -1, tfKind = -1;   // evaluated at deal; -1 = no win
    static final long[] PP_MULT = {5, 10, 25};           // mixed, colored, perfect
    static final String[] PP_NAMES = {"MIXED PAIR", "COLORED PAIR", "PERFECT PAIR"};
    static final long[] TF_MULT = {5, 9, 30, 40, 100};   // flush, straight, trips, SF, suited trips
    static final String[] TF_NAMES = {"FLUSH", "STRAIGHT", "3 OF A KIND", "STRAIGHT FLUSH", "SUITED TRIPS"};
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
    // coin burst: coins fly from screen center to the balance pill (daily bonus claim)
    private final float[] cbx = new float[14], cby = new float[14], cbvx = new float[14], cbvy = new float[14];
    private boolean coinBurstOn = false;
    private long coinBurstAt = 0;

    private float[] texX, texY; private boolean[] texL; // felt texture cache
    private android.graphics.Bitmap bgCache; private int bgW = -1, bgH = -1;
    private float[] dustX, dustY, dustS, dustV;
    private int levelUpPending = 0;      // level number waiting for celebration
    private long levelUpReward = 0;
    private boolean levelUpShowing = false;
    private long levelUpAt = 0;
    private int pressedBtn = -1;
    private long betBumpAt = 0;         // 0..n index into current buttons
    private long pressedAt = 0;

    public BlackjackView(Context c) { super(c); loadStats(); }

    // lifetime stats (persisted in app prefs)
    private boolean statsOpen = false;
    private int cosBack = 0;
    private static final int[] BACK_UNLOCK_LEVEL = {1, 2, 4, 6, 9};
    private static final int[][] BACK_STYLE = {
            {0xFFB71C2E, 0xFF6E0F1C, 0xFFD4AF37, 0x33FFD4AF},  // classic red
            {0xFF16324F, 0xFF081226, 0xFFB8CCE8, 0x33B8D4FF},  // midnight
            {0xFF0E5A38, 0xFF062A1A, 0xFFD4AF37, 0x33FFE9B0},  // emerald
            {0xFF4A1D6E, 0xFF1E0A38, 0xFFD4AF37, 0x33FFD4AF},  // royal
            {0xFF23232B, 0xFF0A0A10, 0xFFD4AF37, 0x26FFD4AF},  // onyx
    };

    static int backCount() { return BACK_STYLE.length; }
    static int backUnlockLevel(int v) { return BACK_UNLOCK_LEVEL[v]; }
    private long stHands, stWins, stPushes, stBlackjacks, stBiggest, stStreak, stBestStreak;

    private void loadStats() {
        SharedPreferences sp = getContext().getSharedPreferences("blackjack21", Context.MODE_PRIVATE);
        stHands = sp.getLong("st_hands", 0);
        stWins = sp.getLong("st_wins", 0);
        stPushes = sp.getLong("st_pushes", 0);
        stBlackjacks = sp.getLong("st_blackjacks", 0);
        stBiggest = sp.getLong("st_biggest", 0);
        stStreak = sp.getLong("st_streak", 0);
        stBestStreak = sp.getLong("st_best_streak", 0);
        cosBack = sp.getInt("cos_back", 0);
        if (cosBack < 0 || cosBack >= BACK_STYLE.length) cosBack = 0;
    }

    private void saveStats() {
        getContext().getSharedPreferences("blackjack21", Context.MODE_PRIVATE).edit()
                .putLong("st_hands", stHands)
                .putLong("st_wins", stWins)
                .putLong("st_pushes", stPushes)
                .putLong("st_blackjacks", stBlackjacks)
                .putLong("st_biggest", stBiggest)
                .putLong("st_streak", stStreak)
                .putLong("st_best_streak", stBestStreak)
                .apply();
    }

    public void setListener(Listener l) { listener = l; }

    public void addCoins(long n) { balance += n; invalidate(); if (listener != null) listener.saveState(); }

    public int getLevel() { return level; }
    public long getSpinsDone() { return handsDone; }
    private long xpNeeded() { return level * 500L; }

    public String serialize() { return balance + ";" + betIdx + ";" + handsDone + ";" + lastRegenAt + ";" + xp + ";" + level + ";" + ((ppOn ? 1 : 0) + (tfOn ? 2 : 0)); }

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
            if (a.length > 6) { int f6 = Integer.parseInt(a[6]); ppOn = (f6 & 1) != 0; tfOn = (f6 & 2) != 0; }
        } catch (Exception ignored) {}
    }

    private void regen() {
        long now = System.currentTimeMillis();
        long ticks = (now - lastRegenAt) / 60000;
        if (ticks > 0) { lastRegenAt += ticks * 60000; if (balance < 100) balance = Math.min(100, balance + ticks * 5); }
    }

    private int draw() { return rng.nextInt(13); }
    private int drawSuit() { return rng.nextInt(4); }

    /** Perfect Pairs on the player's first two cards: 0 mixed, 1 colored, 2 perfect, -1 no pair. */
    static int evalPerfectPairs(int r0, int s0, int r1, int s1) {
        if (r0 != r1) return -1;
        if (s0 == s1) return 2;
        boolean red0 = s0 == 1 || s0 == 2, red1 = s1 == 1 || s1 == 2;
        return red0 == red1 ? 1 : 0;
    }

    /** 21+3 on player's two cards + dealer upcard; only the highest category pays.
     *  0 flush, 1 straight, 2 trips, 3 straight flush, 4 suited trips, -1 no win. */
    static int evalTwentyOneThree(int r0, int s0, int r1, int s1, int r2, int s2) {
        boolean flush = s0 == s1 && s1 == s2;
        boolean trips = r0 == r1 && r1 == r2;
        int a = Math.min(r0, Math.min(r1, r2)), c = Math.max(r0, Math.max(r1, r2));
        int b = r0 + r1 + r2 - a - c;
        boolean straight = (a != c) && ((b == a + 1 && c == b + 1) || (a == 0 && b == 11 && c == 12));
        if (trips) return flush ? 4 : 2;
        if (straight) return flush ? 3 : 1;
        if (flush) return 0;
        return -1;
    }

    private int sideCount() { return (ppOn ? 1 : 0) + (tfOn ? 1 : 0); }
    private long sideTotal() { int n = sideCount(); return n == 0 ? 0 : (BETS[betIdx] / n) * n; }
    private long totalWager() { return BETS[betIdx] + sideTotal(); }
    private long sideNet() {
        return (ppKind >= 0 ? ppStake * PP_MULT[ppKind] : -ppStake)
             + (tfKind >= 0 ? tfStake * TF_MULT[tfKind] : -tfStake);
    }
    static int val(int rankIdx) { return rankIdx == 0 ? 11 : Math.min(10, rankIdx + 1); }

    /** Dealer stands on all 17 (soft 17 stands). Used by the onDraw dealer loop. */
    static boolean dealerShouldDraw(List<Integer> dealer) { return score(dealer) < 17; }

    static int score(List<Integer> hand) {
        int s = 0, aces = 0;
        for (int r : hand) { s += val(r); if (r == 0) aces++; }
        while (s > 21 && aces > 0) { s -= 10; aces--; }
        return s;
    }

    /** Simplified basic strategy (dealer stands on all 17, no split/surrender).
     *  Returns 0=HIT, 1=STAND, 2=DOUBLE. Not a guaranteed-optimal engine. */
    static int basicStrategyMove(List<Integer> playerRanks, int dealerUpRank, boolean canDouble) {
        int s = score(playerRanks);
        boolean soft = isSoft(playerRanks);
        int up = dealerUpRank == 0 ? 11 : Math.min(10, dealerUpRank + 1);
        int move;
        if (soft) {
            if (s >= 19) move = 1;
            else if (s == 18) {
                if (up >= 3 && up <= 6) move = 2;
                else if (up == 2 || up == 7 || up == 8) move = 1;
                else move = 0;
            } else if (s == 17) move = (up >= 3 && up <= 6) ? 2 : 0;
            else move = (up >= 4 && up <= 6) ? 2 : 0; // soft 13-16
        } else {
            if (s >= 17) move = 1;
            else if (s >= 13) move = (up >= 2 && up <= 6) ? 1 : 0;
            else if (s == 12) move = (up >= 4 && up <= 6) ? 1 : 0;
            else if (s == 11) move = (up >= 2 && up <= 10) ? 2 : 0;
            else if (s == 10) move = (up >= 2 && up <= 9) ? 2 : 0;
            else if (s == 9) move = (up >= 3 && up <= 6) ? 2 : 0;
            else move = 0; // 8 or less
        }
        if (move == 2 && !canDouble) move = (soft && s == 18) ? 1 : 0;
        return move;
    }

    static boolean isSoft(List<Integer> hand) {
        int s = 0, aces = 0;
        for (int r : hand) { s += val(r); if (r == 0) aces++; }
        while (s > 21 && aces > 0) { s -= 10; aces--; }
        return aces > 0;
    }

    private void deal() {
        GameSounds.play(getContext(), GameSounds.CARD);
        long bet = BETS[betIdx];
        int nSide = sideCount();
        ppStake = ppOn ? bet / nSide : 0;
        tfStake = tfOn ? bet / nSide : 0;
        long total = bet + ppStake + tfStake;
        if (balance < total) { if (listener != null) listener.onOutOfCoins(); return; }
        balance -= total;
        xp += total;
        ppKind = -1; tfKind = -1;
        doubled = false;
        resultKind = -1; particlesOn = false; levelUpShowing = false;
        player.clear(); dealer.clear(); playerSuits.clear(); dealerSuits.clear(); playerCardT.clear(); dealerCardT.clear();
        long now = System.currentTimeMillis();
        player.add(draw()); playerSuits.add(drawSuit());
        dealer.add(draw()); dealerSuits.add(drawSuit());
        player.add(draw()); playerSuits.add(drawSuit());
        dealer.add(draw()); dealerSuits.add(drawSuit());
        playerCardT.add(now); dealerCardT.add(now + 130);
        playerCardT.add(now + 260); dealerCardT.add(now + 390);
        if (ppStake > 0) ppKind = evalPerfectPairs(player.get(0), playerSuits.get(0), player.get(1), playerSuits.get(1));
        if (tfStake > 0) tfKind = evalTwentyOneThree(player.get(0), playerSuits.get(0), player.get(1), playerSuits.get(1), dealer.get(0), dealerSuits.get(0));
        message = "Your move";
        phase = 1;
        if (score(player) == 21) finishHand();
        invalidate(); if (listener != null) listener.saveState();
    }

    private void hit() {
        player.add(draw());
        playerSuits.add(drawSuit());
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
        else if (pBJ && !dBJ) { long w = bet + bet * 3 / 2; balance += w; GameSounds.play(getContext(), GameSounds.BIG_WIN); message = "Blackjack!"; lastWin = w - bet; resultKind = 3; resultAmount = lastWin; }
        else if (dBJ && !pBJ) { message = "Dealer blackjack"; lastWin = 0; resultKind = 0; resultAmount = bet; }
        else if (ds > 21) { balance += bet * 2; GameSounds.play(getContext(), GameSounds.WIN); message = "Dealer busts!"; lastWin = bet; resultKind = 1; resultAmount = bet; }
        else if (ps > ds) { balance += bet * 2; GameSounds.play(getContext(), GameSounds.WIN); message = "You win!"; lastWin = bet; resultKind = 1; resultAmount = bet; }
        else if (ps == ds) { balance += bet; message = "Push - bet returned"; lastWin = 0; resultKind = 2; resultAmount = 0; }
        else { if (sideNet() <= 0) GameSounds.play(getContext(), GameSounds.LOSE); message = "Dealer wins"; lastWin = 0; resultKind = 0; resultAmount = bet; }
        // side bets settle on the initial cards ("to 1": stake returned on a win)
        long sideReturn = (ppKind >= 0 ? ppStake * (PP_MULT[ppKind] + 1) : 0)
                        + (tfKind >= 0 ? tfStake * (TF_MULT[tfKind] + 1) : 0);
        long sideNet = sideNet();
        if (sideReturn > 0) {
            balance += sideReturn;
            if (resultKind == 0) GameSounds.play(getContext(), GameSounds.WIN);
        }
        resultAt = System.currentTimeMillis();
        if (resultKind == 1 || resultKind == 3 || sideNet > 0) spawnParticles();
        stHands++;
        if (resultKind == 1 || resultKind == 3) {
            stWins++;
            stStreak++;
            if (stStreak > stBestStreak) stBestStreak = stStreak;
            if (lastWin > stBiggest) stBiggest = lastWin;
            if (resultKind == 3) stBlackjacks++;
        } else if (resultKind == 2) {
            stPushes++;
        } else {
            stStreak = 0;
        }
        saveStats();
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
            listener.onSpinComplete((int) handsDone, lastWin + sideNet, bet + ppStake + tfStake);
            listener.saveState();
        }
        if (balance < BETS[0] && listener != null) listener.onOutOfCoins();
        invalidate();
    }


    /** Coins burst from center and fly to the balance pill (e.g. daily claim). */
    public void coinBurst() {
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 14; i++) {
            cbx[i] = 0.5f + (r.nextFloat() - 0.5f) * 0.12f;
            cby[i] = 0.45f + (r.nextFloat() - 0.5f) * 0.08f;
            cbvx[i] = (r.nextFloat() - 0.5f) * 0.08f;
            cbvy[i] = (r.nextFloat() - 0.5f) * 0.08f;
        }
        coinBurstOn = true;
        coinBurstAt = System.currentTimeMillis();
        invalidate();
    }

    private void drawCoinBurst(Canvas cv, float w, float h, long now) {
        if (!coinBurstOn) return;
        float t = (now - coinBurstAt) / 750f;
        if (t >= 1f) { coinBurstOn = false; return; }
        for (int i = 0; i < 14; i++) {
            float local = Math.min(1f, Math.max(0f, t * 1.25f - i * 0.015f));
            float e = local * local;
            float x = (cbx[i] + cbvx[i] * local + (0.5f - cbx[i]) * e) * w;
            float y = (cby[i] + cbvy[i] * local + (0.045f - cby[i]) * e) * h;
            float rad = w * 0.014f * (1f - 0.4f * local);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54F);
            cv.drawCircle(x, y, rad, p);
            p.setColor(0xFFB8860B);
            cv.drawCircle(x, y, rad * 0.55f, p);
        }
        postInvalidateDelayed(28);
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

    private void drawShoe(Canvas cv, float w, float h) {
        float shW = w * 0.085f, shH = shW * 1.42f;
        float shX = w * 0.885f, shY = h * 0.20f;
        for (int i = 2; i >= 0; i--) {
            float ox = shX + i * shW * 0.06f, oy = shY - i * shH * 0.04f;
            rf.set(ox + shW * 0.03f, oy + shH * 0.035f, ox + shW * 1.03f, oy + shH * 1.035f);
            p.setStyle(Paint.Style.FILL); p.setColor(0x73000000);
            cv.drawRoundRect(rf, shW * 0.1f, shW * 0.1f, p);
            rf.set(ox - shW * 0.02f, oy - shH * 0.02f, ox + shW * 0.98f, oy + shH * 0.98f);
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, oy, 0, oy + shH, 0xFF8E1620, 0xFF3F0810, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rf, shW * 0.1f, shW * 0.1f, p);
            p.setAlpha(0xFF); p.setShader(null);
            // top sheen
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, oy, 0, oy + shH * 0.30f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rf.set(ox, oy, ox + shW * 0.96f, oy + shH * 0.30f);
            cv.drawRoundRect(rf, shW * 0.09f, shW * 0.09f, p);
            p.setAlpha(0xFF); p.setShader(null);
            // brass frame
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, shW * 0.030f)); p.setColor(0xFFD8B45A);
            rf.set(ox + shW * 0.05f, oy + shH * 0.03f, ox + shW * 0.91f, oy + shH * 0.93f);
            cv.drawRoundRect(rf, shW * 0.07f, shW * 0.07f, p);
            p.setStrokeWidth(Math.max(1f, shW * 0.012f)); p.setColor(0x66FFF3C4);
            rf.inset(shW * 0.03f, shW * 0.03f);
            cv.drawRoundRect(rf, shW * 0.05f, shW * 0.05f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }


    /** Builds the static casino-table backdrop once per size: layered felt lighting,
     *  grain, spotlight, wood rail with brass trim, stitched betting arc, gold markings. */
    private void buildBackground(int w, int h) {
        if (bgCache != null) bgCache.recycle();
        bgCache = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bgCache);

        // felt: key light high center, warm pool over the betting zone, deep edges
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w / 2f, h * 0.30f, h * 0.80f,
                new int[]{0xFF3CBE8A, 0xFF1E8A5C, 0xFF0B452E, 0xFF032017},
                new float[]{0f, 0.40f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w / 2f, h * 0.44f, w * 0.55f, 0x2EFFE9B0, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // spotlight cone from above
        p.setShader(new LinearGradient(0, 0, 0, h * 0.62f, 0x16FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        android.graphics.Path cone = new android.graphics.Path();
        cone.moveTo(w * 0.30f, 0); cone.lineTo(w * 0.70f, 0);
        cone.lineTo(w * 0.94f, h * 0.62f); cone.lineTo(w * 0.06f, h * 0.62f);
        cone.close();
        c.drawPath(cone, p);
        p.setShader(null);

        // felt grain: two dot scales + faint long brush strokes (fixed seed)
        if (texX == null) {
            java.util.Random tr = new java.util.Random(20241030L);
            texX = new float[340]; texY = new float[340]; texL = new boolean[340];
            for (int i = 0; i < 340; i++) { texX[i] = tr.nextFloat(); texY[i] = tr.nextFloat(); texL[i] = tr.nextBoolean(); }
        }
        float dotR = Math.max(1f, w * 0.0012f);
        for (int i = 0; i < 340; i++) {
            p.setColor(texL[i] ? 0x0CFFFFFF : 0x16000000);
            c.drawCircle(texX[i] * w, texY[i] * h, (i % 5 == 0) ? dotR * 1.9f : dotR, p);
        }
        p.setStyle(Paint.Style.STROKE);
        java.util.Random br = new java.util.Random(77L);
        for (int i = 0; i < 6; i++) {
            float by = h * (0.18f + 0.12f * i);
            p.setStrokeWidth(w * 0.004f);
            p.setColor(br.nextBoolean() ? 0x09FFFFFF : 0x0C000000);
            c.drawArc(-w * 0.2f, by - h * 0.10f, w * 1.2f, by + h * 0.14f, 15, 150, false, p);
        }
        p.setStyle(Paint.Style.FILL);

        // vignette
        p.setShader(new RadialGradient(w / 2f, h * 0.46f, h * 0.72f,
                new int[]{0x00000000, 0x00000000, 0x52000000},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // wood rail: layered walnut frame with brass pinstripe
        float railW = w * 0.030f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(railW);
        p.setShader(new LinearGradient(0, 0, w * 0.7f, h, new int[]{0xFF5D3A28, 0xFF3E2723, 0xFF241611, 0xFF4A2E20},
                new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        rf.set(railW / 2, railW / 2, w - railW / 2, h - railW / 2);
        c.drawRoundRect(rf, w * 0.055f, w * 0.055f, p);
        p.setShader(null);
        // rail sheen, top-left
        p.setStrokeWidth(railW * 0.34f); p.setColor(0x22FFE9C4);
        rf.set(railW / 2, railW / 2, w - railW / 2, h - railW / 2);
        c.drawRoundRect(rf, w * 0.055f, w * 0.055f, p);
        // brass pinstripes
        p.setStrokeWidth(Math.max(2f, w * 0.0045f)); p.setColor(0xCCD8B45A);
        rf.set(railW + w * 0.005f, railW + w * 0.005f, w - railW - w * 0.005f, h - railW - w * 0.005f);
        c.drawRoundRect(rf, w * 0.048f, w * 0.048f, p);
        p.setStrokeWidth(Math.max(1.5f, w * 0.0025f)); p.setColor(0x66D8B45A);
        rf.set(railW + w * 0.011f, railW + w * 0.011f, w - railW - w * 0.011f, h - railW - w * 0.011f);
        c.drawRoundRect(rf, w * 0.043f, w * 0.043f, p);
        p.setStyle(Paint.Style.FILL);

        // betting arc: hairline gold + stitch dashes
        float arcR = w * 0.40f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.012f); p.setColor(0x1FFFD700);
        c.drawArc(w / 2f - arcR, h * 0.335f, w / 2f + arcR, h * 0.335f + arcR * 1.1f, 20, 140, false, p);
        p.setStrokeWidth(Math.max(2f, w * 0.005f)); p.setColor(0xB3FFD700);
        c.drawArc(w / 2f - arcR, h * 0.335f, w / 2f + arcR, h * 0.335f + arcR * 1.1f, 20, 140, false, p);
        float arcR2 = arcR * 1.09f;
        p.setStrokeWidth(Math.max(1.5f, w * 0.003f)); p.setColor(0x59FFD700);
        c.drawArc(w / 2f - arcR2, h * 0.325f, w / 2f + arcR2, h * 0.325f + arcR2 * 1.1f, 20, 140, false, p);
        // stitch dashes between the lines
        p.setStrokeWidth(Math.max(1.5f, w * 0.0028f)); p.setColor(0x40FFE9B0);
        float arcR3 = arcR * 1.045f;
        android.graphics.DashPathEffect dash = new android.graphics.DashPathEffect(new float[]{w * 0.018f, w * 0.014f}, 0);
        p.setPathEffect(dash);
        c.drawArc(w / 2f - arcR3, h * 0.330f, w / 2f + arcR3, h * 0.330f + arcR3 * 1.1f, 22, 136, false, p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);

        // felt markings in embossed gold with diamond separators
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true); p.setLetterSpacing(0.16f);
        p.setTextSize(h * 0.0165f);
        p.setColor(0x33000000);
        c.drawText("BLACKJACK PAYS 3 TO 2", w / 2f, h * 0.3335f + 2, p);
        p.setColor(0xD9FFD700);
        c.drawText("BLACKJACK PAYS 3 TO 2", w / 2f, h * 0.332f, p);
        p.setTextSize(h * 0.0105f); p.setColor(0xB8FFD700);
        c.drawText("\u2666", w / 2f - w * 0.30f, h * 0.331f, p);
        c.drawText("\u2666", w / 2f + w * 0.30f, h * 0.331f, p);
        p.setFakeBoldText(false); p.setLetterSpacing(0.10f);
        p.setTextSize(h * 0.0120f); p.setColor(0x7DFFD700);
        c.drawText("DEALER MUST STAND ON ALL 17", w / 2f, h * 0.353f, p);
        p.setLetterSpacing(0f);
    }

    // ---------- drawing helpers ----------
    private float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }
    private float easeOutBack(float t) { float c = 1.70158f; t -= 1f; return 1f + (c + 1f) * t * t * t + c * t * t; }

    private void drawCard(Canvas cv, int rankIdx, int suitIdx, float x, float y, float w, float h, boolean faceDown, float scaleX) {
        float cx = x + w / 2f;
        float dw = w * Math.abs(scaleX);
        if (dw < 2) return;
        float dx = cx - dw / 2f;
        boolean showBack = faceDown ^ (scaleX < 0);
        float rad = dw * 0.10f;
        // soft shadow
        rf.set(dx + dw * 0.03f, y + h * 0.035f, dx + dw + dw * 0.03f, y + h + h * 0.035f);
        p.setStyle(Paint.Style.FILL); p.setColor(0x66000000);
        cv.drawRoundRect(rf, rad, rad, p);
        // face / back body
        rf.set(dx, y, dx + dw, y + h);
        if (showBack) {
            drawBackFace(cv, dx, y, dw, h, rad, cosBack);
            return;
        }
        // face: warm ivory stock with beveled edge
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, y, 0, y + h, 0xFFFFFEFC, 0xFFF6F1E4, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, rad, rad, p);
        p.setAlpha(0xFF); p.setShader(null);
        // bevel: light top-left, shade bottom-right
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, dw * 0.012f));
        p.setColor(0xBFFFFFFF);
        cv.drawLine(dx + rad, y + 1, dx + dw - rad, y + 1, p);
        cv.drawLine(dx + 1, y + rad, dx + 1, y + h - rad, p);
        p.setColor(0x33000000);
        cv.drawLine(dx + rad, y + h - 1, dx + dw - rad, y + h - 1, p);
        cv.drawLine(dx + dw - 1, y + rad, dx + dw - 1, y + h - rad, p);
        p.setStrokeWidth(Math.max(1.5f, dw * 0.014f)); p.setColor(0xFF9C9690);
        cv.drawRoundRect(rf, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
        int suit = suitIdx;
        boolean red = suit == 1 || suit == 2;
        int ink = red ? 0xFFC62828 : 0xFF1B1B1B;
        boolean face = rankIdx >= 10; // J Q K
        p.setColor(ink); p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true);
        if (scaleX >= 0) {
            // corner indices
            p.setTextSize(w * 0.235f);
            cv.drawText(RANKS[rankIdx], dx + dw * 0.055f, y + h * 0.20f, p);
            p.setTextSize(w * 0.19f);
            cv.drawText(SUITS[suit], dx + dw * 0.055f, y + h * 0.365f, p);
            cv.save();
            cv.rotate(180f, cx, y + h / 2f);
            p.setTextSize(w * 0.235f);
            cv.drawText(RANKS[rankIdx], dx + dw * 0.055f, y + h * 0.20f, p);
            p.setTextSize(w * 0.19f);
            cv.drawText(SUITS[suit], dx + dw * 0.055f, y + h * 0.365f, p);
            cv.restore();
            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.CENTER);
            if (face) {
                // face cards: ivory medallion, double gold ring, rank + crown
                float mr = w * 0.24f, my = y + h * 0.575f;
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(0xFF); p.setShader(new RadialGradient(cx - mr * 0.3f, my - mr * 0.35f, mr * 1.9f, 0xFFFFFDF6, 0xFFEFE3C4, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, my, mr, p);
                p.setAlpha(0xFF); p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(mr * 0.10f); p.setColor(0xFFD4AF37);
                cv.drawCircle(cx, my, mr * 0.90f, p);
                p.setStrokeWidth(mr * 0.045f); p.setColor(0xFFEFE0B8);
                cv.drawCircle(cx, my, mr * 0.76f, p);
                p.setStyle(Paint.Style.FILL);
                p.setFakeBoldText(true);
                p.setColor(ink); p.setTextSize(mr * 1.05f);
                cv.drawText(RANKS[rankIdx], cx, my + mr * 0.36f, p);
                p.setTextSize(mr * 0.52f); p.setColor(0xFFB8860B);
                cv.drawText("\u265B", cx, my - mr * 0.60f, p);
                p.setFakeBoldText(false);
            } else if (rankIdx == 0) {
                // ace: one large elegant pip
                p.setTextSize(w * 0.58f);
                cv.drawText(SUITS[suit], cx, y + h * 0.75f, p);
            } else {
                // number cards: classic pip layouts
                int n = rankIdx + 1; // 2..10
                float pip = n >= 7 ? w * 0.125f : w * 0.155f;
                float xl = dx + dw * 0.325f, xr = dx + dw * 0.675f, xc = cx;
                float T0 = y + h * 0.390f, T1 = y + h * 0.475f, T2 = y + h * 0.560f, T3 = y + h * 0.645f, T4 = y + h * 0.730f;
                float TH0 = y + h * 0.4325f, TH1 = y + h * 0.6875f;
                p.setTextSize(pip);
                java.util.List<float[]> pts = new java.util.ArrayList<>();
                switch (n) {
                    case 2: pts.add(new float[]{xc, T0, 0}); pts.add(new float[]{xc, T4, 1}); break;
                    case 3: pts.add(new float[]{xc, T0, 0}); pts.add(new float[]{xc, T2, 0}); pts.add(new float[]{xc, T4, 1}); break;
                    case 4: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 5: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, T2, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 6: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xl, T2, 0}); pts.add(new float[]{xr, T2, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 7: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, T1, 0}); pts.add(new float[]{xl, T2, 0}); pts.add(new float[]{xr, T2, 0}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 8: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, T1, 0}); pts.add(new float[]{xl, T2, 0}); pts.add(new float[]{xr, T2, 0}); pts.add(new float[]{xc, T3, 1}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    case 9: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xl, T1, 0}); pts.add(new float[]{xr, T1, 0}); pts.add(new float[]{xc, T2, 0}); pts.add(new float[]{xl, T3, 1}); pts.add(new float[]{xr, T3, 1}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                    default: pts.add(new float[]{xl, T0, 0}); pts.add(new float[]{xr, T0, 0}); pts.add(new float[]{xc, TH0, 0}); pts.add(new float[]{xl, T1, 0}); pts.add(new float[]{xr, T1, 0}); pts.add(new float[]{xl, T3, 1}); pts.add(new float[]{xr, T3, 1}); pts.add(new float[]{xc, TH1, 1}); pts.add(new float[]{xl, T4, 1}); pts.add(new float[]{xr, T4, 1}); break;
                }
                for (float[] pt : pts) {
                    float pxp = pt[0], pyp = pt[1];
                    boolean flip = pt[2] > 0.5f;
                    cv.save();
                    if (flip) cv.rotate(180f, pxp, pyp - pip * 0.30f);
                    cv.drawText(SUITS[suit], pxp, pyp, p);
                    cv.restore();
                }
            }
        }
        p.setFakeBoldText(false);
    }

    private int lighten(int color, float f) {
        int a = color & 0xFF000000;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * f));
        g = Math.min(255, (int) (g + (255 - g) * f));
        b = Math.min(255, (int) (b + (255 - b) * f));
        return a | (r << 16) | (g << 8) | b;
    }
    private int darken(int color, float f) {
        int a = color & 0xFF000000;
        int r = (int) (((color >> 16) & 0xFF) * (1f - f));
        int g = (int) (((color >> 8) & 0xFF) * (1f - f));
        int b = (int) ((color & 0xFF) * (1f - f));
        return a | (r << 16) | (g << 8) | b;
    }

    private void drawSideSpot(Canvas cv, int w, int h, boolean pp) {
        float cx = w * (pp ? 0.27f : 0.73f), cy = h * 0.415f, r = w * (pp ? 0.052f : 0.058f);
        boolean on = pp ? ppOn : tfOn;
        long stake = pp ? ppStake : tfStake;
        int kind = pp ? ppKind : tfKind;
        boolean inHand = phase != 0 && stake > 0;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(on || inHand ? w * 0.007f : w * 0.004f);
        p.setColor(on || inHand ? 0xCCFFD54F : 0x44FFD700);
        cv.drawCircle(cx, cy, r, p);
        if (on || inHand) {
            p.setStrokeWidth(2); p.setColor(0x55FFD54F);
            cv.drawCircle(cx, cy, r * 0.86f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setColor(on || inHand ? 0xFFFFD54F : 0x88FFD700);
        p.setTextSize(pp ? r * 0.52f : r * 0.40f);
        cv.drawText(pp ? "PP" : "21+3", cx, cy + (pp ? r * 0.18f : r * 0.14f), p);
        p.setFakeBoldText(false);
        // label under the spot
        String lbl; int col;
        String lbl2 = null; int col2 = 0;
        if (inHand) {
            if (kind >= 0) {
                long mult = pp ? PP_MULT[kind] : TF_MULT[kind];
                lbl = (pp ? PP_NAMES[kind] : TF_NAMES[kind]) + " +" + (stake * mult) + " profit";
                lbl2 = "+" + stake + " stake back";
                col = 0xFFFFD54F; col2 = 0xCCFFD54F;
            }
            else { lbl = "no win"; col = 0xFF7F8FB8; }
        } else if (on) {
            int n = sideCount();
            lbl = "STAKE " + (BETS[betIdx] / n); col = 0xCCFFD54F;
        } else {
            lbl = pp ? "x5-x25" : "x5-x100"; col = 0x66FFD700;
        }
        p.setTextSize(h * 0.013f); p.setColor(col); p.setFakeBoldText(inHand && kind >= 0);
        if (inHand) {
            // win line above the circle (the centered result badge owns the band
            // below); compact stake-back note under the circle, clear of the badge
            cv.drawText(lbl, cx, cy - r - h * 0.012f, p);
            if (lbl2 != null) {
                p.setFakeBoldText(false); p.setTextSize(h * 0.010f); p.setColor(col2);
                cv.drawText(lbl2, cx, cy + r + h * 0.018f, p);
            }
        } else cv.drawText(lbl, cx, cy + r + h * 0.018f, p);
        p.setFakeBoldText(false);
    }

    private void drawChip(Canvas cv, float cx, float cy, float r, int color, String label, boolean selected) {
        float edge = r * 0.22f;
        // drop shadow
        p.setStyle(Paint.Style.FILL); p.setColor(0x73000000);
        cv.drawCircle(cx + r * 0.06f, cy + edge + r * 0.12f, r, p);
        // 3D edge: dark band + stripe marks
        p.setColor(darken(color, 0.55f));
        rf.set(cx - r, cy - r + edge, cx + r, cy + r + edge);
        cv.drawOval(rf, p);
        p.setColor(0xE6F5F5F5);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * (15 + i * 30) / 180.0;
            float exx = cx + (float) Math.cos(a) * r * 0.985f;
            float eyy = cy + edge + (float) Math.sin(a) * r * 0.985f;
            if (Math.sin(a) > 0.15) {
                p.setStrokeWidth(r * 0.14f); p.setStyle(Paint.Style.STROKE);
                cv.drawLine(exx, eyy - edge * 0.55f, exx, eyy + edge * 0.55f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        // 3D body: radial gradient, light from top-left
        p.setAlpha(0xFF); p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.9f,
                lighten(color, 0.22f), darken(color, 0.38f), Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, p);
        p.setAlpha(0xFF); p.setShader(null);
        // edge pips: 8 white dashes around the rim
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.20f); p.setColor(0xFFF5F5F5);
        rf.set(cx - r * 0.88f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.88f);
        for (int i = 0; i < 8; i++)
            cv.drawArc(rf, i * 45f + 8f, 29f, false, p);
        p.setStyle(Paint.Style.FILL);
        // specular highlight
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.10f); p.setColor(0x40FFFFFF);
        rf.set(cx - r * 0.96f, cy - r * 0.96f, cx + r * 0.96f, cy + r * 0.96f);
        cv.drawArc(rf, 200f, 100f, false, p);
        // inner ring
        p.setStrokeWidth(r * 0.05f); p.setColor(0xB3FFFFFF);
        rf.set(cx - r * 0.60f, cy - r * 0.60f, cx + r * 0.60f, cy + r * 0.60f);
        cv.drawArc(rf, 0f, 360f, false, p);
        p.setStyle(Paint.Style.FILL);
        // inlay
        p.setAlpha(0xFF); p.setShader(new RadialGradient(cx, cy - r * 0.2f, r * 0.62f, 0xFFFFFFFF, 0xFFD8D8D8, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r * 0.52f, p);
        p.setAlpha(0xFF); p.setShader(null);
        if (label != null && !label.isEmpty()) {
            p.setColor(darken(color, 0.45f)); p.setTextSize(r * 0.56f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText(label, cx, cy + r * 0.20f, p);
            p.setFakeBoldText(false);
        }
        if (selected) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.13f); p.setColor(0x40FFD700);
            cv.drawCircle(cx, cy, r * 1.20f, p);
            p.setStrokeWidth(r * 0.07f); p.setColor(0xFFFFD700);
            cv.drawCircle(cx, cy, r * 1.14f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawPillButton(Canvas cv, float x, float y, float w, float h, String label,
                                int bgTop, int bgBottom, boolean enabled, boolean pressed, float pulse) {
        float sc = pressed ? 0.94f : 1f;
        float cx = x + w / 2f, cy = y + h / 2f;
        float dw = w * sc, dh = h * sc;
        // drop shadow under the button
        p.setStyle(Paint.Style.FILL); p.setColor(0x59000000);
        rf.set(cx - dw / 2 + 2, cy - dh / 2 + dh * 0.10f, cx + dw / 2 + 2, cy + dh / 2 + dh * 0.10f);
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
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
            // rich 3-stop vertical gradient
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom,
                    new int[]{lighten(bgTop, 0.22f), bgTop, bgBottom},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        } else {
            p.setAlpha(0xFF); p.setShader(null); p.setColor(0xFF37474F);
        }
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        p.setAlpha(0xFF); p.setShader(null);
        // inner bevel: bright top edge, shaded bottom edge
        if (enabled) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, dh * 0.030f));
            p.setColor(0x59FFFFFF);
            cv.drawLine(rf.left + dh * 0.45f, rf.top + dh * 0.09f, rf.right - dh * 0.45f, rf.top + dh * 0.09f, p);
            p.setColor(0x33000000);
            cv.drawLine(rf.left + dh * 0.45f, rf.bottom - dh * 0.09f, rf.right - dh * 0.45f, rf.bottom - dh * 0.09f, p);
            p.setStyle(Paint.Style.FILL);
        }
        // gold rim
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, dh * 0.045f));
        p.setColor(enabled ? 0xE6FFD700 : 0x55FFFFFF);
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        p.setStrokeWidth(Math.max(1f, dh * 0.018f)); p.setColor(enabled ? 0x66FFF3C4 : 0x33FFFFFF);
        rf.inset(dh * 0.05f, dh * 0.05f);
        cv.drawRoundRect(rf, dh * 0.45f, dh * 0.45f, p);
        rf.inset(-dh * 0.05f, -dh * 0.05f);
        p.setStyle(Paint.Style.FILL);
        // gloss highlight
        if (enabled) {
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, cy - dh / 2, 0, cy - dh * 0.05f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rf.set(cx - dw / 2 + dh * 0.12f, cy - dh / 2 + dh * 0.08f, cx + dw / 2 - dh * 0.12f, cy - dh * 0.06f);
            cv.drawRoundRect(rf, dh * 0.4f, dh * 0.4f, p);
            p.setAlpha(0xFF); p.setShader(null);
        }
        p.setColor(enabled ? 0xFFFFFFFF : 0xFF90A4AE);
        p.setTextSize(dh * 0.42f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        p.setLetterSpacing(0.08f);
        p.setShadowLayer(4, 0, 2, 0x80000000);
        cv.drawText(label, cx, cy + dh * 0.15f, p);
        p.clearShadowLayer();
        p.setLetterSpacing(0f);
        p.setFakeBoldText(false);
    }

    private void drawFancyText(Canvas cv, String text, float cx, float cy, float size, int topColor, int bottomColor) {
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size); p.setFakeBoldText(true);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.10f); p.setColor(0xAA000000);
        cv.drawText(text, cx, cy, p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, cy - size, 0, cy + size * 0.2f, topColor, bottomColor, Shader.TileMode.CLAMP));
        cv.drawText(text, cx, cy, p);
        p.setAlpha(0xFF); p.setShader(null); p.setFakeBoldText(false);
    }

    private void drawScoreBubble(Canvas cv, String text, float cx, float cy, float r, int bg) {
        p.setStyle(Paint.Style.FILL); p.setColor(0x66000000);
        cv.drawCircle(cx + 2, cy + 3, r, p);
        p.setAlpha(0xFF); p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.8f, lighten(bg, 0.30f), darken(bg, 0.35f), Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.10f); p.setColor(0xFFFFD700);
        cv.drawCircle(cx, cy, r * 0.92f, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        p.setTextSize(r * 0.85f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        p.setShadowLayer(3, 0, 1, 0x80000000);
        cv.drawText(text, cx, cy + r * 0.32f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    // ---------- main draw ----------
    @Override protected void onDraw(Canvas cv) {
        regen();
        long now = System.currentTimeMillis();
        int w = getWidth(), h = getHeight();
        boolean animating = false;

        // static table background (felt, lighting, rail, arcs, markings) cached as a bitmap
        if (bgCache == null || bgW != w || bgH != h) { buildBackground(w, h); bgW = w; bgH = h; }
        cv.drawBitmap(bgCache, 0, 0, null);

        // ambient dust motes drifting through the spotlight
        if (dustX == null) {
            java.util.Random dr2 = new java.util.Random(4242L);
            dustX = new float[16]; dustY = new float[16]; dustS = new float[16]; dustV = new float[16];
            for (int i = 0; i < 16; i++) { dustX[i] = dr2.nextFloat(); dustY[i] = dr2.nextFloat(); dustS[i] = 0.5f + dr2.nextFloat(); dustV[i] = 0.00012f + dr2.nextFloat() * 0.00025f; }
        }
        {
            boolean anyDust = false;
            for (int i = 0; i < 16; i++) {
                float dy = (dustY[i] - now * dustV[i]) % 1f; if (dy < 0) dy += 1f;
                float dxp = dustX[i] + 0.02f * (float) Math.sin(now / 1700.0 + i * 1.7);
                float fade = (float) Math.sin(dy * Math.PI);
                p.setColor(((int) (46 * fade * dustS[i]) << 24) | 0xFFF3D9);
                cv.drawCircle(dxp * w, dy * h * 0.72f, Math.max(1f, w * 0.0022f * dustS[i]), p);
                anyDust = true;
            }
            if (anyDust) animating = true;
        }


        // shoe (deck) at right edge
        drawShoe(cv, w, h);

        // ---- HUD ----
        // level badge + xp ring
        float lvR = w * 0.055f, lvX = w * 0.10f, lvY = h * 0.045f;
        p.setStyle(Paint.Style.FILL); p.setColor(0x55000000);
        cv.drawCircle(lvX + 2, lvY + 3, lvR, p);
        p.setAlpha(0xFF); p.setShader(new RadialGradient(lvX - lvR * 0.3f, lvY - lvR * 0.35f, lvR * 1.9f, 0xFF2E4E86, 0xFF081C3E, Shader.TileMode.CLAMP));
        cv.drawCircle(lvX, lvY, lvR, p);
        p.setAlpha(0xFF); p.setShader(null);
        // glass shine
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, lvY - lvR, 0, lvY, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(lvX, lvY, lvR * 0.96f, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(lvR * 0.09f); p.setColor(0xE6D8B45A);
        cv.drawCircle(lvX, lvY, lvR * 0.94f, p);
        p.setStrokeWidth(lvR * 0.035f); p.setColor(0x80FFF3C4);
        cv.drawCircle(lvX, lvY, lvR * 0.85f, p);
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
        // sound toggle
        float spX = w * 0.93f, spY = h * 0.042f, spR = w * 0.045f;
        p.setColor(0x66000000);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x55FFFFFF);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(spR * 0.9f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(GameSounds.isEnabled(getContext()) ? "\uD83D\uDD0A" : "\uD83D\uDD07", spX, spY + spR * 0.32f, p);
        // stats toggle (mini bar chart)
        float stX = w * 0.80f;
        p.setColor(0x66000000);
        cv.drawCircle(stX, spY, spR, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x55FFFFFF);
        cv.drawCircle(stX, spY, spR, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xCCFFD700);
        for (int bi = 0; bi < 3; bi++) {
            float bw = spR * 0.26f, bhv = spR * (0.45f + bi * 0.33f);
            rf.set(stX + (bi - 1) * spR * 0.42f - bw / 2, spY + spR * 0.55f - bhv, stX + (bi - 1) * spR * 0.42f + bw / 2, spY + spR * 0.55f);
            cv.drawRoundRect(rf, bw * 0.25f, bw * 0.25f, p);
        }


        // coin pill
        float pillW = w * 0.34f, pillH = h * 0.034f, pillX = w / 2f - pillW / 2f + w * 0.06f, pillY = h * 0.028f;
        rf.set(pillX, pillY, pillX + pillW, pillY + pillH);
        p.setColor(0x59000000);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, pillY, 0, pillY + pillH, 0xF21E332A, 0xF207130D, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setAlpha(0xFF); p.setShader(null);
        // glass shine top half
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, pillY, 0, pillY + pillH * 0.5f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rf.set(pillX + pillH * 0.10f, pillY + pillH * 0.08f, pillX + pillW - pillH * 0.10f, pillY + pillH * 0.48f);
        cv.drawRoundRect(rf, pillH * 0.4f, pillH * 0.4f, p);
        p.setAlpha(0xFF); p.setShader(null);
        rf.set(pillX, pillY, pillX + pillW, pillY + pillH);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(0xCCFFD700);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStrokeWidth(1f); p.setColor(0x55FFF3C4);
        rf.inset(2.5f, 2.5f);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        rf.inset(-2.5f, -2.5f);
        p.setStyle(Paint.Style.FILL);
        // coin icon
        float coinR = pillH * 0.36f, coinX = pillX + pillH * 0.55f, coinY = pillY + pillH / 2f;
        p.setAlpha(0xFF); p.setShader(new RadialGradient(coinX - coinR * 0.3f, coinY - coinR * 0.3f, coinR * 1.8f, 0xFFFFE082, 0xFFB8860B, Shader.TileMode.CLAMP));
        cv.drawCircle(coinX, coinY, coinR, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0xFF8D6E00);
        cv.drawCircle(coinX, coinY, coinR * 0.72f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF6D4C00); p.setTextSize(coinR * 1.1f); p.setFakeBoldText(true);
        cv.drawText("$", coinX, coinY + coinR * 0.38f, p);
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

        float cw = w * 0.185f, ch = cw * 1.42f;
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
            float sx = w * 0.95f, sy = h * 0.20f;
            float x = sx + (tx - sx) * te, y = sy + (h * 0.12f - sy) * te;
            boolean hide = (phase == 1 && i == 1);
            float scaleX = 1f;
            if (i == 1 && flipStartAt > 0 && now - flipStartAt < 420) {
                float ft = (now - flipStartAt) / 420f;
                scaleX = (float) Math.cos(ft * Math.PI);
                animating = true;
                hide = ft < 0.5f;
            }
            drawCard(cv, dealer.get(i), dealerSuits.get(i), x, y, cw, ch, hide, scaleX);
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
                float sx = w * 0.95f, sy = h * 0.20f;
                float x = sx + (tx - sx) * te, y = sy + (h * 0.52f - sy) * te;
                drawCard(cv, player.get(i), playerSuits.get(i), x, y, cw, ch, false, 1f);
            }
            int ps = score(player);
            int bubbleColor = ps > 21 ? 0xFFB71C1C : (ps == 21 ? 0xFF2E7D32 : 0xFF263238);
            drawScoreBubble(cv, "" + ps, px0 + pTotal + w * 0.045f, h * 0.565f, w * 0.036f, bubbleColor);
        }

        // ---- bet spot ----
        float spotR = w * 0.075f, spotX = w / 2f, spotY = h * 0.415f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w * 0.010f); p.setColor(0x26FFD700);
        cv.drawCircle(spotX, spotY, spotR, p);
        p.setStrokeWidth(3); p.setColor(0x66FFD700);
        cv.drawCircle(spotX, spotY, spotR * 0.97f, p);
        p.setStrokeWidth(2); p.setColor(0x40FFD700);
        cv.drawCircle(spotX, spotY, spotR * 1.12f, p);
        p.setStyle(Paint.Style.FILL);

        // ---- side bet spots (PP left, 21+3 right) ----
        drawSideSpot(cv, w, h, true);
        drawSideSpot(cv, w, h, false);
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
            if (!(resultKind >= 0 && now - resultAt < 2100)) {
                p.setColor(0xFFFFFFFF); p.setTextSize(h * 0.016f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
                cv.drawText("" + currentBet, spotX, spotY + spotR * 1.35f, p);
                p.setFakeBoldText(false);
            }
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
                case 1: text = "YOU WIN  +" + resultAmount; topC = 0xFFD7FFB0; botC = 0xFF43A047; break;
                case 2: text = "PUSH"; topC = 0xFFEEEEEE; botC = 0xFF9E9E9E; break;
                default: text = message.toUpperCase(); topC = 0xFFEF9A9A; botC = 0xFFC62828;
            }
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.455f);
            float tsz = h * 0.034f * (resultKind == 3 ? 1.25f : 1f);
            // radiating light rays behind the plaque on wins
            if (resultKind == 1 || resultKind == 3) {
                float rayT = Math.min(1f, (now - resultAt) / 900f);
                int rayN = resultKind == 3 ? 18 : 12;
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < rayN; i++) {
                    double a = Math.PI * 2 * i / rayN + now / 2600.0;
                    float inn = w * 0.10f, out = inn + w * (0.10f + 0.16f * rayT) * (0.7f + 0.3f * (float) Math.sin(i * 2.3));
                    int al = (int) (resultKind == 3 ? 90 : 55 * (1f - rayT * 0.5f));
                    p.setStrokeWidth(w * 0.010f);
                    p.setColor((al << 24) | 0xFFD700);
                    cv.drawLine(w / 2f + (float) Math.cos(a) * inn, h * 0.455f + (float) Math.sin(a) * inn * 0.5f,
                            w / 2f + (float) Math.cos(a) * out, h * 0.455f + (float) Math.sin(a) * out * 0.5f, p);
                }
                p.setStyle(Paint.Style.FILL);
            }
            // dark glass plaque with gold frame behind the result
            p.setTextSize(tsz); p.setFakeBoldText(true);
            float tw = p.measureText(text);
            p.setFakeBoldText(false);
            float plW = tw + w * 0.15f, plH = tsz * 1.75f;
            rf.set(w / 2f - plW / 2f, h * 0.455f - plH * 0.70f, w / 2f + plW / 2f, h * 0.455f + plH * 0.30f);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(0xFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xE6121F1A, 0xE6040B08, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rf, plH * 0.42f, plH * 0.42f, p);
            p.setAlpha(0xFF); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, tsz * 0.07f)); p.setColor(0xFFD8B45A);
            cv.drawRoundRect(rf, plH * 0.42f, plH * 0.42f, p);
            p.setStrokeWidth(Math.max(1.5f, tsz * 0.03f)); p.setColor(0x77FFE9B0);
            rf.inset(tsz * 0.09f, tsz * 0.09f);
            cv.drawRoundRect(rf, plH * 0.36f, plH * 0.36f, p);
            p.setStyle(Paint.Style.FILL);
            drawFancyText(cv, text, w / 2f, h * 0.455f, tsz, topC, botC);
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

            // MAX BET shortcut
            float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
            float bump = now - betBumpAt < 250 ? 1f + 0.25f * (1f - (now - betBumpAt) / 250f) : 1f;
            if (now - betBumpAt < 250) postInvalidateDelayed(28);
            float mr2 = chipR * 0.8f * bump;
            p.setStyle(Paint.Style.FILL); p.setColor(0x73000000);
            cv.drawCircle(mx + mr2 * 0.06f, h * 0.735f + mr2 * 0.12f, mr2, p);
            p.setAlpha(0xFF); p.setShader(new RadialGradient(mx - mr2 * 0.35f, h * 0.735f - mr2 * 0.4f, mr2 * 1.9f, 0xFFFFE082, 0xFFB8860B, Shader.TileMode.CLAMP));
            cv.drawCircle(mx, h * 0.735f, mr2, p);
            p.setAlpha(0xFF); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(mr2 * 0.08f); p.setColor(0xFF8D6E00);
            cv.drawCircle(mx, h * 0.735f, mr2 * 0.92f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF4A3200); p.setTextSize(chipR * 0.4f * bump); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("MAX", mx, h * 0.735f + chipR * 0.14f * bump, p);
            p.setFakeBoldText(false);
            p.setColor(0x99FFFFFF); p.setTextSize(h * 0.012f); p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("TAP A CHIP TO SET YOUR BET", w / 2f, h * 0.677f, p);
            drawPillButton(cv, w * 0.14f, h * 0.80f, w * 0.72f, bh,
                    sideCount() > 0 ? "D E A L   \u2022   " + totalWager() : "D E A L",
                    0xFF43A047, 0xFF1B5E20,
                    balance >= totalWager(), pressedBtn == 0, pulse);
        } else if (phase == 1) {
            boolean canDouble = player.size() == 2 && balance >= BETS[betIdx];
            drawPillButton(cv, w * 0.05f, h * 0.76f, w * 0.28f, bh, "HIT", 0xFF43A047, 0xFF1B5E20, true, pressedBtn == 1, 0);
            drawPillButton(cv, w * 0.36f, h * 0.76f, w * 0.28f, bh, "STAND", 0xFFE53935, 0xFF8E0000, true, pressedBtn == 2, 0);
            drawPillButton(cv, w * 0.67f, h * 0.76f, w * 0.28f, bh, "DOUBLE", 0xFF1E88E5, 0xFF0D47A1, canDouble, pressedBtn == 3, 0);
            if (!dealer.isEmpty()) {
                int hint = basicStrategyMove(player, dealer.get(0), canDouble);
                float hx = w * (hint == 0 ? 0.05f : hint == 1 ? 0.36f : 0.67f);
                float pl2 = 0.55f + 0.45f * (float) Math.sin(now / 300.0);
                rf.set(hx - w * 0.008f, h * 0.76f - bh * 0.08f, hx + w * 0.28f + w * 0.008f, h * 0.76f + bh * 1.08f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.006f);
                p.setColor(((int) (0xA6 * pl2) << 24) | 0x9FD8FF);
                cv.drawRoundRect(rf, bh * 0.55f, bh * 0.55f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(((int) (0xD8 * pl2) << 24) | 0xCFE8FF);
                p.setTextSize(h * 0.014f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
                cv.drawText("SUGGESTED", hx + w * 0.14f, h * 0.76f + bh + h * 0.024f, p);
                p.setFakeBoldText(false);
                animating = true;
            }
        } else {
            for (int i = 0; i < 3; i++) {
                float a = 0.35f + 0.65f * (float) (0.5 + 0.5 * Math.sin(now / 300.0 - i * 0.9));
                p.setColor(((int) (a * 255) << 24) | 0xFFD700);
                cv.drawCircle(w / 2f + (i - 1) * w * 0.045f, h * 0.785f, w * 0.010f, p);
            }
            animating = true;
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

        drawCoinBurst(cv, w, h, now);

        // ---- state machine tick ----
        if (phase == 2 && now >= dealerNextAt) {
            if (dealerShouldDraw(dealer)) {
                dealer.add(draw());
                dealerSuits.add(drawSuit());
                dealerCardT.add(now);
                dealerNextAt = now + 650;
            } else settle();
            invalidate();
        } else if (phase == 2 || animating) {
            postInvalidateDelayed(32);
        }

        if (statsOpen) drawStatsPanel(cv, w, h);
    }

    private void drawBackFace(Canvas cv, float dx, float y, float dw, float h, float rad, int variant) {
        int[] st = BACK_STYLE[Math.max(0, Math.min(variant, BACK_STYLE.length - 1))];
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, y, 0, y + h, st[0], st[1], Shader.TileMode.CLAMP));
        rf.set(dx, y, dx + dw, y + h);
        cv.drawRoundRect(rf, rad, rad, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, dw * 0.022f)); p.setColor(st[2]);
        rf.set(dx + dw * 0.07f, y + h * 0.045f, dx + dw * 0.93f, y + h * 0.955f);
        cv.drawRoundRect(rf, rad * 0.7f, rad * 0.7f, p);
        p.setStrokeWidth(Math.max(1f, dw * 0.012f)); p.setColor(st[3]);
        float step = dw * 0.18f;
        cv.save();
        cv.clipRect(dx + dw * 0.08f, y + h * 0.05f, dx + dw * 0.92f, y + h * 0.95f);
        for (float i = -h; i < h + dw; i += step) {
            cv.drawLine(dx + dw * 0.07f + i, y + h * 0.045f, dx + dw * 0.07f + i + h * 0.91f * 0.5f, y + h * 0.955f, p);
            cv.drawLine(dx + dw * 0.07f + i, y + h * 0.955f, dx + dw * 0.07f + i + h * 0.91f * 0.5f, y + h * 0.045f, p);
        }
        cv.restore();
        p.setStyle(Paint.Style.FILL);
    }

    /** Swatch rect (in panel coordinates) for a cosmetic variant; shared by draw and touch. */
    private void swatchRect(int w, int h, int v, RectF out) {
        float pw = w * 0.80f, ph = h * 0.58f, px = (w - pw) / 2f, py = h * 0.205f;
        float swH = ph * 0.10f, swW = swH / 1.42f, gap = pw * 0.03f;
        float sx0 = px + pw / 2f - (BACK_STYLE.length * swW + (BACK_STYLE.length - 1) * gap) / 2f;
        out.set(sx0 + v * (swW + gap), py + ph * 0.835f, sx0 + v * (swW + gap) + swW, py + ph * 0.835f + swH);
    }

    private void drawStatsPanel(Canvas cv, int w, int h) {
        p.setStyle(Paint.Style.FILL); p.setColor(0x90001008);
        cv.drawRect(0, 0, w, h, p);
        float pw = w * 0.80f, ph = h * 0.58f, px = (w - pw) / 2f, py = h * 0.205f;
        rf.set(px + 4, py + 6, px + pw + 4, py + ph + 6);
        p.setColor(0x73000000);
        cv.drawRoundRect(rf, pw * 0.06f, pw * 0.06f, p);
        rf.set(px, py, px + pw, py + ph);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, py, 0, py + ph, 0xF21E332A, 0xF207130D, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, pw * 0.06f, pw * 0.06f, p);
        p.setAlpha(0xFF); p.setShader(null);
        p.setAlpha(0xFF); p.setShader(new LinearGradient(0, py, 0, py + ph * 0.4f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rf.set(px + pw * 0.03f, py + ph * 0.03f, px + pw * 0.97f, py + ph * 0.38f);
        cv.drawRoundRect(rf, pw * 0.05f, pw * 0.05f, p);
        p.setAlpha(0xFF); p.setShader(null);
        rf.set(px, py, px + pw, py + ph);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(0xCCFFD700);
        cv.drawRoundRect(rf, pw * 0.06f, pw * 0.06f, p);
        p.setStrokeWidth(1.5f); p.setColor(0x55FFF3C4);
        rf.inset(4f, 4f);
        cv.drawRoundRect(rf, pw * 0.05f, pw * 0.05f, p);
        p.setStyle(Paint.Style.FILL);
        drawFancyText(cv, "LIFETIME STATS", w / 2f, py + ph * 0.13f, h * 0.024f, 0xFFFFE082, 0xFFFF8F00);
        String[][] rows = {
                {"Hands played", String.valueOf(stHands)},
                {"Hands won", String.valueOf(stWins)},
                {"Pushes", String.valueOf(stPushes)},
                {"Win rate", stHands > 0 ? String.format(java.util.Locale.US, "%.1f%%", 100f * stWins / stHands) : "-"},
                {"Blackjacks", String.valueOf(stBlackjacks)},
                {"Biggest win", stBiggest > 0 ? "+" + stBiggest : "-"},
                {"Current streak", String.valueOf(stStreak)},
                {"Best streak", String.valueOf(stBestStreak)},
        };
        float ry = py + ph * 0.20f, rstep = ph * 0.078f;
        for (int i = 0; i < rows.length; i++) {
            if (i % 2 == 0) {
                rf.set(px + pw * 0.05f, ry - rstep * 0.62f, px + pw * 0.95f, ry + rstep * 0.38f);
                p.setColor(0x14FFFFFF);
                cv.drawRoundRect(rf, rstep * 0.2f, rstep * 0.2f, p);
            }
            p.setColor(0xFFB9C8B8); p.setTextSize(h * 0.017f); p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(false);
            cv.drawText(rows[i][0], px + pw * 0.09f, ry, p);
            p.setColor(0xFFFFE082); p.setTextAlign(Paint.Align.RIGHT); p.setFakeBoldText(true);
            cv.drawText(rows[i][1], px + pw * 0.91f, ry, p);
            p.setFakeBoldText(false);
            ry += rstep;
        }
        p.setColor(0xFFFFE082); p.setTextSize(h * 0.016f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText("CARD BACK", w / 2f, py + ph * 0.80f, p);
        p.setFakeBoldText(false);
        RectF sw = new RectF();
        for (int v = 0; v < BACK_STYLE.length; v++) {
            swatchRect(w, h, v, sw);
            boolean unlocked = level >= BACK_UNLOCK_LEVEL[v];
            drawBackFace(cv, sw.left, sw.top, sw.width(), sw.height(), sw.width() * 0.10f, v);
            if (!unlocked) {
                p.setColor(0xB0001208);
                cv.drawRoundRect(sw, sw.width() * 0.10f, sw.width() * 0.10f, p);
                p.setColor(0xFFC9E8D4); p.setTextSize(sw.height() * 0.24f); p.setFakeBoldText(true);
                cv.drawText("Lv " + BACK_UNLOCK_LEVEL[v], sw.centerX(), sw.centerY() + sw.height() * 0.08f, p);
                p.setFakeBoldText(false);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(v == cosBack ? 4f : 1.5f);
            p.setColor(v == cosBack ? 0xFFFFD700 : 0x44FFFFFF);
            cv.drawRoundRect(sw, sw.width() * 0.10f, sw.width() * 0.10f, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0x66FFFFFF); p.setTextSize(h * 0.013f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText("tap a back to apply - tap elsewhere to close", w / 2f, py + ph * 0.975f, p);
    }

    private long lastTouchAt = System.currentTimeMillis();

    @Override public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        if (Math.hypot(x - w * 0.93f, y - getHeight() * 0.042f) <= w * 0.06f) {
            GameSounds.toggle(getContext());
            invalidate();
            return true;
        }
        if (Math.hypot(x - w * 0.80f, y - getHeight() * 0.042f) <= w * 0.06f) {
            if (e.getAction() == MotionEvent.ACTION_UP) { statsOpen = !statsOpen; invalidate(); }
            return true;
        }
        if (statsOpen) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                RectF sw = new RectF();
                boolean consumed = false;
                for (int v = 0; v < BACK_STYLE.length; v++) {
                    swatchRect(w, h, v, sw);
                    if (sw.contains(x, y)) {
                        consumed = true;
                        if (level >= BACK_UNLOCK_LEVEL[v]) {
                            cosBack = v;
                            getContext().getSharedPreferences("blackjack21", Context.MODE_PRIVATE)
                                    .edit().putInt("cos_back", v).apply();
                        }
                        break;
                    }
                }
                if (!consumed) statsOpen = false;
                invalidate();
            }
            return true;
        }

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
                if ((phase == 0 || phase == 3) && !levelUpShowing) {
                    if (!selectSideSpotAt(w, h, x, y)) selectChipAt(w, h, x, y);
                }
                return true;
            }
            if (levelUpShowing) {
                if (btn == 9) { levelUpShowing = false; levelUpPending = 0; levelUpReward = 0; invalidate(); }
                return true;
            }
            if (phase == 0 || phase == 3) {
                if (btn == 0 && balance >= totalWager()) deal();
            } else if (phase == 1) {
                if (btn == 1) hit();
                else if (btn == 2) stand();
                else if (btn == 3 && player.size() == 2 && balance >= BETS[betIdx]) doubleDown();
            }
            return true;
        }
        return true;
    }

    private boolean selectSideSpotAt(int w, int h, float x, float y) {
        float cy = h * 0.415f;
        if (Math.hypot(x - w * 0.27f, y - cy) <= w * 0.062f) {
            ppOn = !ppOn; GameSounds.play(getContext(), GameSounds.CHIP); invalidate();
            if (listener != null) listener.saveState();
            return true;
        }
        if (Math.hypot(x - w * 0.73f, y - cy) <= w * 0.068f) {
            tfOn = !tfOn; GameSounds.play(getContext(), GameSounds.CHIP); invalidate();
            if (listener != null) listener.saveState();
            return true;
        }
        return false;
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


    private void maxBet() {
        for (int i = BETS.length - 1; i >= 0; i--) {
            if (balance >= BETS[i]) {
                if (i != betIdx) {
                    betIdx = i;
                    GameSounds.play(getContext(), GameSounds.CHIP);
                    betBumpAt = System.currentTimeMillis();
                }
                invalidate();
                if (listener != null) listener.saveState();
                return;
            }
        }
    }

    private void selectChipAt(int w, int h, float x, float y) {
        float chipR = w * 0.068f, chipGap = w * 0.045f;
        float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
        float cx0 = w / 2f - rowW / 2f + chipR;
        float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
        if (Math.hypot(x - mx, y - h * 0.735f) <= chipR * 1.2f) { maxBet(); return; }
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
