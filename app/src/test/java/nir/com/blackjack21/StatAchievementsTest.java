package nir.com.blackjack21;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatAchievementsTest {

    @Before public void clearPrefs() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("blackjack21", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void statDrivenAchievements() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("blackjack21", Context.MODE_PRIVATE).edit()
                .putLong("st_best_streak", 5).putLong("st_blackjacks", 5).commit();
        MissionsManager mm = new MissionsManager(RuntimeEnvironment.getApplication(), null);
        MissionsManager.Item streak = null, bjs = null;
        for (MissionsManager.Item i : mm.achievements(1, 0)) {
            if (i.id.equals("ach7")) streak = i;
            if (i.id.equals("ach8")) bjs = i;
        }
        assertTrue(streak != null && streak.done());
        assertTrue(bjs != null && bjs.done());
        assertEquals(800, mm.claim(streak));
        // a freshly loaded list reflects the claim (claim() updates prefs, not the Item)
        for (MissionsManager.Item i : mm.achievements(1, 0))
            if (i.id.equals("ach7")) assertEquals(0, mm.claim(i));
        assertEquals(1200, mm.claim(bjs));
    }
}
