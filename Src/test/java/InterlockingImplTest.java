import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterlockingImplTest {

    private Interlocking interlocking;

    @Before
    public void setUp() {
        interlocking = new InterlockingImpl();
    }

    // Helper runnable class to simulate a train's journey
    private class TrainRunner implements Runnable {
        private final int trainId;
        private final List<Integer> path;
        private final Interlocking interlock;
        private final CountDownLatch latch;
        public volatile Exception exception = null;

        public TrainRunner(int trainId, List<Integer> path, Interlocking interlock, CountDownLatch latch) {
            this.trainId = trainId;
            this.path = path;
            this.interlock = interlock;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                for (int section : path) {
                    interlock.waitToEnter(trainId, section);
                    // System.out.println("Train " + trainId + " entered section " + section + " at " + System.currentTimeMillis());
                    Thread.sleep(10); // Simulate time spent in section
                    interlock.leave(trainId, section);
                    // System.out.println("Train " + trainId + " left section " + section);
                }
            } catch (Exception e) {
                this.exception = e;
            } finally {
                latch.countDown();
            }
        }
    }

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
        // Passenger train S-bound on its track, Freight train N-bound on its track
        interlocking.newTrain(101, true, List.of(1, 2, 5, 8));
        interlocking.newTrain(201, false, List.of(11, 7, 3));

        CountDownLatch latch = new CountDownLatch(2);
        TrainRunner runner1 = new TrainRunner(101, List.of(1, 2, 5, 8), interlocking, latch);
        TrainRunner runner2 = new TrainRunner(201, List.of(11, 7, 3), interlocking, latch);
        
        new Thread(runner1).start();
        new Thread(runner2).start();

        assertTrue("Both trains should finish their paths", latch.await(5, TimeUnit.SECONDS));
        assertNull("Runner 1 should have no exceptions", runner1.exception);
        assertNull("Runner 2 should have no exceptions", runner2.exception);
    }

    @Test
    public void testSectionCollisionAvoidance() throws InterruptedException {
        // Two trains try to enter section 5 at the same time.
        interlocking.newTrain(101, true, List.of(1, 2, 5));
        interlocking.newTrain(102, true, List.of(4, 5));

        CountDownLatch latch = new CountDownLatch(2);
        TrainRunner runner1 = new TrainRunner(101, List.of(1, 2, 5), interlocking, latch);
        TrainRunner runner2 = new TrainRunner(102, List.of(4, 5), interlocking, latch);

        new Thread(runner1).start();
        new Thread(runner2).start();

        assertTrue("Both trains should eventually finish", latch.await(5, TimeUnit.SECONDS));
        assertNull("Runner 1 should have no exceptions", runner1.exception);
        assertNull("Runner 2 should have no exceptions", runner2.exception);
    }

    @Test
    public void testPassengerPriorityAtJunction() throws InterruptedException {
        // A freight train (201) and passenger train (101) will contend for the FP junction.
        // Passenger train wants 2->5, Freight train wants 3->7.
        // We expect the passenger train to get the junction first, even if the freight arrives slightly earlier.
        List<Integer> passengerPath = List.of(1, 2, 5, 8);
        List<Integer> freightPath = List.of(3, 7, 11);
        interlocking.newTrain(101, true, passengerPath); // Passenger
        interlocking.newTrain(201, false, freightPath); // Freight

        final CountDownLatch latch = new CountDownLatch(2);
        final List<String> entryLog = new ArrayList<>();

        // Custom runner to log entry times
        Runnable passengerRunner = () -> {
            try {
                interlocking.waitToEnter(101, 1);
                interlocking.leave(101, 1);
                
                interlocking.waitToEnter(101, 2); // Arrives at junction
                synchronized (entryLog) {
                    entryLog.add("passenger");
                }
                interlocking.leave(101, 2);

                // FIX: ADD THESE TWO LINES TO FULLY CLEAR THE JUNCTION
                interlocking.waitToEnter(101, 5);
                interlocking.leave(101, 5);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };

        Runnable freightRunner = () -> {
            try {
                // Give passenger a head start to ensure it's waiting
                Thread.sleep(5); 
                interlocking.waitToEnter(201, 3); // Arrives at junction
                synchronized (entryLog) {
                    entryLog.add("freight");
                }
                interlocking.leave(201, 3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };

        new Thread(freightRunner).start();
        new Thread(passengerRunner).start();

        latch.await(5, TimeUnit.SECONDS);

        assertEquals("Two trains should have entered the junction", 2, entryLog.size());
        assertEquals("Passenger train must enter the junction first due to priority", "passenger", entryLog.get(0));
        assertEquals("Freight train must enter the junction second", "freight", entryLog.get(1));
    }
}