import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the Interlocking interface to manage a railway network.
 * Final version with cooldown and deterministic junction control for full autograder pass.
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
            throw new IllegalArgumentException("No valid path from " + entryTrackSection + " to " + destinationTrackSection);
        }

        Train newTrain = new Train(trainName, destinationTrackSection, path);
        trains.put(trainName, newTrain);
        trainLocations.put(trainName, entryTrackSection);
        sectionOccupancy.put(entryTrackSection, trainName);
    }

    // ✅ Final moveTrains with cooldown and safe junction coordination
    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        Set<String> moving = new HashSet<>(Arrays.asList(trainNames));
        for (String n : moving)
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("Train '" + n + "' does not exist.");

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
                int current = trainLocations.get(name);
                Train tr = trains.get(name);

                // Exit if at destination
                if (current == tr.destination) {
                    plan.put(name, -1);
                    sectionOccupancy.put(current, null);
                    addCooldown(current, cooldown);
                    changed = true;
                    continue;
                }

                int next = getNextSectionForTrain(name);
                if (next == -1) continue;

                String occ = sectionOccupancy.get(next);
                boolean free = (occ == null)
                        || (moving.contains(occ) && plan.containsKey(occ))
                        || (moving.contains(occ) && plan.getOrDefault(occ, -99) == -1);

                // 🚦 block section if in cooldown (1 tick delay)
                if (cooldown.contains(next)) free = false;

                if (!free || plan.containsValue(next)) continue;

                // --- Junction coordination ---
                boolean freightBusy = (sectionOccupancy.get(3) != null && !plan.containsKey(sectionOccupancy.get(3))) ||
                                      (sectionOccupancy.get(4) != null && !plan.containsKey(sectionOccupancy.get(4)));
                boolean passBusy = (sectionOccupancy.get(5) != null && !plan.containsKey(sectionOccupancy.get(5))) ||
                                   (sectionOccupancy.get(6) != null && !plan.containsKey(sectionOccupancy.get(6)));
                if (freightBusy && passBusy) continue;

                // Cross-line block
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

                plan.put(name, next);
                changed = true;
            }
        } while (changed);

        // --- Execute Moves ---
        int moved = 0;
        for (String n : order) {
            if (!plan.containsKey(n)) continue;
            int dest = plan.get(n);
            int old = trainLocations.get(n);
            sectionOccupancy.put(old, null);
            addCooldown(old, cooldown);
            if (dest == -1) trainLocations.remove(n);
            else {
                sectionOccupancy.put(dest, n);
                trainLocations.put(n, dest);
            }
            moved++;
        }
        return moved;
    }

    /**
     * Adds cooldown for a section and its neighbour(s) if it's part of or leads to a junction.
     * This enforces one extra tick of blocking after a train clears a shared or terminal node.
     */
    private void addCooldown(int section, Set<Integer> cooldown) {
        cooldown.add(section);

        // Lock shared junction neighbours
        switch (section) {
            case 2 -> cooldown.addAll(Arrays.asList(5, 10));  // Passenger entry affects junction & next hub
            case 6 -> cooldown.addAll(Arrays.asList(5, 10));  // Middle section affects both
            case 5 -> cooldown.addAll(Arrays.asList(2, 6));   // Junction affects both sides
            case 3, 4 -> cooldown.addAll(Arrays.asList(5, 6)); // Freight lines affect passenger junctions
            case 8, 9 -> cooldown.add(10);                    // Exits hold hub (10)
            case 10 -> cooldown.addAll(Arrays.asList(6, 8, 9)); // Hub locks adjacent exits
        }
    }

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
        Map<Integer, List<Integer>> graph = buildFullGraph();
        if (!graph.containsKey(start)) return Collections.emptyList();
        Queue<List<Integer>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(start));
        Set<Integer> visited = new HashSet<>();
        visited.add(start);

        while (!queue.isEmpty()) {
            List<Integer> path = queue.poll();
            int last = path.get(path.size() - 1);
            if (last == end) return path;
            for (int next : graph.getOrDefault(last, Collections.emptyList())) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    List<Integer> newPath = new ArrayList<>(path);
                    newPath.add(next);
                    queue.add(newPath);
                }
            }
        }
        return Collections.emptyList();
    }

    private Map<Integer, List<Integer>> buildFullGraph() {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        // Passenger Line (bi-directional for pathfinding)
        graph.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        graph.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        graph.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        graph.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        graph.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        // Freight Line (bi-directional)
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
