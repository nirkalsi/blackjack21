package nir.com.blackjack21;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Local Robolectric renders (not device captures) of the side-bet UI:
 *  betting phase with both spots on, and a settled hand with side results. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class SideBetsCaptureTest {

    @SuppressWarnings("unchecked")
    private static void set(Object v, String f, Object val) throws Exception {
        Field fl = BlackjackView.class.getDeclaredField(f); fl.setAccessible(true); fl.set(v, val);
    }
    @SuppressWarnings("unchecked")
    private static <T> T get(Object v, String f) throws Exception {
        Field fl = BlackjackView.class.getDeclaredField(f); fl.setAccessible(true); return (T) fl.get(v);
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void capture() throws Exception {
        new File("/tmp/frames-bj-sidebets").mkdirs();
        // small-screen betting check (720x1480)
        BlackjackView vs = new BlackjackView(RuntimeEnvironment.getApplication());
        vs.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(1480, View.MeasureSpec.EXACTLY));
        vs.layout(0, 0, 720, 1480);
        set(vs, "balance", 8_900L);
        set(vs, "betIdx", 2);
        set(vs, "ppOn", Boolean.TRUE);
        set(vs, "tfOn", Boolean.TRUE);
        set(vs, "shownBalance", 8_900L);
        Bitmap bmps = Bitmap.createBitmap(720, 1480, Bitmap.Config.ARGB_8888);
        vs.draw(new Canvas(bmps));
        FileOutputStream os = new FileOutputStream("/tmp/frames-bj-sidebets/betting_small.png");
        bmps.compress(Bitmap.CompressFormat.PNG, 100, os); os.close();

        // state 1: betting, both side bets on
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(2243, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1080, 2243);
        set(v, "balance", 8_900L);
        set(v, "betIdx", 2);
        set(v, "ppOn", Boolean.TRUE);
        set(v, "tfOn", Boolean.TRUE);
        set(v, "shownBalance", 8_900L);
        Bitmap bmp = Bitmap.createBitmap(1080, 2243, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream o = new FileOutputStream("/tmp/frames-bj-sidebets/betting.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();

        // state 2: settled hand with a colored pair win and 21+3 no win (three sizes)
        settleState(v);
        Bitmap bmp2 = Bitmap.createBitmap(1080, 2243, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp2));
        FileOutputStream o2 = new FileOutputStream("/tmp/frames-bj-sidebets/result.png");
        bmp2.compress(Bitmap.CompressFormat.PNG, 100, o2); o2.close();
        assertTrue(new File("/tmp/frames-bj-sidebets/result.png").length() > 20000);

        int[][] sizes = {{720, 1480}, {1080, 1920}};
        for (int[] sz : sizes) {
            BlackjackView vr = new BlackjackView(RuntimeEnvironment.getApplication());
            vr.measure(View.MeasureSpec.makeMeasureSpec(sz[0], View.MeasureSpec.EXACTLY),
                      View.MeasureSpec.makeMeasureSpec(sz[1], View.MeasureSpec.EXACTLY));
            vr.layout(0, 0, sz[0], sz[1]);
            set(vr, "balance", 8_900L);
            set(vr, "betIdx", 2);
            set(vr, "ppOn", Boolean.TRUE);
            set(vr, "tfOn", Boolean.TRUE);
            settleState(vr);
            Bitmap br = Bitmap.createBitmap(sz[0], sz[1], Bitmap.Config.ARGB_8888);
            vr.draw(new Canvas(br));
            FileOutputStream or = new FileOutputStream("/tmp/frames-bj-sidebets/result_" + sz[0] + "x" + sz[1] + ".png");
            br.compress(Bitmap.CompressFormat.PNG, 100, or); or.close();
        }

        // persistent settled state after the 2.1s banner window (720x1480):
        // banner gone, all labels + wager text visible simultaneously
        BlackjackView vp = new BlackjackView(RuntimeEnvironment.getApplication());
        vp.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(1480, View.MeasureSpec.EXACTLY));
        vp.layout(0, 0, 720, 1480);
        set(vp, "balance", 8_900L);
        set(vp, "betIdx", 2);
        set(vp, "ppOn", Boolean.TRUE);
        set(vp, "tfOn", Boolean.TRUE);
        settleState(vp);
        set(vp, "resultAt", System.currentTimeMillis() - 5000); // banner window over
        Bitmap bp = Bitmap.createBitmap(720, 1480, Bitmap.Config.ARGB_8888);
        vp.draw(new Canvas(bp));
        FileOutputStream op = new FileOutputStream("/tmp/frames-bj-sidebets/result_post_banner_720x1480.png");
        bp.compress(Bitmap.CompressFormat.PNG, 100, op); op.close();
    }

    /** Deals, forces player 7h+7d (colored pair) vs dealer Kc+5s then a forced 4h
     *  draw to 19 (dealer must draw below 17), settles, and freezes all animation
     *  state for a clean still frame. */
    private static void settleState(BlackjackView v) throws Exception {
        set(v, "rng", new Random(9));
        Method deal = BlackjackView.class.getDeclaredMethod("deal"); deal.setAccessible(true);
        deal.invoke(v);
        List<Integer> pl = get(v, "player"), dl = get(v, "dealer");
        List<Integer> ps = get(v, "playerSuits"), ds = get(v, "dealerSuits");
        pl.set(0, 6); ps.set(0, 1); pl.set(1, 6); ps.set(1, 2);
        dl.set(0, 12); ds.set(0, 3); dl.set(1, 4); ds.set(1, 0);
        // dealer K+5 = 15 must draw (stands on all 17): force the 4 of hearts -> 19
        dl.add(3); ds.add(1);
        assertTrue(BlackjackView.score(dl) == 19);
        set(v, "ppKind", BlackjackView.evalPerfectPairs(6, 1, 6, 2));
        set(v, "tfKind", BlackjackView.evalTwentyOneThree(6, 1, 6, 2, 12, 3));
        List<Long> pt = get(v, "playerCardT"), dt = get(v, "dealerCardT");
        dt.add(System.currentTimeMillis());
        for (int i = 0; i < pt.size(); i++) pt.set(i, 0L);
        for (int i = 0; i < dt.size(); i++) dt.set(i, 0L);
        Method settle = BlackjackView.class.getDeclaredMethod("settle"); settle.setAccessible(true);
        settle.invoke(v);
        set(v, "shownBalance", get(v, "balance")); // complete the balance tween for the still frame
        set(v, "particlesOn", Boolean.FALSE);      // coin burst is a ~1s effect; show the settled table
        set(v, "flipStartAt", System.currentTimeMillis() - 5000);
        set(v, "resultAt", System.currentTimeMillis() - 1000); // banner fully scaled in (380ms), still inside its 2.1s window
    }
}
