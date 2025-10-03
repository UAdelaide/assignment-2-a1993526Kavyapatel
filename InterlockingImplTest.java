import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InterlockingImplTest {
    private Interlocking interlocking;
    @Before
    public void setUp() { interlocking = new InterlockingImpl(); }

    private class TrainRunner implements Runnable {
        private final int trainId;
        private final List<Integer> path;
        private final Interlocking interlock;
        private final CountDownLatch latch;
        public volatile Exception exception = null;

        public TrainRunner(int trainId, List<Integer> path, Interlocking interlocking, CountDownLatch latch) {
            this.trainId = trainId;
            this.path = path;
            this.interlock = interlocking;
            this.latch = latch;
        }

       @Override
        public void run() {
         try {
        for (int section : path) {
            interlock.waitToEnter(trainId, section);
            System.out.println("Train " + trainId + " entered section " + section); // This should NOT have //
            Thread.sleep(10);
            interlock.leave(trainId, section);
            System.out.println("Train " + trainId + " left section " + section); // This should NOT have //
        }
    } catch (Exception e) { this.exception = e; }
    finally { if (latch != null) latch.countDown(); }
   }
    }

    // --- Original 5 Tests ---
    @Test
    public void testSinglePassengerTrainSouthbound() throws InterruptedException {
        final int trainId = 101;
        List<Integer> path = List.of(1, 2, 5, 8);
        interlocking.newTrain(trainId, true, path);
        CountDownLatch latch = new CountDownLatch(1);
        TrainRunner runner = new TrainRunner(trainId, path, interlocking, latch);
        new Thread(runner).start();
        assertTrue("Train should finish its path", latch.await(2, TimeUnit.SECONDS));
        assertNull("Should be no exceptions", runner.exception);
    }

    @Test
    public void testSingleFreightTrainNorthbound() throws InterruptedException {
        final int trainId = 201;
        List<Integer> path = List.of(11, 7, 3);
        interlocking.newTrain(trainId, false, path);
        CountDownLatch latch = new CountDownLatch(1);
        TrainRunner runner = new TrainRunner(trainId, path, interlocking, latch);
        new Thread(runner).start();
        assertTrue("Train should finish its path", latch.await(2, TimeUnit.SECONDS));
        assertNull("Should be no exceptions", runner.exception);
    }

    @Test
    public void testTwoTrainsNoConflict() throws InterruptedException {
        interlocking.newTrain(101, true, List.of(1, 2));
        interlocking.newTrain(201, false, List.of(11, 7));
        CountDownLatch latch = new CountDownLatch(2);
        TrainRunner runner1 = new TrainRunner(101, List.of(1, 2), interlocking, latch);
        TrainRunner runner2 = new TrainRunner(201, List.of(11, 7), interlocking, latch);
        new Thread(runner1).start(); new Thread(runner2).start();
        assertTrue("Both trains should finish their paths", latch.await(5, TimeUnit.SECONDS));
        assertNull("Runner 1 should have no exceptions", runner1.exception);
        assertNull("Runner 2 should have no exceptions", runner2.exception);
    }

    @Test
    public void testSectionCollisionAvoidance() throws InterruptedException {
        interlocking.newTrain(101, true, List.of(1, 2, 5));
        interlocking.newTrain(102, true, List.of(4, 5));
        CountDownLatch latch = new CountDownLatch(2);
        TrainRunner r1 = new TrainRunner(101, List.of(1, 2, 5), interlocking, latch);
        TrainRunner r2 = new TrainRunner(102, List.of(4, 5), interlocking, latch);
        new Thread(r1).start(); new Thread(r2).start();
        assertTrue("Both trains should finish", latch.await(5, TimeUnit.SECONDS));
        assertNull("Runner 1 exceptions", r1.exception);
        assertNull("Runner 2 exceptions", r2.exception);
    }

    @Test
    public void testPassengerPriorityAtJunction() throws InterruptedException {
        List<Integer> passengerPath = List.of(1, 2, 5, 8);
        List<Integer> freightPath = List.of(3, 7, 11);
        interlocking.newTrain(101, true, passengerPath);
        interlocking.newTrain(201, false, freightPath);
        final CountDownLatch latch = new CountDownLatch(2);
        final List<String> entryLog = new ArrayList<>();
        Runnable passengerRunner = () -> {
            try {
                interlocking.waitToEnter(101, 1); interlocking.leave(101, 1);
                interlocking.waitToEnter(101, 2);
                synchronized (entryLog) { entryLog.add("passenger"); }
                interlocking.leave(101, 2);
                interlocking.waitToEnter(101, 5);
                interlocking.leave(101, 5);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { latch.countDown(); }
        };
        Runnable freightRunner = () -> {
            try {
                Thread.sleep(5);
                interlocking.waitToEnter(201, 3);
                synchronized (entryLog) { entryLog.add("freight"); }
                interlocking.leave(201, 3);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { latch.countDown(); }
        };
        new Thread(freightRunner).start(); new Thread(passengerRunner).start();
        latch.await(5, TimeUnit.SECONDS);
        assertEquals("Two trains should have entered the junction", 2, entryLog.size());
        assertEquals("Passenger train must enter first", "passenger", entryLog.get(0));
        assertEquals("Freight train must enter second", "freight", entryLog.get(1));
    }

    // --- Added 4 Tests ---

    @Test(expected = IllegalArgumentException.class)
    public void testNewTrainWithDuplicateIdThrowsException() {
        interlocking.newTrain(101, true, List.of(1, 2));
        interlocking.newTrain(101, false, List.of(11, 7));
    }

    @Test(expected = IllegalStateException.class)
    public void testTrainLeavingUnoccupiedSectionThrowsException() {
        interlocking.newTrain(101, true, List.of(1, 2));
        interlocking.leave(101, 1);
    }

    @Test
    public void testPotentialDeadlockScenario() throws InterruptedException {
        interlocking.newTrain(101, true, List.of(1, 2, 5));
        interlocking.newTrain(201, false, List.of(3, 7));
        CountDownLatch latch = new CountDownLatch(2);
        TrainRunner runner1 = new TrainRunner(101, List.of(1, 2, 5), interlocking, latch);
        TrainRunner runner2 = new TrainRunner(201, List.of(3, 7), interlocking, latch);
        new Thread(runner1).start();
        new Thread(runner2).start();
        assertTrue("Both trains should pass sequentially", latch.await(10, TimeUnit.SECONDS));
        assertNull(runner1.exception);
        assertNull(runner2.exception);
    }
    
    @Test
    public void testMultipleTrainsThroughJunction() throws InterruptedException {
        interlocking.newTrain(101, true, List.of(1, 2, 5));
        interlocking.newTrain(102, true, List.of(4, 6, 8));
        interlocking.newTrain(201, false, List.of(3, 7));
        interlocking.newTrain(202, false, List.of(11, 7, 3));
        CountDownLatch latch = new CountDownLatch(4);
        TrainRunner r101 = new TrainRunner(101, List.of(1, 2, 5), interlocking, latch);
        TrainRunner r102 = new TrainRunner(102, List.of(4, 6, 8), interlocking, latch);
        TrainRunner r201 = new TrainRunner(201, List.of(3, 7), interlocking, latch);
        TrainRunner r202 = new TrainRunner(202, List.of(11, 7, 3), interlocking, latch);
        new Thread(r101).start(); new Thread(r201).start();
        new Thread(r102).start(); new Thread(r202).start();
        assertTrue("All four trains should complete", latch.await(20, TimeUnit.SECONDS));
        assertNull(r101.exception); assertNull(r102.exception);
        assertNull(r201.exception); assertNull(r202.exception);
    }

    // --- New 3 Tests ---

    @Test
    public void testTrainChainReaction() throws InterruptedException {
        // Three trains are waiting for the same section (5)
        interlocking.newTrain(101, true, List.of(1, 5)); // Will get section 5 first
        interlocking.newTrain(102, true, List.of(2, 5)); // Will wait for 101
        interlocking.newTrain(103, true, List.of(4, 5)); // Will wait for 101 and 102
        CountDownLatch latch = new CountDownLatch(3);
        TrainRunner r1 = new TrainRunner(101, List.of(1, 5), interlocking, latch);
        TrainRunner r2 = new TrainRunner(102, List.of(2, 5), interlocking, latch);
        TrainRunner r3 = new TrainRunner(103, List.of(4, 5), interlocking, latch);
        new Thread(r1).start();
        Thread.sleep(5); // Ensure r1 gets the lock first
        new Thread(r2).start();
        new Thread(r3).start();
        assertTrue("All three trains should eventually get through the section", latch.await(15, TimeUnit.SECONDS));
        assertNull(r1.exception); assertNull(r2.exception); assertNull(r3.exception);
    }
    
    @Test
    public void testPassengerSplitJunctionUsage() throws InterruptedException {
        // Two passenger trains using non-conflicting parts of the passenger junction
        interlocking.newTrain(101, true, List.of(1, 2));
        interlocking.newTrain(102, true, List.of(4, 6));
        CountDownLatch latch = new CountDownLatch(2);
        TrainRunner r1 = new TrainRunner(101, List.of(1, 2), interlocking, latch);
        TrainRunner r2 = new TrainRunner(102, List.of(4, 6), interlocking, latch);
        new Thread(r1).start();
        new Thread(r2).start();
        assertTrue("Both passenger trains should run concurrently", latch.await(5, TimeUnit.SECONDS));
        assertNull(r1.exception);
        assertNull(r2.exception);
    }

    @Test
    public void testHeavyLoadStressTest() throws InterruptedException {
        int trainCount = 10;
        CountDownLatch latch = new CountDownLatch(trainCount);
        List<TrainRunner> runners = new ArrayList<>();

        for (int i = 0; i < trainCount; i++) {
            boolean isPassenger = i % 2 == 0;
            int id = isPassenger ? 100 + i : 200 + i;
            List<Integer> path;
            if (isPassenger) {
                path = i % 4 == 0 ? List.of(1, 2, 5, 8) : List.of(10, 9, 6, 2);
            } else {
                path = i % 3 == 0 ? List.of(3, 7, 11) : List.of(11, 7, 3);
            }
            interlocking.newTrain(id, isPassenger, path);
            TrainRunner runner = new TrainRunner(id, path, interlocking, latch);
            runners.add(runner);
            new Thread(runner).start();
        }

        assertTrue("All trains in heavy load should complete", latch.await(60, TimeUnit.SECONDS));
        for (TrainRunner runner : runners) {
            assertNull("Train " + runner.trainId + " should not have an exception", runner.exception);
        }
    }
}