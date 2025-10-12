import java.util.*;
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
    private final Set<Integer> validSections = new HashSet<>();
    private final Map<Integer, Integer> lastVacated = new HashMap<>();
    private int tick = 0;

    public InterlockingImpl() {
        for (int i = 1; i <= 11; i++) {
            validSections.add(i);
            sectionOccupancy.put(i, null);
            lastVacated.put(i, -2);
        }
    }

    @Override
    public void addTrain(String trainName, int entry, int dest) {
        if (trains.containsKey(trainName))
            throw new IllegalArgumentException("Duplicate train: " + trainName);
        if (!validSections.contains(entry) || !validSections.contains(dest))
            throw new IllegalArgumentException("Invalid section");
        if (sectionOccupancy.get(entry) != null)
            throw new IllegalStateException("Entry occupied: " + entry);
        List<Integer> path = findPath(entry, dest);
        if (path.isEmpty())
            throw new IllegalArgumentException("No valid path from " + entry + " to " + dest);
        Train t = new Train(trainName, dest, path);
        trains.put(trainName, t);
        trainLocations.put(trainName, entry);
        sectionOccupancy.put(entry, trainName);
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
                if (cur == t.destination || cur == 10) {
                    if (t.arrivalCount < 2) { t.arrivalCount++; continue; }
                    plan.put(n, -1);
                    continue;
                }
                int next = getNextSectionForTrain(n);
                if (next == -1) continue;
                String occ = sectionOccupancy.get(next);
                if (tick - lastVacated.getOrDefault(next, -2) < 2) continue;
                if (plan.containsValue(next)) continue;
                if (occ != null && plan.containsKey(occ) && plan.get(occ) == cur) continue;
                boolean free = (occ == null) || (moveSet.contains(occ) && plan.containsKey(occ));
                if (!free) continue;
                if ((cur == 3 && next == 4) || (cur == 4 && next == 3)) {
                    boolean blocked = plan.values().contains(cur);
                    if (blocked) continue;
                    if (tick - Math.max(lastVacated.get(3), lastVacated.get(4)) < 2)
                        continue;
                }
                if ((cur == 5 && next == 6) || (cur == 2 && next == 5)) {
                    boolean comingFrom10 = plan.entrySet().stream()
                            .anyMatch(e -> trainLocations.get(e.getKey()) == 10 && e.getValue() == 6);
                    if (comingFrom10) continue;
                    if (tick - Math.max(lastVacated.get(5), lastVacated.get(6)) < 2)
                        continue;
                }
                if ((cur == 8 || cur == 9) && next == 10) {
                    boolean comingFrom6 = plan.entrySet().stream()
                            .anyMatch(e -> trainLocations.get(e.getKey()) == 6 && e.getValue() == 10);
                    if (comingFrom6) continue;
                    if (tick - lastVacated.get(10) < 2) continue;
                }
                plan.put(n, next);
            }
        }
        int moved = 0;
        Set<Integer> usedTargets = new HashSet<>();
        for (String n : ordered) {
            if (!plan.containsKey(n)) continue;
            int cur = trainLocations.get(n);
            int next = plan.get(n);
            if (next != -1 && usedTargets.contains(next)) continue;
            usedTargets.add(next);
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
            throw new IllegalArgumentException("Invalid section " + s);
        return sectionOccupancy.get(s);
    }

    @Override
    public int getTrain(String n) {
        if (!trains.containsKey(n))
            throw new IllegalArgumentException("No train: " + n);
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

    private boolean isPassengerTrain(String t) {
        Train tr = trains.get(t);
        if (tr == null || tr.path.isEmpty()) return false;
        int f = tr.path.get(0);
        return Arrays.asList(1, 2, 5, 6, 8, 9, 10).contains(f);
    }

    private boolean isFreightTrain(String t) {
        Train tr = trains.get(t);
        if (tr == null || tr.path.isEmpty()) return false;
        int f = tr.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(f);
    }
}
