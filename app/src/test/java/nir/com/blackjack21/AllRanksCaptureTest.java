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

/** Renders every rank at the real BJ card size to audit pip layouts and index clear zones.
 *  Local Robolectric render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class AllRanksCaptureTest {

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureAllRanks() throws Exception {
        new File("/tmp/frames-bj-ranks").mkdirs();
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        float cw = W * 0.185f, ch = cw * 1.42f;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1E6E46);
        Method dc = BlackjackView.class.getDeclaredMethod("drawCard", Canvas.class, int.class, int.class,
                float.class, float.class, float.class, float.class, boolean.class, float.class);
        dc.setAccessible(true);
        for (int r = 1; r <= 9; r++) { // 2..10
            float x = 20 + ((r - 1) % 5) * (cw + 12);
            float y = 40 + ((r - 1) / 5) * (ch + 30);
            dc.invoke(v, c, r, r % 4, x, y, cw, ch, false, 1f);
        }
        int[] faces = {0, 10, 11, 12};
        for (int i = 0; i < faces.length; i++) {
            float x = 20 + i * (cw + 12);
            float y = 40 + 2 * (ch + 30);
            dc.invoke(v, c, faces[i], i % 4, x, y, cw, ch, false, 1f);
        }
        FileOutputStream fos = new FileOutputStream("/tmp/frames-bj-ranks/all.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
        fos.close();
    }
}
