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

/** Store-screenshot capture driver: plays one scripted round on the live v6 design,
 *  pausing at photogenic states and saving native full-screen PNGs via instrumentation shell (collector pilot).
 *  Taps reuse the proven GameplayScriptTest fractions. Sample ads only (debug build);
 *  no ad content is clicked. */
@RunWith(AndroidJUnit4.class)
public class StoreCaptureScriptTest {
    private static final String TAG = "StoreCaptureBJ";
    private UiDevice d;
    private Rect v;
    private int shot = 0;

    private void tap(float fx, float fy) {
        v = CaptureStateProbe.bounds("BlackjackView");
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    // Plain executable calls only: UiAutomation uses Runtime.exec(String), whose
    // whitespace tokenizer does not understand shell quotes, pipes or &&.
    private byte[] shellStage(String stage, String... args) throws Exception {
        String command = CaptureShellTransport.command(args);
        Log.i(TAG, "STAGE_START " + stage + " command=" + command);
        android.os.ParcelFileDescriptor[] fds = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommandRwe(command);
        if (fds == null || fds.length != 3) throw new AssertionError("missing descriptors: " + stage);
        for (android.os.ParcelFileDescriptor fd : fds)
            if (fd == null) throw new AssertionError("null descriptor: " + stage);
        try (java.io.OutputStream stdin = new android.os.ParcelFileDescriptor.AutoCloseOutputStream(fds[1]);
             java.io.InputStream stdout = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fds[0]);
             java.io.InputStream stderr = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fds[2])) {
            stdin.close(); // No script or input is sent. Each command has plain argv only.
            CaptureShellTransport.Result result = CaptureShellTransport.collect(stdout, stderr);
            String err = new String(result.stderr, java.nio.charset.StandardCharsets.UTF_8);
            Log.i(TAG, "STAGE_DONE " + stage + " stdoutBytes=" + result.stdout.length + " stderr=" + err);
            if (!err.trim().isEmpty()) throw new AssertionError("shell stderr at " + stage + ": " + err);
            return result.stdout;
        } catch (Exception e) {
            throw new AssertionError("shell stage failed: " + stage, e);
        }
    }

    // BJ-only native PNG pilot. No new app permissions or services. Storage writes
    // and Firebase collector behavior still need device proof after transport repair.
    private void shot(String name) throws Exception {
        if (!name.matches("[a-z0-9_]+")) throw new AssertionError("unsafe screenshot name");
        shot++;
        String path = "/sdcard/screenshots/bj_store_" + shot + "_" + name + ".png";
        shellStage("mkdir_" + shot, "/system/bin/mkdir", "-p", "/sdcard/screenshots");
        // Remove any stale pose before capture. rm errors are surfaced separately.
        shellStage("remove_stale_" + shot, "/system/bin/rm", "-f", path);
        shellStage("screencap_" + shot, "/system/bin/screencap", "-p", path);
        String count = new String(shellStage("stat_" + shot, "/system/bin/stat", "-c", "%s", path),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        long size;
        try { size = Long.parseLong(count); }
        catch (NumberFormatException e) { throw new AssertionError("invalid stat size: " + count, e); }
        if (size <= 8 || size > 24 * 1024 * 1024) throw new AssertionError("invalid PNG size " + size);
        byte[] png = shellStage("readback_" + shot, "/system/bin/cat", path);
        if (png.length != size) throw new AssertionError("readback size mismatch for " + path);
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        for (int k = 0; k < signature.length; k++)
            if (png[k] != signature[k]) throw new AssertionError("not PNG: " + path);
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
        if (!"image/png".equals(bounds.outMimeType)
                || bounds.outWidth != d.getDisplayWidth() || bounds.outHeight != d.getDisplayHeight())
            throw new AssertionError("bad PNG bounds " + bounds.outWidth + "x" + bounds.outHeight
                    + " mime=" + bounds.outMimeType);
        Log.i(TAG, "PNG_VERIFIED " + path + " bytes=" + size + " " + bounds.outWidth + "x" + bounds.outHeight);
    }

    @Test public void captureStoreScreens() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // deterministic state: healthy balance, chip 25 selected, first round not yet played
        c.getSharedPreferences("blackjack21", 0).edit().clear()
                .putString("state", "5000;2;0;" + System.currentTimeMillis() + ";0;1;0")
                .putString("daily_last", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(new java.util.Date()))
                .commit();

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
            if (doNot != null) { sawForm = true; doNot.click(); cleanPolls = 0; }
            else if (confirm != null) { sawForm = true; confirm.click(); cleanPolls = 0; }
            else if (welcome != null || manageData != null || acceptAll != null) {
                sawForm = true;
                UiObject2 opts = d.findObject(By.text("Manage options"));
                if (opts != null) opts.click();
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
            throw new AssertionError("consent overlay still present before capture");

        Thread.sleep(1000);
        assertState("phase", 0);
        assertState("betIdx", 2);
        assertState("balance", 5000);
        
        tap(0.5f, 0.829f);
        long dealDeadline = System.currentTimeMillis() + 5000;
        int dealtPhase;
        do {
            dealtPhase = ((Number)CaptureStateProbe.read("BlackjackView", "phase")).intValue();
            if (dealtPhase == 1 || dealtPhase == 3) break;
            Thread.sleep(30);
        } while (System.currentTimeMillis() < dealDeadline);
        assertState("betIdx", 2);
        assertState("ppStake", 0);
        assertState("tfStake", 0);
        if (dealtPhase == 1) {
            assertState("balance", 4975);
            Log.i(TAG, "WAGER_VERIFIED main=25 sides=0 balance=5000->4975");
            Thread.sleep(900);
            assertUnobscured();
            // Stand on actual random cards, without forcing a win.
            tap(0.5f, 0.789f);
            awaitPhase(3, 15000);
        } else if (dealtPhase == 3) {
            // A natural 21 settles synchronously inside deal(). Do not require an
            // intermediate balance/phase that the game never exposes to the test.
            int kind = ((Number)CaptureStateProbe.read("BlackjackView", "resultKind")).intValue();
            if (kind != 3 && kind != 2) throw new AssertionError("unexpected immediate result " + kind);
            assertState("balance", kind == 3 ? 5037 : 5000); // game's integer 3:2 return on wager 25
            Log.i(TAG, "WAGER_VERIFIED natural settlement main=25 sides=0 resultKind=" + kind);
        } else throw new AssertionError("deal did not reach gameplay/result");
        assertState("handsDone", 1);
        if (((Number)CaptureStateProbe.read("BlackjackView", "resultKind")).intValue() < 0)
            throw new AssertionError("no settled result");
        Thread.sleep(450); // allow result plaque entrance, inside its 2100ms lifetime
        assertUnobscured();
        Log.i(TAG, "RESULT_VERIFIED " + CaptureStateProbe.read("BlackjackView", "message"));
        shot("settled_result");
        String foreground = d.getCurrentPackageName();
        if (!"nir.com.blackjack21".equals(foreground)) throw new AssertionError("foreground=" + foreground);
        Log.i(TAG, "capture complete: 1 asserted poses");
    }

    private void assertState(String field, long expected) {
        Object actual = CaptureStateProbe.read("BlackjackView", field);
        if (!(actual instanceof Number) || ((Number)actual).longValue() != expected)
            throw new AssertionError(field + " expected=" + expected + " actual=" + actual);
    }
    private void awaitPhase(int expected, long timeout) throws Exception {
        long until = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < until) {
            if (((Number)CaptureStateProbe.read("BlackjackView", "phase")).intValue() == expected) return;
            Thread.sleep(30);
        }
        throw new AssertionError("phase " + expected + " not reached");
    }
    private void assertUnobscured() {
        if (!"nir.com.blackjack21".equals(d.getCurrentPackageName())
                || d.findObject(By.textContains("Daily Bonus")) != null
                || d.findObject(By.textContains("Claim +")) != null
                || Boolean.TRUE.equals(CaptureStateProbe.read("BlackjackView", "levelUpShowing")))
            throw new AssertionError("game obscured by modal/overlay");
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!today.equals(c.getSharedPreferences("blackjack21", 0).getString("daily_last", "")))
            throw new AssertionError("daily state changed or local date crossed");
    }
}
