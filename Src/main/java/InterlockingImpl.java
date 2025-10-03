import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InterlockingImpl implements Interlocking {

    // Helper class to store train properties
    private static class Train {
        final int id;
        final boolean isPassenger;
        final List<Integer> path;
        int pathIndex = -1; // Current position in the path list

        Train(int id, boolean isPassenger, List<Integer> path) {
            this.id = id;
            this.isPassenger = isPassenger;
            this.path = path;
        }

        int getNextSection() {
            if (pathIndex + 1 < path.size()) {
                return path.get(pathIndex + 1);
            }
            return -1; // -1 indicates end of path
        }
    }

    // --- State Variables ---
    private final Map<Integer, Train> trains = new HashMap<>();
    private final Map<Integer, Integer> sectionOccupancy = new HashMap<>(); // sectionId -> trainId

    // Junctions represented as sets of conflicting sections
    private final Set<Integer> freightPassengerJunction = new HashSet<>(Set.of(2, 3, 5, 6, 7));
    private final Set<Integer> passengerSplitJunction = new HashSet<>(Set.of(1, 2, 4, 5, 6, 9, 10));

    // Track which train holds the lock on a junction
    private Integer freightPassengerJunctionLock = null;
    private Integer passengerSplitJunctionLock = null;

    @Override
    public synchronized void newTrain(int trainId, boolean isPassenger, List<Integer> path) {
        if (trains.containsKey(trainId)) {
            throw new IllegalArgumentException("Train with ID " + trainId + " already exists.");
        }
        trains.put(trainId, new Train(trainId, isPassenger, path));
    }

    @Override
    public synchronized boolean waitToEnter(int trainId, int sectionId) throws InterruptedException {
        Train train = trains.get(trainId);
        if (train == null) {
            throw new IllegalArgumentException("Train with ID " + trainId + " not registered.");
        }

        // Wait until it's safe to proceed
        while (!isSafeToEnter(train, sectionId)) {
            wait();
        }

        // --- Acquire Resources ---
        // 1. Occupy the section
        sectionOccupancy.put(sectionId, trainId);
        train.pathIndex++;

        // 2. Lock any junction this section is part of
        if (freightPassengerJunction.contains(sectionId)) {
            freightPassengerJunctionLock = trainId;
        }
        if (passengerSplitJunction.contains(sectionId)) {
            passengerSplitJunctionLock = trainId;
        }
        
        return true;
    }

    @Override
    public synchronized void leave(int trainId, int sectionId) {
        if (!sectionOccupancy.containsKey(sectionId) || sectionOccupancy.get(sectionId) != trainId) {
            throw new IllegalStateException("Train " + trainId + " cannot leave section " + sectionId + " which it does not occupy.");
        }

        // --- Release Resources ---
        // 1. Free the section
        sectionOccupancy.remove(sectionId);

        // 2. Unlock junctions if the train has cleared all conflicting sections
        Train train = trains.get(trainId);
        int nextSection = train.getNextSection();

        if (freightPassengerJunctionLock != null && freightPassengerJunctionLock == trainId) {
            // Release lock if the next section is outside the junction
            if (!freightPassengerJunction.contains(nextSection)) {
                freightPassengerJunctionLock = null;
            }
        }
        if (passengerSplitJunctionLock != null && passengerSplitJunctionLock == trainId) {
            // Release lock if the next section is outside the junction
            if (!passengerSplitJunction.contains(nextSection)) {
                passengerSplitJunctionLock = null;
            }
        }

        // Wake up all waiting threads to re-check conditions
        notifyAll();
    }

    /**
     * The core safety logic. Checks all conditions required for a train to enter a section.
     */
    private boolean isSafeToEnter(Train train, int sectionId) {
        // Condition 1: The target section must be empty.
        if (sectionOccupancy.containsKey(sectionId)) {
            return false;
        }

        boolean involvesFPJunction = freightPassengerJunction.contains(sectionId);
        boolean involvesPSJunction = passengerSplitJunction.contains(sectionId);

        // Condition 2: Check junction locks.
        // If the section is part of a junction, the junction must be free OR owned by the current train.
        if (involvesFPJunction && freightPassengerJunctionLock != null && freightPassengerJunctionLock != train.id) {
            return false;
        }
        if (involvesPSJunction && passengerSplitJunctionLock != null && passengerSplitJunctionLock != train.id) {
            return false;
        }

        // Condition 3: Passenger Priority Rule.
        // If a freight train wants the FP junction, it must check if any passenger trains are waiting for it.
        if (!train.isPassenger && involvesFPJunction) {
            if (isPassengerTrainWaitingForFPJunction()) {
                return false;
            }
        }

        return true;
    }


    /**
     * Helper method to enforce passenger priority.
     * @return true if there is at least one passenger train waiting to enter a section
     * that is part of the Freight-Passenger junction.
     */
    private boolean isPassengerTrainWaitingForFPJunction() {
        // This is a simplified check. A more robust implementation would inspect waiting threads.
        // For this model, we check if any registered passenger train's *next* section is a locked junction section.
        Set<Integer> waitingPassengerTrains = trains.values().stream()
                .filter(t -> t.isPassenger && !sectionOccupancy.containsValue(t.id)) // is a passenger and not currently moving
                .map(t -> t.id)
                .collect(Collectors.toSet());

        for (int trainId : waitingPassengerTrains) {
            Train train = trains.get(trainId);
            int nextSection = train.getNextSection();
            if (freightPassengerJunction.contains(nextSection)) {
                return true; // A passenger train is waiting for this junction.
            }
        }
        return false;
    }
}