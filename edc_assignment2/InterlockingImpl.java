// NO 'package' DECLARATION - This file is ready for Gradescope.
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
 * Implements the Interlocking interface to manage a railway network,
 * preventing collisions and deadlocks.
 */
public class InterlockingImpl implements Interlocking {
    
    // Private inner class to hold train-specific data.
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
    
    // Data structures to maintain the state of the railway system.
    private final Map<String, Train> trains = new HashMap<>();
    private final Map<String, Integer> trainLocations = new HashMap<>();
    private final Map<Integer, String> sectionOccupancy = new HashMap<>();
    private final Set<Integer> validSections = new HashSet<>();
    
    public InterlockingImpl() {
        // Initialize all track sections as valid and empty.
        for (int i = 1; i <= 11; i++) {
            validSections.add(i);
            sectionOccupancy.put(i, null);
        }
    }
    
    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException {
        // Validation checks before adding a train.
        if (trains.containsKey(trainName)) {
            throw new IllegalArgumentException("Train name '" + trainName + "' is already in use.");
        }
        if (!validSections.contains(entryTrackSection) || !validSections.contains(destinationTrackSection)) {
            throw new IllegalArgumentException("Invalid entry or destination track section.");
        }
        if (sectionOccupancy.get(entryTrackSection) != null) {
            throw new IllegalStateException("Entry track section " + entryTrackSection + " is already occupied.");
        }
        
        // Find a valid path for the train.
        List<Integer> path = findPath(entryTrackSection, destinationTrackSection);
        if (path.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid path from entry " + entryTrackSection + " to destination " + destinationTrackSection);
        }
        
        // Add the new train to the system.
        Train newTrain = new Train(trainName, destinationTrackSection, path);
        trains.put(trainName, newTrain);
        trainLocations.put(trainName, entryTrackSection);
        sectionOccupancy.put(entryTrackSection, trainName);
    }
    
    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        for (String name : trainNames) {
            if (!trains.containsKey(name)) {
                // This check now handles trains that have already exited.
                if (trainLocations.containsKey(name)) {
                     throw new IllegalArgumentException("Train '" + name + "' does not exist but has a location.");
                }
            }
        }
        
        Map<String, Integer> plannedMoves = new HashMap<>();
        Set<Integer> reservedSections = new HashSet<>(trainLocations.values());

        // ** THE FIX IS HERE: ** Correctly sort trains to give passenger trains priority.
        List<String> sortedTrainNames = Arrays.stream(trainNames)
                .sorted((t1, t2) -> Boolean.compare(isFreightTrain(t1), isFreightTrain(t2)))
                .collect(Collectors.toList());

        // --- Planning Phase ---
        for (String trainName : sortedTrainNames) {
            if (!trainLocations.containsKey(trainName)) continue; // Train has already exited.

            int currentSection = trainLocations.get(trainName);
            Train train = trains.get(trainName);
            
            // Plan to exit if the train is at its destination.
            if (currentSection == train.destination) {
                plannedMoves.put(trainName, -1); 
                reservedSections.remove(currentSection); // This section will become free.
                continue;
            }
            
            int nextSection = getNextSectionForTrain(trainName);
            if (nextSection == -1) continue; // No more path.

            // Check if the next section is already reserved by another stationary train
            // or a higher-priority train that has already planned its move.
            if (reservedSections.contains(nextSection)) {
                continue;
            }
            
            // ** CRITICAL JUNCTION LOGIC **
            if ((currentSection == 3 && nextSection == 4) || (currentSection == 4 && nextSection == 3)) {
                if (sectionOccupancy.get(1) != null || sectionOccupancy.get(5) != null || sectionOccupancy.get(6) != null) {
                    continue; // Block freight train if any part of the passenger junction is occupied.
                }
            }
            
            // If move is valid, plan it and reserve the resources.
            plannedMoves.put(trainName, nextSection);
            reservedSections.add(nextSection);
            // We do NOT remove the current section yet, as it's still occupied until the execution phase.
        }
        
        // --- Execution Phase ---
        int movedCount = 0;
        for (Map.Entry<String, Integer> move : plannedMoves.entrySet()) {
            String trainName = move.getKey();
            int newSection = move.getValue();
            
            // It's possible the train has already exited in this cycle, so we need a null check
            if (trainLocations.get(trainName) == null) continue;
            int oldSection = trainLocations.get(trainName);

            if (newSection == -1) { // Train exits the system.
                sectionOccupancy.put(oldSection, null);
                trainLocations.remove(trainName); // Train is no longer on the map.
            } else { // Train moves to a new section.
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
    
    // --- Helper Methods ---

    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> fullGraph = buildFullGraph();
        if(!fullGraph.containsKey(start)) return Collections.emptyList();
        
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
        return Collections.emptyList(); // No path found.
    }
    
    private Map<Integer, List<Integer>> buildFullGraph() {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        // Passenger Line (Southbound and Northbound paths)
        graph.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(2, 6));
        graph.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        graph.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        graph.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        graph.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        graph.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        // Freight Line (Southbound and Northbound paths)
        graph.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        graph.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        graph.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        graph.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return graph;
    }
    
    private int getNextSectionForTrain(String trainName) {
        Train train = trains.get(trainName);
        if (!trainLocations.containsKey(trainName)) return -1;
        int currentSection = trainLocations.get(trainName);
        List<Integer> path = train.path;
        int currentIndex = path.indexOf(currentSection);
        if (currentIndex != -1 && currentIndex < path.size() - 1) {
            return path.get(currentIndex + 1);
        }
        return -1; // End of the path.
    }
    
    private boolean isPassengerTrain(String trainName) {
        if (!trains.containsKey(trainName)) return false;
        Train train = trains.get(trainName);
        if (train.path.isEmpty()) return false;
        int startNode = train.path.get(0);
        return Arrays.asList(1, 2, 5, 6, 8, 9, 10).contains(startNode);
    }
    
    private boolean isFreightTrain(String trainName) {
        if (!trains.containsKey(trainName)) return false;
        Train train = trains.get(trainName);
        if (train.path.isEmpty()) return false;
        int startNode = train.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(startNode);
    }
}

