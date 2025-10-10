import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements the Interlocking interface to manage a railway network.
 * Final deterministic and conflict-safe version with cooldown and priority handling.
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
        if (trains.containsKey(trainName)) {
            throw new IllegalArgumentException("Train name '" + trainName + "' is already in use.");
        }
        if (!validSections.contains(entryTrackSection) || !validSections.contains(destinationTrackSection)) {
            throw new IllegalArgumentException("Invalid entry or destination track section.");
        }
        if (sectionOccupancy.get(entryTrackSection) != null) {
            throw new IllegalStateException("Entry track section " + entryTrackSection + " is already occupied.");
        }
        List<Integer> path = findPath(entryTrackSection, destinationTrackSection);
        if (path.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid path from entry " + entryTrackSection + " to destination " + destinationTrackSection);
        }
        Train newTrain = new Train(trainName, destinationTrackSection, path);
        trains.put(trainName, newTrain);
        trainLocations.put(trainName, entryTrackSection);
        sectionOccupancy.put(entryTrackSection, trainName);
    }

    // ------------------------------------------------------------
    // ✅ Updated moveTrains() with cooldown, priority, and safety
    // ------------------------------------------------------------
    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        Set<String> moving = new HashSet<>(Arrays.asList(trainNames));
        for (String n : moving) {
            if (!trains.containsKey(n)) {
                throw new IllegalArgumentException("Train '" + n + "' does not exist.");
            }
        }

        List<String> order = moving.stream()
                .filter(trainLocations::containsKey)
                .sorted(Comparator.comparing(this::isFreightTrain).thenComparing(n -> n))
                .collect(Collectors.toList());

        Map<String, Integer> plan = new HashMap<>();
        Set<Integer> cooldown = new HashSet<>();
        boolean changed;

        do {
            changed = false;
            for (String name : order) {
                if (plan.containsKey(name)) continue;
                if (!trainLocations.containsKey(name)) continue;

                int current = trainLocations.get(name);
                Train tr = trains.get(name);

                // Exit train at destination
                if (current == tr.destination) {
                    plan.put(name, -1);
                    addCooldown(current, cooldown);
                    changed = true;
                    continue;
                }

                int next = getNextSectionForTrain(name);
                if (next == -1) continue;
                if (cooldown.contains(next)) continue; // recently vacated section

                String occ = sectionOccupancy.get(next);

                boolean free = (occ == null)
                        || (moving.contains(occ) && plan.containsKey(occ))
                        || (moving.contains(occ) && plan.getOrDefault(occ, -99) == -1);

                if (!free || plan.containsValue(next)) continue;

                // Head-on collision prevention (unless passenger wins)
                if (occ != null && moving.contains(occ) && plan.containsKey(occ)) {
                    int occNext = plan.get(occ);
                    if (occNext == current) {
                        boolean mePass = isPassengerTrain(name);
                        boolean occPass = isPassengerTrain(occ);
                        if (mePass && !occPass) {
                            plan.remove(occ); // passenger overrides freight
                        } else {
                            continue; // block both
                        }
                    }
                }

                // Passenger priority at 3–4 crossing
                if ((current == 3 && next == 4) || (current == 4 && next == 3)) {
                    boolean passBusy =
                            (sectionOccupancy.get(5) != null && !plan.containsKey(sectionOccupancy.get(5))) ||
                            (sectionOccupancy.get(6) != null && !plan.containsKey(sectionOccupancy.get(6))) ||
                            plan.containsValue(5) || plan.containsValue(6);
                    if (passBusy) continue;
                }

                plan.put(name, next);
                changed = true;
            }
        } while (changed);

        // Execute moves
        int moved = 0;
        for (String n : order) {
            if (!plan.containsKey(n)) continue;

            int dest = plan.get(n);
            int old = trainLocations.get(n);
            sectionOccupancy.put(old, null);
            addCooldown(old, cooldown);

            if (dest == -1) {
                trainLocations.remove(n);
            } else {
                sectionOccupancy.put(dest, n);
                trainLocations.put(n, dest);
            }
            moved++;
        }
        return moved;
    }

    /** Adds cooldown for a section and related neighbours (prevents re-entry same tick). */
    private void addCooldown(int section, Set<Integer> cooldown) {
        cooldown.add(section);
        switch (section) {
            case 5 -> cooldown.addAll(Arrays.asList(2, 6));
            case 2 -> cooldown.add(5);
            case 6 -> cooldown.addAll(Arrays.asList(5, 10));
            case 10 -> cooldown.addAll(Arrays.asList(6, 8, 9));
            case 3, 4 -> cooldown.addAll(Arrays.asList(5, 6)); // protect crossing
            default -> {
            }
        }
    }

    // ------------------------------------------------------------
    // Remaining helper and interface methods (unchanged)
    // ------------------------------------------------------------
    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        if (!validSections.contains(trackSection)) {
            throw new IllegalArgumentException("Track section " + trackSection + " does not exist.");
        }
        return sectionOccupancy.get(trackSection);
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        if (!trains.containsKey(trainName)) {
            throw new IllegalArgumentException("Train '" + trainName + "' does not exist.");
        }
        return trainLocations.getOrDefault(trainName, -1);
    }

    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> fullGraph = buildFullGraph();
        if (!fullGraph.containsKey(start)) return Collections.emptyList();
        Queue<List<Integer>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(start));
        Set<Integer> visited = new HashSet<>();
        visited.add(start);
        while (!queue.isEmpty()) {
            List<Integer> currentPath = queue.poll();
            int lastNode = currentPath.get(currentPath.size() - 1);
            if (lastNode == end) {
                return currentPath;
            }
            for (int neighbor : fullGraph.getOrDefault(lastNode, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<Integer> newPath = new ArrayList<>(currentPath);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }
        return Collections.emptyList();
    }

    private Map<Integer, List<Integer>> buildFullGraph() {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        graph.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        graph.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        graph.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        graph.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        graph.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        graph.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        graph.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        graph.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        graph.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return graph;
    }

    private int getNextSectionForTrain(String trainName) {
        if (!trainLocations.containsKey(trainName)) return -1;
        Train train = trains.get(trainName);
        int currentSection = trainLocations.get(trainName);
        List<Integer> path = train.path;
        int currentIndex = path.indexOf(currentSection);
        if (currentIndex != -1 && currentIndex < path.size() - 1) {
            return path.get(currentIndex + 1);
        }
        return -1;
    }

    private boolean isPassengerTrain(String trainName) {
        if (!trains.containsKey(trainName)) return false;
        Train train = trains.get(trainName);
        if (train.path.isEmpty()) return false;
        int firstSection = train.path.get(0);
        return Arrays.asList(1, 8, 9, 10, 2, 5, 6).contains(firstSection);
    }

    private boolean isFreightTrain(String trainName) {
        if (!trains.containsKey(trainName)) return false;
        Train train = trains.get(trainName);
        if (train.path.isEmpty()) return false;
        int firstSection = train.path.get(0);
        return Arrays.asList(3, 11, 4, 7).contains(firstSection);
    }
}
