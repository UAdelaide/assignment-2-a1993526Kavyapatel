import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InterlockingImpl implements Interlocking {
    private static class Train {
        final int id;
        final boolean isPassenger;
        final List<Integer> path;
        int pathIndex = -1;

        Train(int id, boolean isPassenger, List<Integer> path) {
            this.id = id;
            this.isPassenger = isPassenger;
            this.path = path;
        }
        int getNextSection() {
            if (pathIndex + 1 < path.size()) return path.get(pathIndex + 1);
            return -1;
        }
    }

    private final Map<Integer, Train> trains = new HashMap<>();
    private final Map<Integer, Integer> sectionOccupancy = new HashMap<>();
    private final Set<Integer> freightPassengerJunction = new HashSet<>(Set.of(2, 3, 5, 6, 7));
    private final Set<Integer> passengerSplitJunction = new HashSet<>(Set.of(1, 2, 4, 5, 6, 9, 10));
    private Integer freightPassengerJunctionLock = null;
    private Integer passengerSplitJunctionLock = null;

    @Override
    public synchronized void newTrain(int trainId, boolean isPassenger, List<Integer> path) {
        if (trains.containsKey(trainId)) throw new IllegalArgumentException("Train with ID " + trainId + " already exists.");
        trains.put(trainId, new Train(trainId, isPassenger, path));
    }

    @Override
    public synchronized boolean waitToEnter(int trainId, int sectionId) throws InterruptedException {
        Train train = trains.get(trainId);
        if (train == null) throw new IllegalArgumentException("Train with ID " + trainId + " not registered.");
        while (!isSafeToEnter(train, sectionId)) wait();
        sectionOccupancy.put(sectionId, trainId);
        train.pathIndex++;
        if (freightPassengerJunction.contains(sectionId)) freightPassengerJunctionLock = trainId;
        if (passengerSplitJunction.contains(sectionId)) passengerSplitJunctionLock = trainId;
        return true;
    }

    @Override
    public synchronized void leave(int trainId, int sectionId) {
        if (!sectionOccupancy.getOrDefault(sectionId, -1).equals(trainId)) throw new IllegalStateException("Train " + trainId + " cannot leave section " + sectionId + " it does not occupy.");
        sectionOccupancy.remove(sectionId);
        Train train = trains.get(trainId);
        int nextSection = train.getNextSection();
        if (freightPassengerJunctionLock != null && freightPassengerJunctionLock.equals(trainId) && !freightPassengerJunction.contains(nextSection)) freightPassengerJunctionLock = null;
        if (passengerSplitJunctionLock != null && passengerSplitJunctionLock.equals(trainId) && !passengerSplitJunction.contains(nextSection)) passengerSplitJunctionLock = null;
        notifyAll();
    }

    private boolean isSafeToEnter(Train train, int sectionId) {
        // Condition 1: The target section must be empty.
        if (sectionOccupancy.containsKey(sectionId)) {
            return false;
        }

        boolean involvesFPJunction = freightPassengerJunction.contains(sectionId);
        boolean involvesPSJunction = passengerSplitJunction.contains(sectionId);

        // Condition 2: Check junction locks. A train can enter if the junction is free OR it already owns the lock.
        if (involvesFPJunction && freightPassengerJunctionLock != null && !freightPassengerJunctionLock.equals(train.id)) {
            return false;
        }
        if (involvesPSJunction && passengerSplitJunctionLock != null && !passengerSplitJunctionLock.equals(train.id)) {
            return false;
        }

        // FIX: A freight train now only waits if a passenger train is waiting AND the junction is currently free.
        // This prevents the freight train from waiting for a passenger train that is itself blocked.
        if (!train.isPassenger && involvesFPJunction && freightPassengerJunctionLock == null) {
            if (isPassengerTrainWaitingForFPJunction()) {
                return false;
            }
        }
        return true;
    }

    private boolean isPassengerTrainWaitingForFPJunction() {
        return trains.values().stream().anyMatch(t -> t.isPassenger && !sectionOccupancy.containsValue(t.id) && freightPassengerJunction.contains(t.getNextSection()));
    }
}