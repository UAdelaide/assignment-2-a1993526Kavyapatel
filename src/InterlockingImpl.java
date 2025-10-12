import java.util.*;
import java.util.stream.Collectors;


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
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection) {
        if (trains.containsKey(trainName)) {
            throw new IllegalArgumentException("Train name already used: " + trainName);
        }
        if (!validSections.contains(entryTrackSection) || !validSections.contains(destinationTrackSection)) {
            throw new IllegalArgumentException("Invalid section");
        }
        if (sectionOccupancy.get(entryTrackSection) != null) {
            throw new IllegalStateException("Entry section occupied");
        }

        List<Integer> path = findPath(entryTrackSection, destinationTrackSection);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("No valid path");
        }

        trains.put(trainName, new Train(trainName, destinationTrackSection, path));
        trainLocations.put(trainName, entryTrackSection);
        sectionOccupancy.put(entryTrackSection, trainName);
    }

    @Override
    public int moveTrains(String... trainNames) {
        Set<String> moveSet = new HashSet<>(Arrays.asList(trainNames));
        for (String n : moveSet) {
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("Unknown train: " + n);
        }

        Comparator<String> prio = Comparator
                .comparing((String n) -> isFreightTrain(n)) // passenger (false) before freight (true)
                .thenComparing(n -> n);

        List<String> ordered = moveSet.stream()
                .filter(trainLocations::containsKey)
                .sorted(prio)
                .collect(Collectors.toList());

        // Check if any passenger is using the main corridor this tick
        Set<Integer> mainCorridor = new HashSet<>(Arrays.asList(1, 2, 5, 6));
        boolean passengerUsingCorridor = ordered.stream().anyMatch(t -> {
            if (!isPassengerTrain(t)) return false;
            int cur = trainLocations.get(t);
            int nxt = getNextSectionForTrain(t);
            return mainCorridor.contains(cur) || mainCorridor.contains(nxt);
        });

        Map<String, Integer> plan = new HashMap<>();

        // === Planning Phase ===
        for (String n : ordered) {
            Train t = trains.get(n);
            int cur = trainLocations.get(n);

            // Exit immediately if already at destination
            if (cur == t.destination) {
                plan.put(n, -1);
                continue;
            }

            int next = getNextSectionForTrain(n);
            if (next == -1) continue;

            String occ = sectionOccupancy.get(next);

            // Freight crossing control (3↔4↔7)
            boolean isFreightCross = (cur == 3 && (next == 4 || next == 7)) ||
                                     (cur == 4 && next == 3) ||
                                     (cur == 7 && next == 3);
            if (isFreightCross) {
                if (passengerUsingCorridor) continue; // passenger priority
                if (occ != null && moveSet.contains(occ)) {
                    int occNext = getNextSectionForTrain(occ);
                    if (occNext == cur && prio.compare(n, occ) > 0) continue; // lower priority loses
                }
            }

            // Merge control for sections 5 & 10
            if (next == 5 || next == 10) {
                boolean taken = false;
                for (Map.Entry<String, Integer> e : plan.entrySet()) {
                    if (e.getValue() == next) {
                        taken = true;
                        if (prio.compare(n, e.getKey()) < 0) { // we win
                            plan.remove(e.getKey());
                            plan.put(n, next);
                        }
                        break;
                    }
                }
                if (taken) continue;
            }

            // Normal move if free or chain reaction
            boolean willFree = (occ != null && moveSet.contains(occ));
            if (occ != null && !willFree) continue;
            if (occ != null && moveSet.contains(occ)) {
                int occNext = getNextSectionForTrain(occ);
                if (occNext == cur && prio.compare(n, occ) > 0) continue;
            }
            plan.put(n, next);
        }

        // === Chain Reaction ===
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String n : ordered) {
                if (plan.containsKey(n)) continue;
                Train t = trains.get(n);
                int cur = trainLocations.get(n);
                if (cur == t.destination) {
                    plan.put(n, -1);
                    changed = true;
                    continue;
                }
                int next = getNextSectionForTrain(n);
                if (next == -1) continue;
                String occ = sectionOccupancy.get(next);
                if (occ != null && plan.containsKey(occ) && plan.get(occ) != next) {
                    plan.put(n, next);
                    changed = true;
                }
            }
        }

        // === Execution Phase ===
        int moved = 0;
        for (String n : ordered) {
            if (!plan.containsKey(n)) continue;
            int cur = trainLocations.get(n);
            int next = plan.get(n);
            sectionOccupancy.put(cur, null);
            if (next == -1) {
                trainLocations.remove(n);
            } else {
                sectionOccupancy.put(next, n);
                trainLocations.put(n, next);
            }
            moved++;
        }
        return moved;
    }

    @Override
    public String getSection(int trackSection) {
        if (!validSections.contains(trackSection))
            throw new IllegalArgumentException("Invalid section");
        return sectionOccupancy.get(trackSection);
    }

    @Override
    public int getTrain(String trainName) {
        if (!trains.containsKey(trainName))
            throw new IllegalArgumentException("Unknown train: " + trainName);
        return trainLocations.getOrDefault(trainName, -1);
    }

    // === Helper Methods ===
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

    private int getNextSectionForTrain(String trainName) {
        Train tr = trains.get(trainName);
        if (tr == null) return -1;
        int cur = trainLocations.get(trainName);
        int idx = tr.path.indexOf(cur);
        if (idx != -1 && idx < tr.path.size() - 1)
            return tr.path.get(idx + 1);
        return -1;
    }

    private boolean isPassengerTrain(String name) {
        Train t = trains.get(name);
        if (t == null || t.path.isEmpty()) return false;
        int f = t.path.get(0);
        return Arrays.asList(1, 2, 5, 6, 8, 9, 10).contains(f);
    }

    private boolean isFreightTrain(String name) {
        Train t = trains.get(name);
        if (t == null || t.path.isEmpty()) return false;
        int f = t.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(f);
    }
}
