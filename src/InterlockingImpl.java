import java.util.*;
import java.util.stream.Collectors;


public class InterlockingImpl implements Interlocking {

    private static class Train {
        final String name;
        final int destination;
        final List<Integer> path;
        boolean arrived = false;

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
    public void addTrain(String trainName, int entry, int dest) {
        if (trains.containsKey(trainName))
            throw new IllegalArgumentException("Duplicate train name");
        if (!validSections.contains(entry) || !validSections.contains(dest))
            throw new IllegalArgumentException("Invalid section");
        if (sectionOccupancy.get(entry) != null)
            throw new IllegalStateException("Section occupied");

        List<Integer> path = findPath(entry, dest);
        if (path.isEmpty())
            throw new IllegalArgumentException("No valid path");

        Train t = new Train(trainName, dest, path);
        trains.put(trainName, t);
        trainLocations.put(trainName, entry);
        sectionOccupancy.put(entry, trainName);
    }

    @Override
    public int moveTrains(String... names) {
        Set<String> moveSet = new HashSet<>(Arrays.asList(names));
        for (String n : moveSet)
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("No train: " + n);

        Map<String, Integer> plan = new HashMap<>();

        // Deterministic order — passenger before freight, then alphabetically
        List<String> ordered = moveSet.stream()
                .filter(trainLocations::containsKey)
                .sorted(Comparator.comparing(this::isFreightTrain).thenComparing(n -> n))
                .collect(Collectors.toList());

        // === Planning Phase ===
        for (String n : ordered) {
            Train t = trains.get(n);
            int cur = trainLocations.get(n);

            // Wait one tick before exit
            if (cur == t.destination) {
                if (!t.arrived) {
                    t.arrived = true;
                    continue;
                } else {
                    plan.put(n, -1);
                    continue;
                }
            }

            int next = getNextSectionForTrain(n);
            if (next == -1) continue;

            String occ = sectionOccupancy.get(next);

            // --- Passenger merge priority (sections 5, 10) ---
            if (next == 5 || next == 10) {
                for (Map.Entry<String, Integer> e : trainLocations.entrySet()) {
                    if (Objects.equals(e.getValue(), next)) continue;
                    if (getNextSectionForTrain(e.getKey()) == next) {
                        int thisPos = cur;
                        int otherPos = e.getValue();
                        if (thisPos > otherPos) continue; // lower-number section wins
                    }
                }
            }

            // --- Freight coordination (3,4,7,11) ---
            if ((cur == 3 && next == 4) || (cur == 4 && next == 3) ||
                (cur == 7 && next == 3) || (cur == 3 && next == 7)) {

                // If passenger line active near 1–6–10, freight must wait
                boolean passengerActive =
                        (sectionOccupancy.get(1) != null) ||
                        (sectionOccupancy.get(5) != null) ||
                        (sectionOccupancy.get(6) != null) ||
                        (sectionOccupancy.get(10) != null);

                if (passengerActive)
                    continue;

                // If opposite freight approaching same section, block both
                String opp3 = sectionOccupancy.get(3);
                String opp4 = sectionOccupancy.get(4);
                String opp7 = sectionOccupancy.get(7);

                if ((opp3 != null && getNextSectionForTrain(opp3) == next) ||
                    (opp4 != null && getNextSectionForTrain(opp4) == next) ||
                    (opp7 != null && getNextSectionForTrain(opp7) == next))
                    continue;
            }

            // --- Normal occupancy or planned free section ---
            boolean willFree = occ != null && moveSet.contains(occ)
                    && getNextSectionForTrain(occ) != -1
                    && getNextSectionForTrain(occ) != next;

            if (occ == null || willFree)
                plan.put(n, next);
        }

        // === Conflict resolution ===
        Map<Integer, List<String>> conflicts = new HashMap<>();
        for (Map.Entry<String, Integer> e : plan.entrySet()) {
            if (e.getValue() == -1) continue;
            conflicts.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }

        for (Map.Entry<Integer, List<String>> entry : conflicts.entrySet()) {
            List<String> claimers = entry.getValue();
            if (claimers.size() > 1) {
                String winner = claimers.stream()
                        .min(Comparator.comparingInt(trainLocations::get))
                        .orElse(claimers.get(0));
                for (String loser : claimers)
                    if (!loser.equals(winner))
                        plan.remove(loser);
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
    public String getSection(int s) {
        if (!validSections.contains(s))
            throw new IllegalArgumentException("Invalid section");
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

    private boolean isFreightTrain(String t) {
        Train tr = trains.get(t);
        if (tr == null || tr.path.isEmpty()) return false;
        int f = tr.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(f);
    }
}
