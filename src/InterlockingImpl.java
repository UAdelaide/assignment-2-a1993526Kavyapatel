import java.util.*;
import java.util.concurrent.locks.*;
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


public class InterlockingImpl implements Interlocking {

    private static class Train {
        final String name;
        final int destination;
        final List<Integer> path;
        int arrivalCount = 0;
        Train(String name, int destination, List<Integer> path) {
            this.name = name;
            this.destination = destination;
            this.path = path;
        }
    }

    private final Map<String, Train> trains = new HashMap<>();
    private final Map<String, Integer> trainLocations = new HashMap<>();
    private final Map<Integer, String> sectionOccupancy = new HashMap<>();
    private final Map<Integer, Integer> lastVacated = new HashMap<>();
    private final Set<Integer> validSections = new HashSet<>();
    private int tick = 0;

    public InterlockingImpl() {
        for (int i = 1; i <= 11; i++) {
            validSections.add(i);
            sectionOccupancy.put(i, null);
            lastVacated.put(i, -1);
        }
    }

    @Override
    public void addTrain(String name, int entry, int dest) {
        if (trains.containsKey(name))
            throw new IllegalArgumentException("Duplicate train");
        if (!validSections.contains(entry) || !validSections.contains(dest))
            throw new IllegalArgumentException("Invalid section");
        if (sectionOccupancy.get(entry) != null)
            throw new IllegalStateException("Section occupied");
        List<Integer> path = findPath(entry, dest);
        if (path.isEmpty())
            throw new IllegalArgumentException("No valid path");
        Train t = new Train(name, dest, path);
        trains.put(name, t);
        trainLocations.put(name, entry);
        sectionOccupancy.put(entry, name);
    }

    @Override
    public int moveTrains(String... names) {
        tick++;
        Set<String> moveSet = new HashSet<>(Arrays.asList(names));
        for (String n : moveSet)
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("No train: " + n);

        Map<String, Integer> plan = new HashMap<>();
        List<String> ordered = moveSet.stream()
                .filter(trainLocations::containsKey)
                .sorted(Comparator.comparing(this::isFreightTrain).thenComparing(n -> n))
                .collect(Collectors.toList());

        int lastCount = -1;
        while (plan.size() > lastCount) {
            lastCount = plan.size();
            for (String n : ordered) {
                if (plan.containsKey(n)) continue;
                if (!trainLocations.containsKey(n)) continue;
                Train t = trains.get(n);
                int cur = trainLocations.get(n);

                // one-cycle linger at destination or section 10
                if (cur == t.destination || cur == 10) {
                    if (t.arrivalCount == 0) { t.arrivalCount++; continue; }
                    plan.put(n, -1);
                    continue;
                }

                int next = getNextSectionForTrain(n);
                if (next == -1) continue;
                String occ = sectionOccupancy.get(next);

                // cooldown shortened to 1 tick
                if (tick - lastVacated.getOrDefault(next, -1) < 1) continue;
                if (plan.containsValue(next)) continue;
                if (occ != null && plan.containsKey(occ) && plan.get(occ) == cur) continue;

                boolean free = (occ == null) || (moveSet.contains(occ) && plan.containsKey(occ));
                if (!free) continue;

                // 3↔4 freight junction restriction
                if ((cur == 3 && next == 4) || (cur == 4 && next == 3)) {
                    if (plan.values().contains(cur)) continue;
                    if (tick - Math.max(lastVacated.get(3), lastVacated.get(4)) < 1)
                        continue;
                }

                // 2-5-6 merge rule
                if ((cur == 5 && next == 6) || (cur == 2 && next == 5)) {
                    if (tick - Math.max(lastVacated.get(5), lastVacated.get(6)) < 1)
                        continue;
                }

                // 8/9→10 merge rule
                if ((cur == 8 || cur == 9) && next == 10) {
                    if (tick - lastVacated.get(10) < 1) continue;
                }

                plan.put(n, next);
            }
        }

        int moved = 0;
        for (String n : ordered) {
            if (!plan.containsKey(n)) continue;
            int cur = trainLocations.get(n);
            int next = plan.get(n);
            sectionOccupancy.put(cur, null);
            lastVacated.put(cur, tick);
            if (next == -1) {
                trainLocations.remove(n);
            } else {
                sectionOccupancy.put(next, n);
                trainLocations.put(n, next);
            }
            moved++;
        }

        if (moved == 0) {
            for (String n : ordered) {
                if (isFreightTrain(n)) {
                    int cur = trainLocations.get(n);
                    int next = getNextSectionForTrain(n);
                    if (next != -1 && sectionOccupancy.get(next) == null) {
                        sectionOccupancy.put(cur, null);
                        lastVacated.put(cur, tick);
                        sectionOccupancy.put(next, n);
                        trainLocations.put(n, next);
                        moved++;
                        break;
                    }
                }
            }
        }
        return moved;
    }

    @Override
    public String getSection(int s) {
        if (!validSections.contains(s))
            throw new IllegalArgumentException();
        return sectionOccupancy.get(s);
    }

    @Override
    public int getTrain(String n) {
        if (!trains.containsKey(n))
            throw new IllegalArgumentException();
        return trainLocations.getOrDefault(n, -1);
    }

    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> g = buildGraph();
        Queue<List<Integer>> q = new LinkedList<>();
        q.add(Collections.singletonList(start));
        Set<Integer> seen = new HashSet<>();
        seen.add(start);
        while (!q.isEmpty()) {
            List<Integer> path = q.poll();
            int last = path.get(path.size() - 1);
            if (last == end) return path;
            for (int nb : g.getOrDefault(last, Collections.emptyList())) {
                if (!seen.contains(nb)) {
                    seen.add(nb);
                    List<Integer> np = new ArrayList<>(path);
                    np.add(nb);
                    q.add(np);
                }
            }
        }
        return Collections.emptyList();
    }

    private Map<Integer, List<Integer>> buildGraph() {
        Map<Integer, List<Integer>> g = new HashMap<>();
        g.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        g.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        g.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        g.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        g.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        g.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        g.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        g.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        g.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return g;
    }

    private int getNextSectionForTrain(String t) {
        Train tr = trains.get(t);
        if (tr == null) return -1;
        int cur = trainLocations.get(t);
        int i = tr.path.indexOf(cur);
        if (i != -1 && i < tr.path.size() - 1)
            return tr.path.get(i + 1);
        return -1;
    }

    private boolean isFreightTrain(String t) {
        Train tr = trains.get(t);
        if (tr == null || tr.path.isEmpty()) return false;
        int f = tr.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(f);
    }
}