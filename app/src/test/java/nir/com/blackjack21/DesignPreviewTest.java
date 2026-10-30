package nir.com.blackjack21;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Renders the REAL screen (native top row + BlackjackView canvas) in three
 *  game states as PNGs for design preview. Local render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class DesignPreviewTest {

    private static Field fld(Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n); f.setAccessible(true); return f;
    }
    private static void setLong(Object o, String n, long v) throws Exception { fld(o.getClass(), n).setLong(o, v); }
    private static void setInt(Object o, String n, int v) throws Exception { fld(o.getClass(), n).setInt(o, v); }
    private static void setBool(Object o, String n, boolean v) throws Exception { fld(o.getClass(), n).setBoolean(o, v); }
    @SuppressWarnings("unchecked")
    private static List<Integer> intList(Object o, String n) throws Exception { return (List<Integer>) fld(o.getClass(), n).get(o); }
    @SuppressWarnings("unchecked")
    private static List<Long> longList(Object o, String n) throws Exception { return (List<Long>) fld(o.getClass(), n).get(o); }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderStates() throws Exception {
        new File("/tmp/bj-design").mkdirs();
        int W = 1080, H = 2400;

        // real top button row from the real MainActivity code (no onCreate side effects)
        MainActivity act = new MainActivity();
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true);
        attach.invoke(act, RuntimeEnvironment.getApplication());
        Method br = MainActivity.class.getDeclaredMethod("buildButtonRow");
        br.setAccessible(true);
        View row = (View) br.invoke(act);
        // Robolectric native-graphics quirk: singleLine TextViews render blank here
        // (fine on device - see validated run frames). Flip it off for the preview only.
        android.widget.LinearLayout rowL = (android.widget.LinearLayout) row;
        for (int i = 0; i < rowL.getChildCount(); i++)
            ((android.widget.Button) rowL.getChildAt(i)).setSingleLine(false);

        BlackjackView v = new BlackjackView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF06281E);
        root.addView(row);
        root.addView(v, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        // shared cosmetic state
        setLong(v, "balance", 12480L);
        setLong(v, "shownBalance", 12480L);
        setInt(v, "betIdx", 2);
        setInt(v, "level", 7);
        setLong(v, "xp", 1600L);
        setLong(v, "handsDone", 41L);

        // ---- state 1: betting ----
        setInt(v, "phase", 0);
        setLong(v, "lastTouchAt", System.currentTimeMillis() - 6000);
        root.draw(c);
        save(bmp, "/tmp/bj-design/preview-1-betting.png");

        // ---- state 2: mid-hand (dealer hole card hidden) ----
        long old = System.currentTimeMillis() - 10000;
        intList(v, "player").clear(); intList(v, "dealer").clear();
        longList(v, "playerCardT").clear(); longList(v, "dealerCardT").clear();
        intList(v, "player").add(11); intList(v, "player").add(6);   // Q + 7 = 17
        intList(v, "dealer").add(8); intList(v, "dealer").add(4);    // 9 + hidden
        longList(v, "playerCardT").add(old); longList(v, "playerCardT").add(old);
        longList(v, "dealerCardT").add(old); longList(v, "dealerCardT").add(old);
        setInt(v, "phase", 1);
        setLong(v, "balance", 12455L); setLong(v, "shownBalance", 12455L);
        setLong(v, "lastTouchAt", System.currentTimeMillis());
        root.draw(c);
        save(bmp, "/tmp/bj-design/preview-2-midhand.png");

        // ---- state 3: blackjack result celebration ----
        intList(v, "player").clear(); intList(v, "dealer").clear();
        longList(v, "playerCardT").clear(); longList(v, "dealerCardT").clear();
        intList(v, "player").add(0); intList(v, "player").add(10);   // A + J = blackjack
        intList(v, "dealer").add(9); intList(v, "dealer").add(6);    // 10 + 7 = 17
        longList(v, "playerCardT").add(old); longList(v, "playerCardT").add(old);
        longList(v, "dealerCardT").add(old); longList(v, "dealerCardT").add(old);
        setInt(v, "phase", 3);
        setInt(v, "resultKind", 3);
        setLong(v, "resultAt", System.currentTimeMillis() - 420);
        setLong(v, "resultAmount", 62L);
        setBool(v, "particlesOn", true);
        setLong(v, "balance", 12542L); setLong(v, "shownBalance", 12542L);
        root.draw(c);
        save(bmp, "/tmp/bj-design/preview-3-blackjack.png");
    }

    private static void save(Bitmap bmp, String path) throws Exception {
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
        fos.close();
        System.out.println("WROTE " + path);
    }
}
