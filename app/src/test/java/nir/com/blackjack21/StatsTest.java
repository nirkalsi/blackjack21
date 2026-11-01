package nir.com.blackjack21;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Lifetime stats: persistence + panel render. Local Robolectric render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class StatsTest {

    @SuppressWarnings("unchecked")
    private static void forceHand(BlackjackView v, int[] playerRanks, int[] dealerRanks) throws Exception {
        Method deal = BlackjackView.class.getDeclaredMethod("deal");
        deal.setAccessible(true);
        deal.invoke(v);
        Field pF = BlackjackView.class.getDeclaredField("player"); pF.setAccessible(true);
        Field dF = BlackjackView.class.getDeclaredField("dealer"); dF.setAccessible(true);
        List<Integer> p = (List<Integer>) pF.get(v);
        List<Integer> d = (List<Integer>) dF.get(v);
        p.clear(); for (int r : playerRanks) p.add(r);
        d.clear(); for (int r : dealerRanks) d.add(r);
        Method settle = BlackjackView.class.getDeclaredMethod("settle");
        settle.setAccessible(true);
        settle.invoke(v);
    }

    @Before public void clearPrefs() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("blackjack21", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void statsAccumulateAndPersist() throws Exception {
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        Field betIdxF = BlackjackView.class.getDeclaredField("betIdx"); betIdxF.setAccessible(true);
        int[] bets = {5, 10, 25, 50};
        long bet = bets[betIdxF.getInt(v)];
        forceHand(v, new int[]{0, 10}, new int[]{9, 7});   // blackjack vs 18: BJ win
        forceHand(v, new int[]{9, 6}, new int[]{9, 9});    // 17 vs 20: loss
        forceHand(v, new int[]{9, 8}, new int[]{9, 8});    // 18 vs 18: push
        SharedPreferences sp = RuntimeEnvironment.getApplication()
                .getSharedPreferences("blackjack21", Context.MODE_PRIVATE);
        assertEquals(3, sp.getLong("st_hands", 0));
        assertEquals(1, sp.getLong("st_wins", 0));
        assertEquals(1, sp.getLong("st_blackjacks", 0));
        assertEquals(1, sp.getLong("st_pushes", 0));
        assertEquals(bet * 3 / 2, sp.getLong("st_biggest", 0));
        assertEquals(0, sp.getLong("st_streak", 0));  // push does not break... loss reset it
        BlackjackView v2 = new BlackjackView(RuntimeEnvironment.getApplication());
        Field stHandsF = BlackjackView.class.getDeclaredField("stHands"); stHandsF.setAccessible(true);
        assertEquals(3, stHandsF.getLong(v2));
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderStatsPanel() throws Exception {
        new File("/tmp/frames-bj-stats").mkdirs();
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        forceHand(v, new int[]{0, 10}, new int[]{9, 7});
        forceHand(v, new int[]{9, 8}, new int[]{9, 8});
        Field so = BlackjackView.class.getDeclaredField("statsOpen"); so.setAccessible(true);
        so.setBoolean(v, true);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream fos = new FileOutputStream("/tmp/frames-bj-stats/panel.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
        fos.close();
        // small-screen fit check
        int W2 = 720, H2 = 1480;
        BlackjackView v2 = new BlackjackView(RuntimeEnvironment.getApplication());
        v2.measure(View.MeasureSpec.makeMeasureSpec(W2, View.MeasureSpec.EXACTLY),
                   View.MeasureSpec.makeMeasureSpec(H2, View.MeasureSpec.EXACTLY));
        v2.layout(0, 0, W2, H2);
        so.setBoolean(v2, true);
        Bitmap bmp2 = Bitmap.createBitmap(W2, H2, Bitmap.Config.ARGB_8888);
        v2.draw(new Canvas(bmp2));
        FileOutputStream fos2 = new FileOutputStream("/tmp/frames-bj-stats/panel_small.png");
        bmp2.compress(Bitmap.CompressFormat.PNG, 80, fos2);
        fos2.close();
    }
}
