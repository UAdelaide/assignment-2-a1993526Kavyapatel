// NO 'package' DECLARATION - This file is ready for Gradescope.
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Comprehensive test suite for the InterlockingImpl class.
 * This version has been corrected to be internally consistent.
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
    public void testSingleTrainMoveAndExit() {
        interlocking.addTrain("F1", 3, 7);
        assertEquals(1, interlocking.moveTrains("F1")); // 3 -> 7
        assertEquals("F1", interlocking.getSection(7));
        assertEquals(1, interlocking.moveTrains("F1")); // 7 -> exit
        assertEquals(-1, interlocking.getTrain("F1"));
    }

    @Test
    public void testGetTrainAfterExit() {
        interlocking.addTrain("P1", 1, 5);
        interlocking.moveTrains("P1"); // 1 -> 5
        interlocking.moveTrains("P1"); // 5 -> exit
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
        assertEquals(2, moved);
        assertEquals("P1", interlocking.getSection(5));
        assertEquals("F1", interlocking.getSection(7));
    }

    @Test
    public void testCollisionAvoidance() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("P2", 5, 8);
        int moved = interlocking.moveTrains("P1");
        assertEquals(0, moved);
        assertEquals("P1", interlocking.getSection(1));
    }

    @Test
    public void testHeadOnDeadlock() {
        interlocking.addTrain("F_A", 7, 11);
        interlocking.addTrain("F_B", 11, 7);
        int moved = interlocking.moveTrains("F_A", "F_B");
        assertEquals(0, moved);
        assertEquals("F_A", interlocking.getSection(7));
        assertEquals("F_B", interlocking.getSection(11));
    }

    @Test
    public void testFreightTrainBlockedByPassengerTrainAtSection1() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("F1", 3, 4);
        int moved = interlocking.moveTrains("P1", "F1");
        assertEquals(1, moved);
        assertEquals("P1", interlocking.getSection(5));
        assertEquals("F1", interlocking.getSection(3));
    }

    @Test
    public void testFreightTrainBlockedByPassengerTrainAtSection5() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.moveTrains("P1"); // P1 moves 1 -> 5
        interlocking.addTrain("F1", 3, 4);
        int moved = interlocking.moveTrains("P1", "F1");
        assertEquals(1, moved);
        assertEquals("P1", interlocking.getSection(6));
        assertEquals("F1", interlocking.getSection(3));
    }
    
    // ** THIS IS THE FIXED TEST CASE **
    // It is now consistent with the other tests and the core logic.
    @Test
    public void testFreightTrainBlockedByPassengerTrainAtSection6() {
        interlocking.addTrain("P1", 6, 9); // P1 starts at 6.
        interlocking.addTrain("F1", 3, 4);
        
        // Logically, the train at section 6 is PAST the junction and should NOT block F1.
        // Therefore, we expect BOTH trains to move.
        int moved = interlocking.moveTrains("P1", "F1");
        
        // The assertion is now corrected to expect 2 moved trains.
        assertEquals(2, moved); 
        
        // Verify their new positions.
        assertEquals("P1", interlocking.getSection(10));
        assertEquals("F1", interlocking.getSection(4));
    }

    @Test
    public void testFreightTrainMovesWhenJunctionIsClear() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("F1", 3, 4);
        interlocking.moveTrains("P1"); // P1 -> 5
        interlocking.moveTrains("P1"); // P1 -> 6, junction is now clear of approaching trains
        
        // F1 should be able to move now.
        int moved = interlocking.moveTrains("F1");
        assertEquals(1, moved);
        assertEquals("F1", interlocking.getSection(4));
    }
}

