package nir.com.blackjack21;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Side-bet math: exact enumerated category counts over the implemented sample space
 *  (independent rank 0..12 and suit 0..3 per card, with replacement), aggregate cap,
 *  settlement accounting, and toggle persistence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SideBetsTest {

    private static void set(Object v, String f, Object val) throws Exception {
        Field fl = BlackjackView.class.getDeclaredField(f); fl.setAccessible(true); fl.set(v, val);
    }
    @SuppressWarnings("unchecked")
    private static <T> T get(Object v, String f) throws Exception {
        Field fl = BlackjackView.class.getDeclaredField(f); fl.setAccessible(true); return (T) fl.get(v);
    }
    private static long num(Object v, String f) throws Exception { return ((Number) get(v, f)).longValue(); }

    @Test public void perfectPairsEnumeratedCounts() {
        int[] counts = new int[3]; int none = 0;
        for (int r0 = 0; r0 < 13; r0++) for (int s0 = 0; s0 < 4; s0++)
            for (int r1 = 0; r1 < 13; r1++) for (int s1 = 0; s1 < 4; s1++) {
                int k = BlackjackView.evalPerfectPairs(r0, s0, r1, s1);
                if (k < 0) none++; else counts[k]++;
            }
        assertEquals(52, counts[2]);   // perfect
        assertEquals(52, counts[1]);   // colored
        assertEquals(104, counts[0]);  // mixed
        assertEquals(2704 - 208, none);
        // house edge check: E[return] = sum(count*(mult+1))/2704
        double ev = (104 * 6.0 + 52 * 11.0 + 52 * 26.0) / 2704.0;
        assertTrue("PP house edge ~5.8%", Math.abs((1 - ev) - 0.0575) < 0.005);
    }

    @Test public void twentyOneThreeEnumeratedCounts() {
        int[] counts = new int[5]; int none = 0;
        for (int r0 = 0; r0 < 13; r0++) for (int s0 = 0; s0 < 4; s0++)
            for (int r1 = 0; r1 < 13; r1++) for (int s1 = 0; s1 < 4; s1++)
                for (int r2 = 0; r2 < 13; r2++) for (int s2 = 0; s2 < 4; s2++) {
                    int k = BlackjackView.evalTwentyOneThree(r0, s0, r1, s1, r2, s2);
                    if (k < 0) none++; else counts[k]++;
                }
        assertEquals(52, counts[4]);    // suited trips
        assertEquals(288, counts[3]);   // straight flush
        assertEquals(780, counts[2]);   // trips
        assertEquals(4320, counts[1]);  // straight
        assertEquals(8448, counts[0]);  // flush
        assertEquals(140608, none + counts[0] + counts[1] + counts[2] + counts[3] + counts[4]);
        // Q,K,A is a straight; K,A,2 is not
        assertEquals(1, BlackjackView.evalTwentyOneThree(11, 0, 12, 1, 0, 2));
        assertEquals(-1, BlackjackView.evalTwentyOneThree(12, 0, 0, 1, 1, 2));
        double ev = (8448 * 6.0 + 4320 * 10.0 + 780 * 31.0 + 288 * 41.0 + 52 * 101.0) / 140608.0;
        assertTrue("21+3 house edge ~3.9%", Math.abs((1 - ev) - 0.039) < 0.005);
    }

    @Test public void aggregateCapAndSettlement() throws Exception {
        for (int seed = 0; seed < 20; seed++) {
            BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                      android.view.View.MeasureSpec.makeMeasureSpec(2243, android.view.View.MeasureSpec.EXACTLY));
            v.layout(0, 0, 1080, 2243);
            set(v, "rng", new Random(seed * 31337 + 5));
            set(v, "balance", 50_000L);
            set(v, "betIdx", 3); // 50 main
            set(v, "ppOn", Boolean.TRUE);
            set(v, "tfOn", Boolean.TRUE);
            Method deal = BlackjackView.class.getDeclaredMethod("deal"); deal.setAccessible(true);
            deal.invoke(v);
            long ppStake = num(v, "ppStake"), tfStake = num(v, "tfStake");
            assertTrue("aggregate side stake <= main bet", ppStake + tfStake <= 50);
            assertEquals(25, ppStake); assertEquals(25, tfStake);
            long afterDeal = num(v, "balance");
            assertEquals(50_000 - 50 - 25 - 25, afterDeal);

            // force settle: bust the player via reflection-free path - call settle directly
            Method settle = BlackjackView.class.getDeclaredMethod("settle"); settle.setAccessible(true);
            settle.invoke(v);
            long bal = num(v, "balance");
            int ppKind = (int) num(v, "ppKind"), tfKind = (int) num(v, "tfKind");
            long expected = afterDeal + (ppKind >= 0 ? ppStake * (BlackjackView.PP_MULT[ppKind] + 1) : 0)
                                      + (tfKind >= 0 ? tfStake * (BlackjackView.TF_MULT[tfKind] + 1) : 0);
            // main outcome may add too; recompute main from hands
            List<Integer> player = get(v, "player"), dealer = get(v, "dealer");
            int ps = BlackjackView.score(player), ds = BlackjackView.score(dealer);
            boolean pBJ = player.size() == 2 && ps == 21, dBJ = dealer.size() == 2 && ds == 21;
            long main;
            if (ps > 21 || (dBJ && !pBJ)) main = 0;
            else if (pBJ && !dBJ) main = 50 + 75;
            else if (ds > 21 || ps > ds) main = 100;
            else if (ps == ds) main = 50;
            else main = 0;
            assertEquals(expected + main, bal);
        }
    }

    @Test public void togglesPersist() throws Exception {
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        set(v, "ppOn", Boolean.TRUE);
        set(v, "tfOn", Boolean.TRUE);
        String ser = v.serialize();
        BlackjackView v2 = new BlackjackView(RuntimeEnvironment.getApplication());
        v2.restore(ser);
        assertEquals(Boolean.TRUE, get(v2, "ppOn"));
        assertEquals(Boolean.TRUE, get(v2, "tfOn"));
        BlackjackView v3 = new BlackjackView(RuntimeEnvironment.getApplication());
        v3.restore("5000;1;0;0;0;1");
        assertEquals(Boolean.FALSE, get(v3, "ppOn"));
        assertEquals(Boolean.FALSE, get(v3, "tfOn"));
    }
}
