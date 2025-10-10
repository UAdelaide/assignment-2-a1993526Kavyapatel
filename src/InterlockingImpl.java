import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the Interlocking interface to manage a railway network.
 * Final tuned version for Programming Assignment 2 (University of Adelaide).
 */
public class InterlockingImpl implements Interlocking {

    private static class Train {
        final String name;
        final int destination;
        final List<Integer> path;

        Train(String name, int destination, List<Integer> path) {
            this.name = name;
            this.destination = destination;
            this.path = path;
        }
    }

    private final Map<String, Train> trains = new HashMap<>();
    private final Map<String, Integer> trainLocations = new HashMap<>();
    private final Map<Integer, String> sectionOccupancy = new HashMap<>();
    private final Set<Integer> validSections = new HashSet<>();

    public InterlockingImpl() {
        for (int i = 1; i <= 11; i++) {
            validSections.add(i);
            sectionOccupancy.put(i, null);
        }
    }

    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException {

        if (trains.containsKey(trainName))
            throw new IllegalArgumentException("Duplicate train name: " + trainName);

        if (!validSections.contains(entryTrackSection) || !validSections.contains(destinationTrackSection))
            throw new IllegalArgumentException("Invalid section numbers.");

        if (sectionOccupancy.get(entryTrackSection) != null)
            throw new IllegalStateException("Entry section occupied.");

        List<Integer> path = findPath(entryTrackSection, destinationTrackSection);
        if (path.isEmpty())
            throw new IllegalArgumentException("No valid path from entry to destination.");

        Train train = new Train(trainName, destinationTrackSection, path);
        trains.put(trainName, train);
        trainLocations.put(trainName, entryTrackSection);
        sectionOccupancy.put(entryTrackSection, trainName);
    }

    // ✅ Integrated moveTrains() method
    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        Set<String> moving = new HashSet<>(Arrays.asList(trainNames));
        for (String n : moving)
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("Train '" + n + "' does not exist.");

        // Passenger first, then freight, then alphabetical
        List<String> order = moving.stream()
                .filter(trainLocations::containsKey)
                .sorted(Comparator.comparing(this::isFreightTrain).thenComparing(n -> n))
                .collect(Collectors.toList());

        Map<String, Integer> plan = new HashMap<>();
        boolean changed;

        do {
            changed = false;
            for (String name : order) {
                if (plan.containsKey(name)) continue;
                int current = trainLocations.get(name);
                Train tr = trains.get(name);

                // Exit if already at destination
                if (current == tr.destination) {
                    plan.put(name, -1);
                    // mark freed section immediately
                    sectionOccupancy.put(current, null);
                    changed = true;
                    continue;
                }

                int next = getNextSectionForTrain(name);
                if (next == -1) continue;

                String occ = sectionOccupancy.get(next);
                boolean free = (occ == null) || (moving.contains(occ) && plan.containsKey(occ));
                if (!free) continue;

                // prevent two trains going to same target
                if (plan.containsValue(next)) continue;

                // --- Junction coordination ---
                boolean freightBusy = sectionOccupancy.get(3) != null || sectionOccupancy.get(4) != null;
                boolean passBusy = sectionOccupancy.get(5) != null || sectionOccupancy.get(6) != null;
                boolean freightClearing = plan.containsValue(3) || plan.containsValue(4);
                boolean passClearing = plan.containsValue(5) || plan.containsValue(6);

                if (freightBusy && passBusy && !freightClearing && !passClearing) continue;

                // Crossline mutual exclusion
                if ((current == 3 && next == 4) || (current == 4 && next == 3)) {
                    if ((sectionOccupancy.get(5) != null && !plan.containsKey(sectionOccupancy.get(5))) ||
                        (sectionOccupancy.get(6) != null && !plan.containsKey(sectionOccupancy.get(6))))
                        continue;
                }
                if ((current == 5 && next == 6) || (current == 6 && next == 5)) {
                    if ((sectionOccupancy.get(3) != null && !plan.containsKey(sectionOccupancy.get(3))) ||
                        (sectionOccupancy.get(4) != null && !plan.containsKey(sectionOccupancy.get(4))))
                        continue;
                }

                // 🚦 NEW: reuse of just-freed section 10 (passenger hub)
                if ((next == 10) && sectionOccupancy.get(10) == null) {
                    // allow if last occupant planned to exit
                    changed = true;
                }

                plan.put(name, next);
                changed = true;
            }
        } while (changed);

        // --- Execute planned moves ---
        int moved = 0;
        for (String n : order) {
            if (!plan.containsKey(n)) continue;
            int dest = plan.get(n);
            int old = trainLocations.get(n);
            sectionOccupancy.put(old, null);
            if (dest == -1)
                trainLocations.remove(n);
            else {
                sectionOccupancy.put(dest, n);
                trainLocations.put(n, dest);
            }
            moved++;
        }
        return moved;
    }

    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        if (!validSections.contains(trackSection))
            throw new IllegalArgumentException("Invalid section: " + trackSection);
        return sectionOccupancy.get(trackSection);
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        if (!trains.containsKey(trainName))
            throw new IllegalArgumentException("Unknown train: " + trainName);
        return trainLocations.getOrDefault(trainName, -1);
    }

    // 🔍 Graph pathfinding logic
    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> graph = buildGraph();
        Queue<List<Integer>> q = new LinkedList<>();
        q.add(Collections.singletonList(start));
        Set<Integer> visited = new HashSet<>();
        visited.add(start);

        while (!q.isEmpty()) {
            List<Integer> path = q.poll();
            int last = path.get(path.size() - 1);
            if (last == end) return path;
            for (int n : graph.getOrDefault(last, List.of())) {
                if (!visited.contains(n)) {
                    visited.add(n);
                    List<Integer> np = new ArrayList<>(path);
                    np.add(n);
                    q.add(np);
                }
            }
        }
        return List.of();
    }

    private Map<Integer, List<Integer>> buildGraph() {
        Map<Integer, List<Integer>> g = new HashMap<>();
        // Passenger
        g.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        g.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        g.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        g.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        g.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        // Freight
        g.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        g.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        g.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        g.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return g;
    }

    private int getNextSectionForTrain(String name) {
        Train t = trains.get(name);
        int current = trainLocations.get(name);
        int idx = t.path.indexOf(current);
        if (idx != -1 && idx < t.path.size() - 1)
            return t.path.get(idx + 1);
        return -1;
    }

    private boolean isFreightTrain(String n) {
        Train t = trains.get(n);
        if (t.path.isEmpty()) return false;
        int first = t.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(first);
    }
}
