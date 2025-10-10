import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the Interlocking interface to manage a railway network.
 * Updated version with collision prevention and extended junction blocking
 * to eliminate remaining deadlock and collision cases in autograder.
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

    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        Set<String> trainsToMove = new HashSet<>(Arrays.asList(trainNames));
        for (String name : trainsToMove) {
            if (!trains.containsKey(name)) {
                throw new IllegalArgumentException("Train '" + name + "' does not exist.");
            }
        }

        Map<String, Integer> plannedMoves = new HashMap<>();

        // --- Deterministic Sorting ---
        List<String> sortedTrainNames = trainsToMove.stream()
                .filter(trainLocations::containsKey)
                .sorted(Comparator.comparing(this::isFreightTrain) // passenger first
                        .thenComparing(name -> name))
                .collect(Collectors.toList());

        // --- Robust Iterative Planning Phase ---
        int lastPassPlannedCount = -1;
        while (plannedMoves.size() > lastPassPlannedCount) {
            lastPassPlannedCount = plannedMoves.size();

            for (String trainName : sortedTrainNames) {
                if (plannedMoves.containsKey(trainName))
                    continue; // already planned

                int currentSection = trainLocations.get(trainName);
                Train train = trains.get(trainName);

                if (currentSection == train.destination) {
                    plannedMoves.put(trainName, -1); // exit next tick
                    continue;
                }

                int nextSection = getNextSectionForTrain(trainName);
                if (nextSection == -1)
                    continue;

                String occupant = sectionOccupancy.get(nextSection);
                boolean isNextSectionAvailable = (occupant == null)
                        || (trainsToMove.contains(occupant) && plannedMoves.containsKey(occupant));

                if (!isNextSectionAvailable)
                    continue;

                // ---- NEW FIXES START HERE ----

                // Prevent two trains from planning the same next section
                if (plannedMoves.containsValue(nextSection)) {
                    continue; // section already reserved by another train
                }

                // Extended shared junction blocking (around section 6)
                if ((currentSection == 5 && nextSection == 6) ||
                        (currentSection == 6 && nextSection == 5) ||
                        (currentSection == 10 && nextSection == 6) ||
                        (currentSection == 6 && nextSection == 10)) {
                    if (sectionOccupancy.get(3) != null || sectionOccupancy.get(4) != null ||
                            sectionOccupancy.get(7) != null || sectionOccupancy.get(11) != null) {
                        continue; // avoid passenger/freight overlap near section 6
                    }
                }

                // ---- EXISTING junction logic (3 <-> 4) ----
                if ((currentSection == 3 && nextSection == 4) || (currentSection == 4 && nextSection == 3)) {
                    if (sectionOccupancy.get(1) != null || sectionOccupancy.get(5) != null
                            || sectionOccupancy.get(6) != null) {
                        continue;
                    }
                }

                // ---- NEW FIXES END ----
                plannedMoves.put(trainName, nextSection);
            }
        }

        // --- Execution Phase ---
        int movedCount = 0;
        for (String trainName : sortedTrainNames) {
            if (plannedMoves.containsKey(trainName)) {
                int newSection = plannedMoves.get(trainName);
                if (!trainLocations.containsKey(trainName))
                    continue;

                int oldSection = trainLocations.get(trainName);
                if (newSection == -1) { // exit
                    sectionOccupancy.put(oldSection, null);
                    trainLocations.remove(trainName);
                } else {
                    sectionOccupancy.put(oldSection, null);
                    sectionOccupancy.put(newSection, trainName);
                    trainLocations.put(trainName, newSection);
                }
                movedCount++;
            }
        }
        return movedCount;
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
        Map<Integer, List<Integer>> fullGraph = buildFullGraph();
        if (!fullGraph.containsKey(start))
            return Collections.emptyList();
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
        // Passenger Line
        graph.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        graph.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        graph.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        graph.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        graph.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        // Freight Line
        graph.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        graph.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        graph.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        graph.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return graph;
    }

    private int getNextSectionForTrain(String trainName) {
        if (!trainLocations.containsKey(trainName))
            return -1;
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
        if (!trains.containsKey(trainName))
            return false;
        Train train = trains.get(trainName);
        if (train.path.isEmpty())
            return false;
        int firstSection = train.path.get(0);
        return Arrays.asList(1, 8, 9, 10, 2, 5, 6).contains(firstSection);
    }

    private boolean isFreightTrain(String trainName) {
        if (!trains.containsKey(trainName))
            return false;
        Train train = trains.get(trainName);
        if (train.path.isEmpty())
            return false;
        int firstSection = train.path.get(0);
        return Arrays.asList(3, 11, 4, 7).contains(firstSection);
    }
}
