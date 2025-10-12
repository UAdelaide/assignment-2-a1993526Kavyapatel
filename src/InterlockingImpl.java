import java.util.*;
import java.util.stream.Collectors;

/**
 * Interlocking implementation with deterministic planning, passenger priority at 3<->4,
 * and fair merges at sections 5, 6, and 10. Supports simultaneous vacate->enter in one tick.
 */
public class InterlockingImpl implements Interlocking {

    /* ------------ model ------------ */

    private static class Train {
        final String name;
        final int destination;
        final List<Integer> path; // inclusive from entry to destination

        Train(String name, int destination, List<Integer> path) {
            this.name = name;
            this.destination = destination;
            this.path = path;
        }
    }

    // train id -> train
    private final Map<String, Train> trains = new HashMap<>();
    // train id -> current section (or absent after exit)
    private final Map<String, Integer> trainLoc = new HashMap<>();
    // section -> train id or null
    private final Map<Integer, String> occ = new HashMap<>();
    // valid sections
    private final Set<Integer> valid = new HashSet<>();

    public InterlockingImpl() {
        for (int s = 1; s <= 11; s++) {
            valid.add(s);
            occ.put(s, null);
        }
    }

    /* ------------ API ------------ */

    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException {
        if (trains.containsKey(trainName)) {
            throw new IllegalArgumentException("Duplicate train name: " + trainName);
        }
        if (!valid.contains(entryTrackSection) || !valid.contains(destinationTrackSection)) {
            throw new IllegalArgumentException("Invalid section(s).");
        }
        if (occ.get(entryTrackSection) != null) {
            throw new IllegalStateException("Entry section occupied: " + entryTrackSection);
        }
        List<Integer> path = findPath(entryTrackSection, destinationTrackSection);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("No path " + entryTrackSection + " -> " + destinationTrackSection);
        }
        Train t = new Train(trainName, destinationTrackSection, path);
        trains.put(trainName, t);
        trainLoc.put(trainName, entryTrackSection);
        occ.put(entryTrackSection, trainName);
    }

    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        if (trainNames == null) trainNames = new String[0];
        Set<String> toMove = new HashSet<>(Arrays.asList(trainNames));
        for (String n : toMove) {
            if (!trains.containsKey(n)) {
                throw new IllegalArgumentException("Unknown train: " + n);
            }
        }

        // Priority: passenger before freight, then name
        Comparator<String> prio = Comparator
                .comparing((String n) -> isFreight(n)) // false (passenger) first
                .thenComparing(n -> n);

        List<String> order = toMove.stream()
                .filter(trainLoc::containsKey) // only still on map
                .sorted(prio)
                .collect(Collectors.toList());

        // plannedMoves: train -> target section (or -1 for exit)
        Map<String, Integer> planned = new HashMap<>();

        // iterative planning to allow cascades (A leaves -> B enters -> C enters ...)
        int lastCount = -1;
        while (planned.size() > lastCount) {
            lastCount = planned.size();

            // Compute which sections will be vacated this tick (based on current plans)
            Set<Integer> willVacate = new HashSet<>();
            for (Map.Entry<String, Integer> e : planned.entrySet()) {
                String t = e.getKey();
                Integer tgt = e.getValue();
                Integer cur = trainLoc.get(t);
                if (cur != null) {
                    // any planned move (including exit) vacates current
                    willVacate.add(cur);
                }
                // reservation of tgt will be handled via "reserved" map below
            }

            // Track reservations to avoid double-targeting
            Set<Integer> reservedTargets = planned.values().stream()
                    .filter(x -> x != -1)
                    .collect(Collectors.toSet());

            for (String name : order) {
                if (planned.containsKey(name)) continue; // already planned

                Integer cur = trainLoc.get(name);
                if (cur == null) continue; // already exited earlier in planning
                Train t = trains.get(name);

                // If already at destination, plan to exit
                if (cur == t.destination) {
                    planned.put(name, -1);
                    // update vacate cache immediately for later trains in this pass
                    willVacate.add(cur);
                    continue;
                }

                int next = nextHop(name, cur, t.path);
                if (next == -1) continue;

                // Passenger priority block at 3<->4 if any passenger sections (1,5,6) currently occupied
                if ((cur == 3 && next == 4) || (cur == 4 && next == 3)) {
                    if (occ.get(1) != null || occ.get(5) != null || occ.get(6) != null) {
                        continue; // blocked by passenger corridor activity
                    }
                }

                // Is target available now or will become free this tick?
                String occu = occ.get(next);
                boolean occupantWillLeave = false;
                if (occu != null && toMove.contains(occu) && planned.containsKey(occu)) {
                    // occupant has a plan (move or exit) -> will vacate
                    occupantWillLeave = true;
                }

                boolean targetFreeNow = (occu == null);
                boolean targetFreeThisTick = targetFreeNow || occupantWillLeave || willVacate.contains(next);

                // Merge fairness at 5, 6, 10:
                // exactly one entrant per tick; use deterministic priority "order"
                if (targetFreeThisTick) {
                    // cannot take if already reserved by some other planned move
                    if (reservedTargets.contains(next)) {
                        // BUT: if the existing reservation is by a lower-priority train (shouldn't happen
                        // because we plan in priority order), we could swap. We keep simple & deterministic: skip.
                        continue;
                    }

                    // Extra guard: if target has an occupant that is NOT moving this tick (and not vacating),
                    // then it's not free.
                    if (occu != null && !(toMove.contains(occu) && planned.containsKey(occu))) {
                        continue;
                    }

                    // Good: reserve and plan
                    planned.put(name, next);
                    reservedTargets.add(next);
                    // current section will vacate, which may help followers this pass
                    willVacate.add(cur);
                }
            }
        }

        // execute in priority order
        int moved = 0;
        for (String name : order) {
            Integer plan = planned.get(name);
            if (plan == null) continue;

            Integer cur = trainLoc.get(name);
            if (cur == null) continue; // already gone

            if (plan == -1) {
                // exit
                occ.put(cur, null);
                trainLoc.remove(name);
                moved++;
            } else {
                // move to new section
                occ.put(cur, null);
                occ.put(plan, name);
                trainLoc.put(name, plan);
                moved++;
            }
        }

        return moved;
    }

    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        if (!valid.contains(trackSection)) {
            throw new IllegalArgumentException("No such section: " + trackSection);
        }
        return occ.get(trackSection);
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        if (!trains.containsKey(trainName)) {
            throw new IllegalArgumentException("No such train: " + trainName);
        }
        return trainLoc.getOrDefault(trainName, -1);
    }

    /* ------------ helpers ------------ */

    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> g = graph();
        if (!g.containsKey(start)) return Collections.emptyList();
        Queue<List<Integer>> q = new ArrayDeque<>();
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

    // Bi-directional edges (as per the assignment diagram)
    private Map<Integer, List<Integer>> graph() {
        Map<Integer, List<Integer>> g = new HashMap<>();
        // Passenger corridor
        addEdge(g, 1, 5);
        addEdge(g, 2, 5);
        addEdge(g, 5, 6);
        addEdge(g, 6, 10);
        addEdge(g, 10, 8);
        addEdge(g, 10, 9);

        // Freight corridor
        addEdge(g, 3, 4);
        addEdge(g, 3, 7);
        addEdge(g, 7, 11);

        return g;
    }

    private void addEdge(Map<Integer, List<Integer>> g, int a, int b) {
        g.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        g.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }

    private int nextHop(String trainName, int cur, List<Integer> path) {
        int i = path.indexOf(cur);
        if (i >= 0 && i + 1 < path.size()) return path.get(i + 1);
        return -1;
    }

    private boolean isPassenger(String trainName) {
        // classify by entry side / first node on its path
        Train t = trains.get(trainName);
        if (t == null || t.path.isEmpty()) return false;
        int first = t.path.get(0);
        // passenger corridor nodes set
        return first == 1 || first == 2 || first == 5 || first == 6 || first == 8 || first == 9 || first == 10;
    }

    private boolean isFreight(String trainName) {
        Train t = trains.get(trainName);
        if (t == null || t.path.isEmpty()) return false;
        int first = t.path.get(0);
        return first == 3 || first == 4 || first == 7 || first == 11;
    }
}
