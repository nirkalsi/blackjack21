package nir.com.blackjack21;

import android.graphics.Rect;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Verifies the adaptive banner actually LOADS (AdMob SDK onAdLoaded, Google sample unit ID)
 *  and becomes visible after a real played round. No ad content is clicked.
 *
 *  Round taps reuse the proven GameplayScriptTest fractions (chip 25 -> DEAL -> HIT -> STAND);
 *  round completion is asserted via the persisted SharedPreferences state (handsDone >= 1)
 *  BEFORE any ad wait. Banner load is asserted via AdsManager.lastBannerLoadedAt plus a
 *  rendered ad WebView with real visible height. */
@RunWith(AndroidJUnit4.class)
public class BannerLoadScriptTest {
    private static final String TAG = "BannerCheckBJ";
    private UiDevice d;
    private Rect v;

    private void tap(float fx, float fy) {
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    @Test public void bannerLoadsAfterFirstRound() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // fresh state so the first round is really the first (banner attaches on round 1)
        c.getSharedPreferences("blackjack21", 0).edit().clear().commit();

        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage("nir.com.blackjack21");
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);

        // --- UMP consent (real production form; handle it, never bypass) ---
        long consentDeadline = System.currentTimeMillis() + 75000;
        int cleanPolls = 0;
        boolean sawForm = false;
        while (System.currentTimeMillis() < consentDeadline) {
            UiObject2 doNot = d.findObject(By.text("Do not consent"));
            UiObject2 confirm = d.findObject(By.text("Confirm choices"));
            UiObject2 welcome = d.findObject(By.textContains("asks for your consent"));
            UiObject2 manageData = d.findObject(By.text("Manage your data"));
            UiObject2 acceptAll = d.findObject(By.text("Accept all"));
            if (doNot != null) {
                sawForm = true;
                Log.i(TAG, "UMP welcome page - choosing 'Do not consent'");
                doNot.click();
                cleanPolls = 0;
            } else if (confirm != null) {
                sawForm = true;
                Log.i(TAG, "UMP preferences page - 'Confirm choices' with all toggles left off");
                confirm.click();
                cleanPolls = 0;
            } else if (welcome != null || manageData != null || acceptAll != null) {
                sawForm = true;
                UiObject2 opts = d.findObject(By.text("Manage options"));
                if (opts != null) { Log.i(TAG, "UMP page without refuse button - opening Manage options"); opts.click(); }
                cleanPolls = 0;
            } else {
                cleanPolls++;
                if (cleanPolls >= (sawForm ? 4 : 12)) break;
            }
            Thread.sleep(750);
        }
        if (d.findObject(By.text("Do not consent")) != null
                || d.findObject(By.text("Confirm choices")) != null
                || d.findObject(By.text("Accept all")) != null
                || d.findObject(By.textContains("asks for your consent")) != null
                || d.findObject(By.text("Manage your data")) != null)
            throw new AssertionError("consent overlay still present before gameplay");
        Log.i(TAG, "consent settled (form shown=" + sawForm + ")");

        // measure the game view area exactly as GameplayScriptTest does
        UiObject2 first = d.wait(Until.findObject(By.pkg("nir.com.blackjack21").clazz("android.widget.Button")), 30000);
        if (first == null) throw new AssertionError("top buttons not found");
        int dh = d.getDisplayHeight(), dw = d.getDisplayWidth(), top = 0;
        for (UiObject2 b : d.findObjects(By.pkg("nir.com.blackjack21").clazz("android.widget.Button"))) {
            Rect r = b.getVisibleBounds();
            if (r.centerY() < dh / 4) top = Math.max(top, r.bottom);
        }
        int navId = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = navId > 0 ? c.getResources().getDimensionPixelSize(navId) : 0;
        v = new Rect(0, top, dw, dh - nav);

        // play one real round (proven GameplayScriptTest sequence)
        Thread.sleep(1500);
        tap(0.30f, 0.735f); Thread.sleep(600);   // select chip 25
        tap(0.5f, 0.829f); Thread.sleep(3000);   // DEAL, cards animate
        tap(0.19f, 0.789f); Thread.sleep(2500);  // HIT
        tap(0.5f, 0.789f); Thread.sleep(4000);   // STAND, dealer plays, result banner
        tap(0.5f, 0.646f); Thread.sleep(1500);   // dismiss level-up overlay if shown

        // assert the round really completed before waiting on any ad
        String state = c.getSharedPreferences("blackjack21", 0).getString("state", "");
        if (state.isEmpty()) throw new AssertionError("no saved state after scripted round");
        long handsDone = Long.parseLong(state.split(";")[2]);
        if (handsDone < 1) throw new AssertionError("round did not complete, state=" + state);
        Log.i(TAG, "round completed (handsDone=" + handsDone + "); waiting for banner load");

        // bounded wait for the actual SDK load callback (fires after first round attach)
        long deadline = System.currentTimeMillis() + 90000;
        while (System.currentTimeMillis() < deadline) {
            if (AdsManager.lastBannerLoadedAt != 0) break;
            if (AdsManager.lastBannerError != null) Log.w(TAG, "load attempt failed: " + AdsManager.lastBannerError);
            Thread.sleep(2000);
        }
        if (AdsManager.lastBannerLoadedAt == 0)
            throw new AssertionError("banner did not load within 90s; lastError=" + AdsManager.lastBannerError);
        Log.i(TAG, "onAdLoaded fired, unit=" + AdsManager.lastBannerUnit);
        if (!"ca-app-pub-3940256099942544/9214589741".equals(AdsManager.lastBannerUnit))
            throw new AssertionError("unexpected banner unit: " + AdsManager.lastBannerUnit);

        // full visible banner bounds: an ad WebView with real height in the app window
        Thread.sleep(3000);
        UiObject2 web = d.wait(Until.findObject(By.pkg("nir.com.blackjack21").clazz("android.webkit.WebView")), 10000);
        if (web == null) throw new AssertionError("no rendered ad WebView in app window after onAdLoaded");
        Rect b = web.getVisibleBounds();
        Log.i(TAG, "ad WebView bounds=" + b);
        if (b.height() < 40) throw new AssertionError("ad view has no visible height: " + b);

        // dismiss possible daily-bonus dialog (do not click ad content) and screenshot
        UiObject2 close = d.findObject(By.text("Close"));
        if (close != null) close.click();
        Thread.sleep(1000);
        java.io.File shot = new java.io.File("/sdcard/Download/banner_loaded.png");
        d.takeScreenshot(shot);
        Log.i(TAG, "screenshot=" + shot + " exists=" + shot.exists());

        String pkg = d.getCurrentPackageName();
        if (!"nir.com.blackjack21".equals(pkg))
            throw new AssertionError("foreground=" + pkg + " (possible ad takeover - should not happen without clicks)");
        Log.i(TAG, "BANNER LOAD VERIFIED: loaded callback + rendered bounds " + b);
    }
}
