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

    // ===== Public API =====

    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException {
        if (trainName == null) throw new IllegalArgumentException("Null train name");
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
            throw new IllegalArgumentException("No valid path from " + entryTrackSection + " to " + destinationTrackSection);
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
        Set<String> toMove = Arrays.stream(trainNames).collect(Collectors.toCollection(LinkedHashSet::new));
        for (String n : toMove) {
            if (!trains.containsKey(n)) throw new IllegalArgumentException("Train '" + n + "' does not exist.");
        }

        // Build requests for this tick
        class Req {
            final String train;
            final int src;
            final int tgt; // -1 means exit
            Req(String train, int src, int tgt){ this.train=train; this.src=src; this.tgt=tgt; }
        }

        // Deterministic order (passenger before freight, then name)
        Comparator<String> prio = Comparator
                .comparing((String n) -> isFreightTrain(n)) // false (passenger) first
                .thenComparing(n -> n);

        List<String> ordered = toMove.stream()
                .filter(trainLocations::containsKey) // ignore trains already gone
                .sorted(prio)
                .collect(Collectors.toList());

        Map<String, Req> requests = new LinkedHashMap<>();
        for (String n : ordered) {
            int src = trainLocations.get(n);
            Train t = trains.get(n);
            if (src == t.destination) {
                requests.put(n, new Req(n, src, -1)); // exit
            } else {
                int nxt = getNextOnPath(n);
                if (nxt != -1) requests.put(n, new Req(n, src, nxt));
            }
        }

        // Approvals per tick using per-target arbitration
        Map<String, Integer> approvedMoves = new HashMap<>(); // train -> newSection(-1 exit)
        Set<Integer> reservedTargets = new HashSet<>();

        // We may need a couple of passes to allow “follow-the-leader” (when the target frees this tick)
        int lastApproved = -1;
        while (approvedMoves.size() > lastApproved) {
            lastApproved = approvedMoves.size();

            // Group requests by target section (excluding exits)
            Map<Integer, List<Req>> byTarget = new HashMap<>();
            List<Req> exitReqs = new ArrayList<>();

            for (Req r : requests.values()) {
                if (approvedMoves.containsKey(r.train)) continue; // already approved
                if (r.tgt == -1) { exitReqs.add(r); continue; }

                // Block 3<->4 if passenger presence at 1/5/6
                if (isFreightCrossing(r.src, r.tgt)) {
                    if (sectionOccupancy.get(1) != null || sectionOccupancy.get(5) != null || sectionOccupancy.get(6) != null) {
                        continue; // cannot even request this tick
                    }
                }
                byTarget.computeIfAbsent(r.tgt, k -> new ArrayList<>()).add(r);
            }

            // Always approve exits first
            for (Req r : exitReqs) {
                approvedMoves.put(r.train, -1);
            }

            // For each target, pick ONE winner
            for (Map.Entry<Integer, List<Req>> e : byTarget.entrySet()) {
                int target = e.getKey();
                if (reservedTargets.contains(target)) continue;

                String occ = sectionOccupancy.get(target);

                // target is free this tick if empty OR its occupant is approved to move out
                boolean targetFreesThisTick = (occ == null) || (occ != null && approvedMoves.containsKey(occ));

                if (!targetFreesThisTick) continue; // cannot enter

                // Choose winner by (passenger first) then junction rank then name
                Comparator<Req> reqCmp = Comparator
                        .comparing((Req r) -> isFreightTrain(r.train)) // passenger first
                        .thenComparing((Req r) -> junctionSourceRank(target, r.src))
                        .thenComparing(r -> r.train);

                Req winner = e.getValue().stream().min(reqCmp).orElse(null);
                if (winner == null) continue;

                // Additional safety: nobody else already approved to the same target
                if (reservedTargets.contains(target)) continue;

                approvedMoves.put(winner.train, target);
                reservedTargets.add(target);
            }
        }

        // Execute in deterministic order
        int moved = 0;
        for (String n : ordered) {
            if (!approvedMoves.containsKey(n)) continue;

            int newPos = approvedMoves.get(n);
            int oldPos = trainLocations.getOrDefault(n, -1);
            if (oldPos == -1) continue; // already gone

            if (newPos == -1) {
                // exit
                sectionOccupancy.put(oldPos, null);
                trainLocations.remove(n);
            } else {
                sectionOccupancy.put(oldPos, null);
                sectionOccupancy.put(newPos, n);
                trainLocations.put(n, newPos);
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

    // ===== Helpers =====

    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> g = buildGraph();
        if (!g.containsKey(start)) return Collections.emptyList();

        Queue<List<Integer>> q = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        q.add(Collections.singletonList(start));
        seen.add(start);

        while (!q.isEmpty()) {
            List<Integer> path = q.poll();
            int u = path.get(path.size() - 1);
            if (u == end) return path;
            for (int v : g.getOrDefault(u, Collections.emptyList())) {
                if (seen.add(v)) {
                    List<Integer> np = new ArrayList<>(path);
                    np.add(v);
                    q.add(np);
                }
            }
        }
        return Collections.emptyList();
    }

    private Map<Integer, List<Integer>> buildGraph() {
        Map<Integer, List<Integer>> g = new HashMap<>();
        // Passenger corridor (bi-directional for planning)
        g.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        g.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        g.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        g.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        g.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        // Freight corridor
        g.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        g.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        g.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        g.computeIfAbsent(11, k -> new ArrayList<>()).add(7);
        return g;
    }

    private int getNextOnPath(String train) {
        Train t = trains.get(train);
        if (!trainLocations.containsKey(train)) return -1;
        int cur = trainLocations.get(train);
        int idx = t.path.indexOf(cur);
        if (idx >= 0 && idx < t.path.size() - 1) return t.path.get(idx + 1);
        return -1;
    }

    private boolean isPassengerTrain(String n) {
        Train t = trains.get(n);
        if (t == null || t.path.isEmpty()) return false;
        int start = t.path.get(0);
        return Arrays.asList(1, 2, 5, 6, 8, 9, 10).contains(start);
    }
    private boolean isFreightTrain(String n) { return !isPassengerTrain(n); }

    private boolean isFreightCrossing(int src, int tgt) {
        return (src == 3 && tgt == 4) || (src == 4 && tgt == 3);
    }

    // Junction-specific priority: smaller is better
    private int junctionSourceRank(int target, int src) {
        switch (target) {
            case 5:  // entrants: 1,2,6  (prefer mainline)
                if (src == 1) return 0;
                if (src == 2) return 1;
                if (src == 6) return 2;
                return 3;
            case 6:  // entrants: 5,10 (prefer 5 over 10)
                if (src == 5) return 0;
                if (src == 10) return 1;
                return 2;
            case 10: // entrants: 6,8,9 (prefer 6 > 8 > 9)
                if (src == 6) return 0;
                if (src == 8) return 1;
                if (src == 9) return 2;
                return 3;
            default:
                return 0;
        }
    }
}
