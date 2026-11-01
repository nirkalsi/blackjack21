package nir.com.blackjack21;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;

/** Dealer draw-loop regression: dealer must keep drawing below 17 and stand on
 *  all 17 (soft 17 stands). Guards the onDraw state-machine predicate. */
public class DealerFlowTest {
    private static List<Integer> hand(int... ranks) {
        List<Integer> h = new ArrayList<>(); for (int r : ranks) h.add(r); return h;
    }

    @Test public void dealer15MustDraw_againstPlayer14() {
        // Regression: K+5=15 must NOT settle; dealer draws even against a weak 14.
        assertEquals(15, BlackjackView.score(hand(12, 4)));   // K(12) + 5(rank4)
        assertTrue(BlackjackView.dealerShouldDraw(hand(12, 4)));
    }

    @Test public void dealerDrawsUntilAtLeast17() {
        List<Integer> d = hand(12, 4);              // K5 = 15
        int[] forced = {3, 9};                      // draws a 4 (rank3) -> 19, stands; 10 unused
        int i = 0;
        while (BlackjackView.dealerShouldDraw(d)) d.add(forced[i++]);
        assertEquals(19, BlackjackView.score(d));
        assertEquals(1, i);                         // exactly one draw
    }

    @Test public void dealerStandsOnAll17() {
        assertFalse(BlackjackView.dealerShouldDraw(hand(12, 6)));       // K7 = 17 hard
        assertFalse(BlackjackView.dealerShouldDraw(hand(0, 5)));        // A6 = soft 17, stands
        assertTrue(BlackjackView.dealerShouldDraw(hand(12, 3)));        // K4 = 14, draws
        assertTrue(BlackjackView.dealerShouldDraw(hand(4, 5, 3)));      // 5+6+4 = 15, draws
        assertTrue(BlackjackView.dealerShouldDraw(hand(0, 4)));         // A5 = soft 16, draws
    }
}
