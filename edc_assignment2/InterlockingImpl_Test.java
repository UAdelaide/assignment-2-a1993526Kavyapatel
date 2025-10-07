import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Comprehensive test suite for the InterlockingImpl class.
 * Covers basic functionality, error handling, and complex conflict scenarios.
 */
public class InterlockingImpl_Test {

    private Interlocking interlocking;

    @Before
    public void setUp() {
        interlocking = new InterlockingImpl();
    }
    
    // --- Basic Functionality Tests ---
    @Test
    public void testAddTrainSuccessfully() {
        interlocking.addTrain("P1", 1, 9);
        assertEquals("P1", interlocking.getSection(1));
        assertEquals(1, interlocking.getTrain("P1"));
    }
    @Test
    public void testGetSection() {
        assertNull(interlocking.getSection(1));
        interlocking.addTrain("P1", 1, 9);
        assertEquals("P1", interlocking.getSection(1));
    }
    @Test
    public void testGetTrain() {
        interlocking.addTrain("P1", 1, 9);
        assertEquals(1, interlocking.getTrain("P1"));
    }
    @Test
    public void testSingleTrainMoveAndExit() {
        interlocking.addTrain("F1", 3, 7);
        assertEquals(1, interlocking.moveTrains("F1")); // 3 -> 7
        assertEquals("F1", interlocking.getSection(7));
        assertNull(interlocking.getSection(3));
        assertEquals(1, interlocking.moveTrains("F1")); // 7 -> exit (at destination)
        assertNull(interlocking.getSection(7));
        assertEquals(-1, interlocking.getTrain("F1"));
    }
    @Test
    public void testGetTrainAfterExit() {
        interlocking.addTrain("P1", 1, 5);
        interlocking.moveTrains("P1"); // 1 -> 5
        interlocking.moveTrains("P1"); // 5 -> exit (at destination)
        assertEquals(-1, interlocking.getTrain("P1"));
    }

    // --- Error Handling Tests ---
    @Test(expected = IllegalArgumentException.class)
    public void testAddTrainWithDuplicateName() {
        interlocking.addTrain("T1", 1, 9);
        interlocking.addTrain("T1", 3, 11);
    }
    @Test(expected = IllegalStateException.class)
    public void testAddTrainToOccupiedSection() {
        interlocking.addTrain("T1", 1, 9);
        interlocking.addTrain("T2", 1, 8);
    }
    @Test(expected = IllegalArgumentException.class)
    public void testAddTrainWithInvalidPath() {
        // A freight train cannot pathfind to a passenger track.
        interlocking.addTrain("F1", 3, 9);
    }
    @Test(expected = IllegalArgumentException.class)
    public void testAddTrainWithInvalidSection() {
        interlocking.addTrain("T1", 99, 1);
    }
     @Test(expected = IllegalArgumentException.class)
    public void testGetNonExistentTrain() {
        interlocking.getTrain("ghost");
    }

    // --- Concurrency and Conflict Tests ---
    @Test
    public void testMultipleTrainsMoveConcurrently() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("F1", 3, 11);
        int moved = interlocking.moveTrains("P1", "F1");
        assertEquals(2, moved); // Both should move as paths are not conflicting.
        assertEquals("P1", interlocking.getSection(5));
        assertEquals("F1", interlocking.getSection(7));
    }
    @Test
    public void testCollisionAvoidance() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("P2", 5, 8); // P2 is blocking P1's next section.
        int moved = interlocking.moveTrains("P1");
        assertEquals(0, moved); // P1 cannot move into the occupied section 5.
        assertEquals("P1", interlocking.getSection(1));
    }
    @Test
    public void testHeadOnDeadlock() {
        interlocking.addTrain("F_A", 7, 11);
        interlocking.addTrain("F_B", 11, 7);
        int moved = interlocking.moveTrains("F_A", "F_B");
        assertEquals(0, moved); // Neither can move into the other's occupied spot.
        assertEquals("F_A", interlocking.getSection(7));
        assertEquals("F_B", interlocking.getSection(11));
    }
    @Test
    public void testFreightTrainBlockedByPassengerTrainAtSection1() {
        interlocking.addTrain("P1", 1, 9); // P1 is at the junction.
        interlocking.addTrain("F1", 3, 4); // F1 wants to cross.
        int moved = interlocking.moveTrains("P1", "F1");
        assertEquals(1, moved); // Only passenger train P1 moves.
        assertEquals("P1", interlocking.getSection(5)); // P1 moved away.
        assertEquals("F1", interlocking.getSection(3)); // F1 was blocked.
    }
    @Test
    public void testFreightTrainBlockedByPassengerTrainAtSection6() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.moveTrains("P1"); // 1 -> 5
        interlocking.moveTrains("P1"); // 5 -> 6
        assertEquals("P1", interlocking.getSection(6)); // P1 is now at the other junction point.
        interlocking.addTrain("F1", 3, 4);
        int moved = interlocking.moveTrains("P1", "F1");
        assertEquals(1, moved); // Only P1 moves.
        assertEquals("P1", interlocking.getSection(10)); // P1 moved away.
        assertEquals("F1", interlocking.getSection(3)); // F1 was blocked again.
    }
    @Test
    public void testFreightTrainMovesWhenJunctionIsClear() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("F1", 3, 4);
        
        // P1 moves past the junction, F1 is blocked.
        interlocking.moveTrains("P1", "F1");
        assertEquals("P1", interlocking.getSection(5));
        assertEquals("F1", interlocking.getSection(3));
        
        // P1 moves further away, clearing the junction completely.
        interlocking.moveTrains("P1"); 
        assertEquals("P1", interlocking.getSection(6));
        
        // Now that the junction is clear, F1 should be able to move.
        int moved = interlocking.moveTrains("F1");
        assertEquals(1, moved);
        assertEquals("F1", interlocking.getSection(4));
    }
}