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
        // state 1: betting, both side bets on
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(2243, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1080, 2243);
        set(v, "balance", 8_900L);
        set(v, "betIdx", 2);
        set(v, "ppOn", Boolean.TRUE);
        set(v, "tfOn", Boolean.TRUE);
        Bitmap bmp = Bitmap.createBitmap(1080, 2243, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream o = new FileOutputStream("/tmp/frames-bj-sidebets/betting.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();

        // state 2: settled hand with a colored pair win and 21+3 no win
        set(v, "rng", new Random(9));
        Method deal = BlackjackView.class.getDeclaredMethod("deal"); deal.setAccessible(true);
        deal.invoke(v);
        List<Integer> pl = get(v, "player"), dl = get(v, "dealer");
        List<Integer> ps = get(v, "playerSuits"), ds = get(v, "dealerSuits");
        // force: player 8h 8d (colored pair), dealer Kc + 6s; re-evaluate sides
        pl.set(0, 6); ps.set(0, 1); pl.set(1, 6); ps.set(1, 2);
        dl.set(0, 12); ds.set(0, 3); dl.set(1, 4); ds.set(1, 0);
        set(v, "ppKind", BlackjackView.evalPerfectPairs(6, 1, 6, 2));
        set(v, "tfKind", BlackjackView.evalTwentyOneThree(6, 1, 6, 2, 12, 3));
        List<Long> pt = get(v, "playerCardT"), dt = get(v, "dealerCardT");
        for (int i = 0; i < pt.size(); i++) pt.set(i, 0L);
        for (int i = 0; i < dt.size(); i++) dt.set(i, 0L);
        Method settle = BlackjackView.class.getDeclaredMethod("settle"); settle.setAccessible(true);
        settle.invoke(v);
        set(v, "flipStartAt", System.currentTimeMillis() - 5000);
        Bitmap bmp2 = Bitmap.createBitmap(1080, 2243, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp2));
        FileOutputStream o2 = new FileOutputStream("/tmp/frames-bj-sidebets/result.png");
        bmp2.compress(Bitmap.CompressFormat.PNG, 100, o2); o2.close();
        assertTrue(new File("/tmp/frames-bj-sidebets/result.png").length() > 20000);
    }
}
