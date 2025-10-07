import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 */
public class InterlockingImpl implements Interlocking {
    
    private static class Train {
        String name;
        int destination;
        List<Integer> path;

        Train(String name, int destination, List<Integer> path) {
            this.name = name;
            this.destination = destination;
            this.path = path;
        }
    }
    
    private final Map<String, Train> trains = new HashMap<>();
    private final Map<String, Integer> trainLocations = new HashMap<>();
    private final Map<Integer, String> sectionOccupancy = new HashMap<>();
    private final Map<Integer, List<Integer>> trackGraph = new HashMap<>();
    private final Set<Integer> validSections = new HashSet<>();
    
    public InterlockingImpl() {
        buildTrackGraph();
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
        for (String name : trainNames) {
            if (!trains.containsKey(name)) {
                throw new IllegalArgumentException("Train '" + name + "' does not exist.");
            }
        }
        Map<String, Integer> plannedMoves = new HashMap<>();
        Set<Integer> reservedSections = new HashSet<>(sectionOccupancy.values().stream()
                .filter(java.util.Objects::nonNull)
                .map(this::getTrain)
                .collect(Collectors.toSet()));
        List<String> sortedTrainNames = Arrays.stream(trainNames)
                .sorted((t1, t2) -> Boolean.compare(isFreightTrain(t1), isPassengerTrain(t2)))
                .collect(Collectors.toList());

        for (String trainName : sortedTrainNames) {
            int currentSection = trainLocations.get(trainName);
            Train train = trains.get(trainName);
            if (currentSection == train.destination) {
                plannedMoves.put(trainName, -1);
                reservedSections.remove(currentSection);
                continue;
            }
            int nextSection = getNextSectionForTrain(trainName);
            if (nextSection == -1) continue;
            if (reservedSections.contains(nextSection)) {
                continue;
            }
            if ((currentSection == 3 && nextSection == 4) || (currentSection == 4 && nextSection == 3)) {
                if (sectionOccupancy.get(1) != null || sectionOccupancy.get(6) != null) {
                    continue;
                }
            }
            plannedMoves.put(trainName, nextSection);
            reservedSections.add(nextSection);
            reservedSections.remove(currentSection);
        }
        
        int movedCount = 0;
        for (Map.Entry<String, Integer> move : plannedMoves.entrySet()) {
            String trainName = move.getKey();
            int newSection = move.getValue();
            int oldSection = trainLocations.get(trainName);
            
            if (newSection == -1) { // Train exits the system
                sectionOccupancy.put(oldSection, null);
                trainLocations.remove(trainName);
                // DO NOT remove the train from the main 'trains' map. THIS IS THE FIX.
            } else {
                sectionOccupancy.put(oldSection, null);
                sectionOccupancy.put(newSection, trainName);
                trainLocations.put(trainName, newSection);
            }
            movedCount++;
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
    
    private void buildTrackGraph() {
        trackGraph.put(1, List.of(5));
        trackGraph.put(5, List.of(6));
        trackGraph.put(6, List.of(10));
        trackGraph.put(10, Arrays.asList(8, 9));
        trackGraph.put(8, List.of(10));
        trackGraph.put(9, List.of(10));
        trackGraph.put(2, new ArrayList<>());
        trackGraph.put(3, Arrays.asList(4, 7));
        trackGraph.put(7, List.of(11));
        trackGraph.put(4, new ArrayList<>());
        trackGraph.put(11, new ArrayList<>());
    }
    
    private List<Integer> findPath(int start, int end) {
        if (start == end) return List.of(start);
        Map<Integer, List<Integer>> fullGraph = buildFullGraph();
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
        Map<Integer, List<Integer>> fullGraph = new HashMap<>();
        trackGraph.forEach((key, value) -> value.forEach(v -> {
            fullGraph.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
            fullGraph.computeIfAbsent(v, k -> new ArrayList<>()).add(key);
        }));
        return fullGraph;
    }
    
    private int getNextSectionForTrain(String trainName) {
        Train train = trains.get(trainName);
        int currentSection = trainLocations.get(trainName);
        List<Integer> path = train.path;
        int currentIndex = path.indexOf(currentSection);
        if (currentIndex < path.size() - 1) {
            return path.get(currentIndex + 1);
        }
        return -1;
    }
    
    private boolean isPassengerTrain(String trainName) {
        Integer section = trainLocations.get(trainName);
        if (section == null) return false;
        return section == 1 || section == 2 || section == 5 || section == 6 || section == 8 || section == 9 || section == 10;
    }
    
    private boolean isFreightTrain(String trainName) {
        Integer section = trainLocations.get(trainName);
        if (section == null) return false;
        return section == 3 || section == 4 || section == 7 || section == 11;
    }
}