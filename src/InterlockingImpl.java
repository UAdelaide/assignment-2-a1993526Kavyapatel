import java.util.*;
import java.util.stream.Collectors;
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
 * Implements the Interlocking interface for Programming Assignment 2.
 * Handles train movement, occupancy, and safe coordination of railway sections.
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
            throw new IllegalArgumentException("Train already exists: " + trainName);

        if (!validSections.contains(entryTrackSection) || !validSections.contains(destinationTrackSection))
            throw new IllegalArgumentException("Invalid track section.");

        if (sectionOccupancy.get(entryTrackSection) != null)
            throw new IllegalStateException("Entry section already occupied.");

        List<Integer> path = findPath(entryTrackSection, destinationTrackSection);
        if (path.isEmpty())
            throw new IllegalArgumentException("No valid path found.");

        Train train = new Train(trainName, destinationTrackSection, path);
        trains.put(trainName, train);
        trainLocations.put(trainName, entryTrackSection);
        sectionOccupancy.put(entryTrackSection, trainName);
    }

    // ✅ Final moveTrains() with extended cooldown and junction blocking
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

                if (current == tr.destination) {
                    plan.put(name, -1);
                    sectionOccupancy.put(current, null);
                    addCooldown(current, cooldown);   // 🔹 neighbour lock
                    changed = true;
                    continue;
                }

                int next = getNextSectionForTrain(name);
                if (next == -1) continue;

                String occ = sectionOccupancy.get(next);

                boolean free = (occ == null)
                        || (moving.contains(occ) && plan.containsKey(occ))
                        || (moving.contains(occ) && plan.getOrDefault(occ, -99) == -1);

                if (cooldown.contains(next)) free = false;   // 🔹 new blocking rule

                if (!free || plan.containsValue(next)) continue;

                // --- Junction rules ---
                boolean freightBusy = (sectionOccupancy.get(3) != null && !plan.containsKey(sectionOccupancy.get(3))) ||
                                      (sectionOccupancy.get(4) != null && !plan.containsKey(sectionOccupancy.get(4)));
                boolean passBusy = (sectionOccupancy.get(5) != null && !plan.containsKey(sectionOccupancy.get(5))) ||
                                   (sectionOccupancy.get(6) != null && !plan.containsKey(sectionOccupancy.get(6)));
                if (freightBusy && passBusy) continue;

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

        int moved = 0;
        for (String n : order) {
            if (!plan.containsKey(n)) continue;
            int dest = plan.get(n);
            int old = trainLocations.get(n);
            sectionOccupancy.put(old, null);
            addCooldown(old, cooldown);   // 🔹 apply cooldown on exit
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
     * Adds cooldown for a section and its neighbour if it's part of a junction.
     */
    private void addCooldown(int section, Set<Integer> cooldown) {
        cooldown.add(section);
        switch (section) {
            case 2 -> cooldown.add(5);
            case 6 -> cooldown.add(5);
            case 5 -> cooldown.addAll(Arrays.asList(2, 6));
            case 3, 4 -> cooldown.addAll(Arrays.asList(5, 6));
        }
    }

    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        if (!validSections.contains(trackSection))
            throw new IllegalArgumentException("Invalid section number: " + trackSection);
        return sectionOccupancy.get(trackSection);
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        if (!trains.containsKey(trainName))
            throw new IllegalArgumentException("Train does not exist: " + trainName);
        return trainLocations.getOrDefault(trainName, -1);
    }

    // 🔹 Pathfinding logic
    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> graph = buildGraph();
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
        return List.of();
    }

    private Map<Integer, List<Integer>> buildGraph() {
        Map<Integer, List<Integer>> g = new HashMap<>();
        // Passenger network
        g.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        g.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        g.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        g.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        g.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        // Freight network
        g.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        g.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        g.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        g.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return g;
    }

    private int getNextSectionForTrain(String trainName) {
        Train t = trains.get(trainName);
        int current = trainLocations.get(trainName);
        int idx = t.path.indexOf(current);
        if (idx != -1 && idx < t.path.size() - 1)
            return t.path.get(idx + 1);
        return -1;
    }

    private boolean isFreightTrain(String name) {
        Train t = trains.get(name);
        if (t == null || t.path.isEmpty()) return false;
        int first = t.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(first);
    }
}
