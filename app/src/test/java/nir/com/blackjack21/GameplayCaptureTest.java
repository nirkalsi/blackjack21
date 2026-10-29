package nir.com.blackjack21;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Captures a scripted gameplay sequence as PNG frames for a synthetic preview video.
 *  NOT a device capture: rendered locally via Robolectric native graphics. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class GameplayCaptureTest {

    private static void call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureBlackjackRound() throws Exception {
        new File("/tmp/frames-bj").mkdirs();
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        long t0 = System.currentTimeMillis();
        int frame = 0;
        boolean dealt = false, hit = false, stood = false;
        while (frame < 240) {                          // ~8s at 30fps
            long el = System.currentTimeMillis() - t0;
            if (!dealt && el > 500) { call(v, "deal"); dealt = true; }
            else if (dealt && !hit && el > 2500) { call(v, "hit"); hit = true; }
            else if (hit && !stood && el > 4500) { call(v, "stand"); stood = true; }
            v.draw(c);
            FileOutputStream fos = new FileOutputStream(String.format("/tmp/frames-bj/f%04d.png", frame++));
            bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
            fos.close();
            Thread.sleep(33);
        }
    }
}
