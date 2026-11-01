package nir.com.blackjack21;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Cosmetic card backs: unlock rules, persisted selection, renders. Local renders only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class CosmeticsTest {

    @Before public void clearPrefs() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("blackjack21", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void unlockLevelsAndPersistence() throws Exception {
        assertEquals(5, BlackjackView.backCount());
        assertEquals(1, BlackjackView.backUnlockLevel(0));
        assertEquals(9, BlackjackView.backUnlockLevel(4));
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("blackjack21", Context.MODE_PRIVATE).edit()
                .putInt("cos_back", 2).commit();
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        Field cb = BlackjackView.class.getDeclaredField("cosBack"); cb.setAccessible(true);
        assertEquals(2, cb.getInt(v));
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("blackjack21", Context.MODE_PRIVATE).edit()
                .putInt("cos_back", 99).commit();
        BlackjackView v2 = new BlackjackView(RuntimeEnvironment.getApplication());
        assertEquals(0, cb.getInt(v2));
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderPickerAndBacks() throws Exception {
        new File("/tmp/frames-bj-cos").mkdirs();
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        Field lv = BlackjackView.class.getDeclaredField("level"); lv.setAccessible(true);
        lv.setInt(v, 9);
        Field cb = BlackjackView.class.getDeclaredField("cosBack"); cb.setAccessible(true);
        cb.setInt(v, 3);
        Field so = BlackjackView.class.getDeclaredField("statsOpen"); so.setAccessible(true);
        so.setBoolean(v, true);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream fos = new FileOutputStream("/tmp/frames-bj-cos/picker.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
        fos.close();
        lv.setInt(v, 1);
        cb.setInt(v, 0);
        v.draw(new Canvas(bmp));
        FileOutputStream fos2 = new FileOutputStream("/tmp/frames-bj-cos/picker_locked.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 80, fos2);
        fos2.close();
    }
}
