package nir.com.blackjack21;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

/** Pixel-render smoke: game view + full screen + reward/daily/missions/shop overlays. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class RenderSmokeTest {

    private void renderView(View v, int w, int h, String path) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        File parent = new File(path).getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        assertTrue(path + " too small", new File(path).length() > 5000);
    }

    private MainActivity activity() {
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
    }

    private void callPrivate(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    private static void resetAnimationState(android.view.View v) {
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) resetAnimationState(g.getChildAt(i));
        }
    }

    private void renderLatestDialog(String tag) throws Exception {
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("no dialog for " + tag, d);
        View content = d.getWindow().getDecorView();
        resetAnimationState(content); // dialogs enter with alpha/scale animation; render post-animation state
        content.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(1700, View.MeasureSpec.AT_MOST));
        // slack: shadow decor mis-measures wrapped TextViews; extra height avoids edge clipping
        renderView(content, Math.max(content.getMeasuredWidth(), 400),
                   Math.max(content.getMeasuredHeight(), 200) + 80, "/tmp/renders/" + tag + ".png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView1080() throws Exception {
        renderView(new BlackjackView(RuntimeEnvironment.getApplication()), 1080, 2243, "/tmp/renders/blackjack21-game-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView720() throws Exception {
        renderView(new BlackjackView(RuntimeEnvironment.getApplication()), 720, 1480, "/tmp/renders/blackjack21-game-720x1480.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderFullScreen1080() throws Exception {
        MainActivity a = activity();
        renderView(a.findViewById(android.R.id.content), 1080, 2243, "/tmp/renders/blackjack21-full-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderDailyBonus() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "maybeDailyBonus");
        renderLatestDialog("blackjack21-daily");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderMissions() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "showMissions");
        renderLatestDialog("blackjack21-missions");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderShop() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "showShop");
        renderLatestDialog("blackjack21-shop");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderOutOfCoinsRewardPrompt() throws Exception {
        MainActivity a = activity();
        a.onOutOfCoins();
        renderLatestDialog("blackjack21-outofcoins");
    }


    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderDealtHand() throws Exception {
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(2243, View.MeasureSpec.EXACTLY));
        callPrivate(v, "deal");
        { // accounting: 500 default balance minus default bet 10 must equal 490
            java.lang.reflect.Field bf = BlackjackView.class.getDeclaredField("balance");
            bf.setAccessible(true);
            org.junit.Assert.assertEquals("deal must debit exactly the displayed bet", 490L, bf.getLong(v));
        }
        // rewind card fly-in timestamps so the render shows the settled hand
        { // settle the animated balance display for the static capture
            java.lang.reflect.Field sb = BlackjackView.class.getDeclaredField("shownBalance");
            sb.setAccessible(true);
            sb.setLong(v, 490L);
        }
        for (String field : new String[]{"playerCardT", "dealerCardT"}) {
            java.lang.reflect.Field fl = BlackjackView.class.getDeclaredField(field);
            fl.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Long> ts = (java.util.List<Long>) fl.get(v);
            for (int i = 0; i < ts.size(); i++) ts.set(i, 0L);
        }
        renderView(v, 1080, 2243, "/tmp/renders/blackjack21-dealt.png");
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void coinBurstMidFlight() throws Exception {
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        v.coinBurst();
        java.lang.reflect.Field f = BlackjackView.class.getDeclaredField("coinBurstAt");
        f.setAccessible(true);
        f.setLong(v, System.currentTimeMillis() - 350);
        renderView(v, 1080, 2243, "/tmp/renders/blackjack21-coinburst.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void levelUpOverlay() throws Exception {
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        java.lang.reflect.Field fp = BlackjackView.class.getDeclaredField("levelUpPending"); fp.setAccessible(true); fp.setInt(v, 4);
        java.lang.reflect.Field fr = BlackjackView.class.getDeclaredField("levelUpReward"); fr.setAccessible(true); fr.setLong(v, 200L);
        java.lang.reflect.Field fs = BlackjackView.class.getDeclaredField("levelUpShowing"); fs.setAccessible(true); fs.setBoolean(v, true);
        java.lang.reflect.Field fa = BlackjackView.class.getDeclaredField("levelUpAt"); fa.setAccessible(true); fa.setLong(v, System.currentTimeMillis() - 1000);
        renderView(v, 1080, 2243, "/tmp/renders/blackjack21-levelup.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameViewSmall() throws Exception {
        renderView(new BlackjackView(RuntimeEnvironment.getApplication()), 960, 1600, "/tmp/renders/blackjack21-game-960x1600.png");
    }


    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void dealtHandSmall() throws Exception {
        BlackjackView v = new BlackjackView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        callPrivate(v, "deal");
        { // settle the animated balance display for the static capture
            java.lang.reflect.Field sb = BlackjackView.class.getDeclaredField("shownBalance");
            sb.setAccessible(true);
            sb.setLong(v, 490L);
        }
        for (String field : new String[]{"playerCardT", "dealerCardT"}) {
            java.lang.reflect.Field fl = BlackjackView.class.getDeclaredField(field);
            fl.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Long> ts = (java.util.List<Long>) fl.get(v);
            for (int i = 0; i < ts.size(); i++) ts.set(i, 0L);
        }
        renderView(v, 960, 1600, "/tmp/renders/blackjack21-dealt-960x1600.png");
    }

}
