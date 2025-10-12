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
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException {
        if (trainName == null) throw new IllegalArgumentException("Train name is null.");
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
        Train t = new Train(trainName, destinationTrackSection, path);
        trains.put(trainName, t);
        trainLocations.put(trainName, entryTrackSection);
        sectionOccupancy.put(entryTrackSection, trainName);
    }

    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        if (trainNames == null) trainNames = new String[0];
        // Validate
        Set<String> toMove = new HashSet<>(Arrays.asList(trainNames));
        for (String n : toMove) {
            if (!trains.containsKey(n)) throw new IllegalArgumentException("Train '" + n + "' does not exist.");
        }

        // Deterministic priority: passenger first (false < true), then by name
        Comparator<String> priority = Comparator
                .comparing((String n) -> isFreightTrain(n)) // passenger=false first
                .thenComparing(n -> n);

        List<String> order = toMove.stream()
                .filter(trainLocations::containsKey) // still on corridor
                .sorted(priority)
                .collect(Collectors.toList());

        Map<String, Integer> planned = new HashMap<>();

        // Iteratively plan moves until no new plans can be added
        int lastPlanned = -1;
        while (planned.size() > lastPlanned) {
            lastPlanned = planned.size();

            for (String name : order) {
                if (planned.containsKey(name)) continue;
                Integer cur = trainLocations.get(name);
                if (cur == null) continue; // already exited earlier in planning
                Train t = trains.get(name);

                // Exit rule: when at destination and asked to move → leave corridor
                if (cur == t.destination) {
                    planned.put(name, -1);
                    continue;
                }

                int nxt = nextHop(name, cur, t.path);
                if (nxt == -1) continue;

                // Availability check
                String occ = sectionOccupancy.get(nxt);

                // 1) If empty and nobody else already targeted nxt, OK
                boolean emptyTarget = (occ == null) && !planned.containsValue(nxt);

                // 2) If occupied by a train that will move elsewhere (not swapping back), OK
                boolean occupantWillLeave = false;
                if (occ != null && toMove.contains(occ) && planned.containsKey(occ)) {
                    int occTarget = planned.get(occ);
                    // forbid direct swap (occ moves to cur while we move to nxt=occ's cur)
                    if (occTarget != cur) {
                        // also ensure no other train already planned to nxt (double target)
                        if (!planned.containsValue(nxt)) {
                            occupantWillLeave = true;
                        }
                    }
                }

                // forbid head-on swap in same tick: if occ exists and its planned target is cur, we must NOT move
                boolean headOnSwap =
                        (occ != null && toMove.contains(occ) && planned.containsKey(occ) && planned.get(occ) == cur);

                if (!(emptyTarget || occupantWillLeave) || headOnSwap) {
                    continue;
                }

                // Crossing rule: ONLY block 3↔4 when passenger(s) present on 1/5/6
                boolean isCrossing34 = (cur == 3 && nxt == 4) || (cur == 4 && nxt == 3);
                if (isCrossing34) {
                    if (sectionOccupancy.get(1) != null
                            || sectionOccupancy.get(5) != null
                            || sectionOccupancy.get(6) != null) {
                        // blocked by passenger presence on corridor
                        continue;
                    }
                }
                // NOTE: do NOT block 7↔3 or 7↔4 moves. Only the 3↔4 segment crosses passenger lines.

                planned.put(name, nxt);
            }
        }

        // Execute in deterministic order
        int moved = 0;
        for (String name : order) {
            if (!planned.containsKey(name)) continue;
            Integer cur = trainLocations.get(name);
            if (cur == null) continue; // already exited
            int target = planned.get(name);

            if (target == -1) {
                // exit
                sectionOccupancy.put(cur, null);
                trainLocations.remove(name);
            } else {
                // move
                sectionOccupancy.put(cur, null);
                sectionOccupancy.put(target, name);
                trainLocations.put(name, target);
            }
            moved++;
        }
        return moved;
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

    // ---------- helpers ----------

    private int nextHop(String trainName, int currentSection, List<Integer> path) {
        int idx = path.indexOf(currentSection);
        if (idx >= 0 && idx + 1 < path.size()) return path.get(idx + 1);
        return -1;
    }

    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> g = buildGraph();
        if (!g.containsKey(start)) return Collections.emptyList();
        Queue<List<Integer>> q = new LinkedList<>();
        q.add(Collections.singletonList(start));
        Set<Integer> seen = new HashSet<>();
        seen.add(start);
        while (!q.isEmpty()) {
            List<Integer> p = q.poll();
            int last = p.get(p.size() - 1);
            if (last == end) return p;
            for (int nb : g.getOrDefault(last, Collections.emptyList())) {
                if (seen.add(nb)) {
                    List<Integer> np = new ArrayList<>(p);
                    np.add(nb);
                    q.add(np);
                }
            }
        }
        return Collections.emptyList();
    }

    private Map<Integer, List<Integer>> buildGraph() {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        // Passenger corridor (bi-directional for pathfinding)
        graph.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        graph.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        graph.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        graph.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        graph.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        graph.computeIfAbsent(9, k -> new ArrayList<>()).add(10);

        // Freight Y (bi-directional for pathfinding)
        graph.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        graph.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        graph.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        graph.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return graph;
    }

    private boolean isFreightTrain(String name) {
        Train t = trains.get(name);
        if (t == null || t.path.isEmpty()) return false;
        int first = t.path.get(0);
        return first == 3 || first == 4 || first == 7 || first == 11;
    }
}
