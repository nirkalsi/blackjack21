package nir.com.blackjack21;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for the simplified basic-strategy hint.
 *  Ranks: 0=A, 1=2 ... 9=T, 10=J, 11=Q, 12=K. Moves: 0=HIT, 1=STAND, 2=DOUBLE. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BlackjackStrategyTest {

    private static List<Integer> hand(int... ranks) {
        List<Integer> h = new ArrayList<>();
        for (int r : ranks) h.add(r);
        return h;
    }

    @Test public void hardTotals() {
        assertEquals(0, BlackjackView.basicStrategyMove(hand(9, 5), 6, true));   // 16 vs 7: HIT
        assertEquals(1, BlackjackView.basicStrategyMove(hand(9, 5), 4, true));   // 16 vs 5: STAND
        assertEquals(1, BlackjackView.basicStrategyMove(hand(9, 1), 3, true));   // 12 vs 4: STAND
        assertEquals(0, BlackjackView.basicStrategyMove(hand(9, 1), 1, true));   // 12 vs 2: HIT
        assertEquals(1, BlackjackView.basicStrategyMove(hand(9, 6), 9, true));   // 17 vs T: STAND
        assertEquals(0, BlackjackView.basicStrategyMove(hand(3, 3), 4, true));   // 8 vs 5: HIT
    }

    @Test public void doubleTotals() {
        assertEquals(2, BlackjackView.basicStrategyMove(hand(5, 4), 9, true));   // 11 vs T: DOUBLE
        assertEquals(0, BlackjackView.basicStrategyMove(hand(5, 4), 0, true));   // 11 vs A: HIT
        assertEquals(2, BlackjackView.basicStrategyMove(hand(6, 2), 8, true));   // 10 vs 9: DOUBLE
        assertEquals(0, BlackjackView.basicStrategyMove(hand(6, 2), 9, true));   // 10 vs T: HIT
        assertEquals(2, BlackjackView.basicStrategyMove(hand(4, 3), 2, true));   // 9 vs 3: DOUBLE
        assertEquals(0, BlackjackView.basicStrategyMove(hand(4, 3), 1, true));   // 9 vs 2: HIT
        assertEquals(0, BlackjackView.basicStrategyMove(hand(5, 4), 9, false));  // 11 vs T, no double: HIT
    }

    @Test public void softTotals() {
        assertEquals(2, BlackjackView.basicStrategyMove(hand(0, 6), 2, true));   // A7 vs 3: DOUBLE
        assertEquals(0, BlackjackView.basicStrategyMove(hand(0, 6), 8, true));   // A7 vs 9: HIT
        assertEquals(1, BlackjackView.basicStrategyMove(hand(0, 6), 1, true));   // A7 vs 2: STAND
        assertEquals(1, BlackjackView.basicStrategyMove(hand(0, 6), 2, false));  // A7 vs 3, no double: STAND
        assertEquals(2, BlackjackView.basicStrategyMove(hand(0, 5), 3, true));   // A6 vs 4: DOUBLE
        assertEquals(0, BlackjackView.basicStrategyMove(hand(0, 5), 9, true));   // A6 vs T: HIT
        assertEquals(2, BlackjackView.basicStrategyMove(hand(0, 4), 4, true));   // A5 vs 5: DOUBLE
        assertEquals(1, BlackjackView.basicStrategyMove(hand(0, 7), 0, true));   // A8 vs A: STAND
        assertEquals(0, BlackjackView.basicStrategyMove(hand(0, 0), 8, true));   // AA (soft 12) vs 9: HIT
    }

    @Test public void multiCardSoft() {
        assertEquals(0, BlackjackView.basicStrategyMove(hand(0, 1, 2), 9, true)); // A2,3 = soft 16 vs T: HIT
        assertEquals(0, BlackjackView.basicStrategyMove(hand(0, 1, 4), 9, true)); // A2,5 = soft 18 vs T: HIT
    }
}
