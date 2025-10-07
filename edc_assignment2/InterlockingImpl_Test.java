import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class InterlockingImpl_Test {

    private Interlocking interlocking;

    @Before
    public void setUp() {
        interlocking = new InterlockingImpl();
    }

    // --- Add Train Tests ---

    @Test
    public void testAddTrainSuccessfully() {
        interlocking.addTrain("P1", 1, 9);
        assertEquals("P1", interlocking.getSection(1));
        assertEquals(1, interlocking.getTrain("P1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddTrainWithDuplicateName() {
        interlocking.addTrain("T1", 1, 9);
        interlocking.addTrain("T1", 3, 11); // Should fail
    }

    @Test(expected = IllegalStateException.class)
    public void testAddTrainToOccupiedSection() {
        interlocking.addTrain("T1", 1, 9);
        interlocking.addTrain("T2", 1, 8); // Should fail
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddTrainWithInvalidPath() {
        // A freight train cannot go to a passenger section
        interlocking.addTrain("F1", 3, 9);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testAddTrainWithInvalidSection() {
        interlocking.addTrain("T1", 99, 1);
    }


    // --- Get and Query Tests ---
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
    public void testGetTrainAfterExit() {
        interlocking.addTrain("P1", 1, 5);
        interlocking.moveTrains("P1"); // Moves to 5
        interlocking.moveTrains("P1"); // Exits from 5
        assertEquals(-1, interlocking.getTrain("P1"));
    }

    // --- Basic Movement Tests ---

    @Test
    public void testSingleTrainMoveAndExit() {
        interlocking.addTrain("F1", 3, 7);
        assertEquals(1, interlocking.moveTrains("F1")); // 3 -> 7
        assertEquals("F1", interlocking.getSection(7));
        assertNull(interlocking.getSection(3));
        
        assertEquals(1, interlocking.moveTrains("F1")); // Exits from 7
        assertNull(interlocking.getSection(7));
        assertEquals(-1, interlocking.getTrain("F1"));
    }
    
    @Test
    public void testMultipleTrainsMoveConcurrently() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("F1", 3, 11);
        
        // Both trains should be able to move as they are on separate tracks
        int moved = interlocking.moveTrains("P1", "F1");
        assertEquals(2, moved);
        assertEquals("P1", interlocking.getSection(5));
        assertEquals("F1", interlocking.getSection(7));
    }

    // --- Collision and Deadlock Tests ---

    @Test
    public void testCollisionAvoidance() {
        interlocking.addTrain("P1", 1, 9);
        interlocking.addTrain("P2", 5, 8); // P2 is blocking P1's path
        
        int moved = interlocking.moveTrains("P1"); // P1 cannot move into occupied section 5
        assertEquals(0, moved);
        assertEquals("P1", interlocking.getSection(1)); // P1 stays put
    }
    
    @Test
    public void testHeadOnDeadlock() {
        interlocking.addTrain("F1", 7, 11);
        interlocking.addTrain("F2", 11, 7);
        
        // F1 wants to go to 11, F2 wants to go to 7. Neither can move.
        // This setup is simplified, real path would be via 3.
        // Let's use a more realistic one: 3->7 and 11->7->3
        interlocking = new InterlockingImpl();
        interlocking.addTrain("F_A", 3, 11);
        interlocking.addTrain("F_B", 11, 3);
        
        interlocking.moveTrains("F_A"); // F_A moves 3 -> 7
        
        // Now F_A is at 7 wanting 11, F_B is at 11 wanting 7
        int moved = interlocking.moveTrains("F_A", "F_B");
        assertEquals(0, moved); // Neither can move
        assertEquals("F_A", interlocking.getSection(7));
        assertEquals("F_B", interlocking.getSection(11));
    }
    
    // --- Junction Priority ("Block") Tests ---
    
    @Test
    public void testFreightTrainBlockedByPassengerTrain() {
        interlocking.addTrain("P1", 1, 9); // Passenger train approaching junction
        interlocking.addTrain("F1", 3, 4); // Freight train wants to cross
        
        // P1 should move, F1 should be blocked because section 1 is occupied
        int moved = interlocking.moveTrains("P1", "F1");
        
        assertEquals(1, moved); // Only P1 moves
        assertEquals("P1", interlocking.getSection(5)); // P1 moved 1->5
        assertEquals("F1", interlocking.getSection(3)); // F1 is blocked and stays at 3
    }
    
    @Test
    public void testFreightTrainBlockedByPassengerTrainAtSection6() {
        // Setup train to be at section 6
        interlocking.addTrain("P1", 1, 9);
        interlocking.moveTrains("P1"); // 1->5
        interlocking.moveTrains("P1"); // 5->6
        assertEquals("P1", interlocking.getSection(6));
        
        interlocking.addTrain("F1", 3, 4); // Freight train wants to cross
        
        // P1 should move, F1 should be blocked because section 6 is occupied
        int moved = interlocking.moveTrains("P1", "F1");
        
        assertEquals(1, moved); // Only P1 moves
        assertEquals("P1", interlocking.getSection(10)); // P1 moved 6->10
        assertEquals("F1", interlocking.getSection(3));  // F1 is blocked
    }
    
    @Test
    public void testFreightTrainMovesWhenJunctionIsClear() {
        interlocking.addTrain("F1", 3, 4);
        
        // No passenger trains, junction is clear
        int moved = interlocking.moveTrains("F1");
        
        assertEquals(1, moved);
        assertEquals("F1", interlocking.getSection(4)); // F1 successfully crossed
    }
}
