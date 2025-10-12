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
    private final Map<String, Integer> trainLoc = new HashMap<>();
    private final Map<Integer, String> occ = new HashMap<>();
    private final Set<Integer> valid = new HashSet<>();

    public InterlockingImpl() {
        for (int i = 1; i <= 11; i++) {
            valid.add(i);
            occ.put(i, null);
        }
    }

    @Override
    public void addTrain(String name, int entry, int dest)
            throws IllegalArgumentException, IllegalStateException {
        if (trains.containsKey(name))
            throw new IllegalArgumentException("Duplicate train " + name);
        if (!valid.contains(entry) || !valid.contains(dest))
            throw new IllegalArgumentException("Invalid section");
        if (occ.get(entry) != null)
            throw new IllegalStateException("Section " + entry + " occupied");

        List<Integer> path = findPath(entry, dest);
        if (path.isEmpty())
            throw new IllegalArgumentException("No path from " + entry + " to " + dest);

        Train t = new Train(name, dest, path);
        trains.put(name, t);
        trainLoc.put(name, entry);
        occ.put(entry, name);
    }

    @Override
    public int moveTrains(String... names) throws IllegalArgumentException {
        if (names == null) names = new String[0];
        Set<String> moving = new HashSet<>(Arrays.asList(names));
        for (String n : moving)
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("Unknown train: " + n);

        // Base priority: passengers before freight, then deterministic by name
        Comparator<String> basePrio = Comparator
                .comparing((String n) -> isFreight(n))  // false (passenger) first
                .thenComparing(n -> n);

        // Planning results
        Map<String, Integer> plan = new HashMap<>();

        boolean changed = true;
        while (changed) {
            changed = false;

            // Sections that will be vacated this tick (from already planned moves)
            Set<Integer> willVacate = new HashSet<>();
            for (Map.Entry<String, Integer> e : plan.entrySet()) {
                String tn = e.getKey();
                Integer cur = trainLoc.get(tn);
                if (cur != null) willVacate.add(cur);
            }

            // Gather all move intents for this pass
            Map<Integer, List<String>> wantTarget = new HashMap<>(); // next section -> list of trains wanting it
            List<String> exitNow = new ArrayList<>();

            // Iterate in basePrio so earlier (higher) priority trains place intents first
            List<String> order = moving.stream()
                    .filter(trainLoc::containsKey)
                    .sorted(basePrio)
                    .collect(Collectors.toList());

            for (String name : order) {
                if (plan.containsKey(name)) continue; // already planned

                Integer cur = trainLoc.get(name);
                if (cur == null) continue; // already exited

                Train t = trains.get(name);
                if (cur == t.destination) {
                    exitNow.add(name);  // exit this tick
                    continue;
                }

                int next = nextHop(cur, t.path);
                if (next == -1) continue;

                // 3<->4 block if corridor (1/5/6) has any train
                if ((cur == 3 && next == 4) || (cur == 4 && next == 3)) {
                    if (occ.get(1) != null || occ.get(5) != null || occ.get(6) != null) {
                        continue;
                    }
                }

                // Is next section effectively free this tick?
                String nowOcc = occ.get(next);
                boolean vacating = nowOcc != null && moving.contains(nowOcc) && plan.containsKey(nowOcc);
                boolean free = (nowOcc == null) || vacating || willVacate.contains(next);

                if (!free) continue;

                // Record the intent to occupy 'next'
                wantTarget.computeIfAbsent(next, k -> new ArrayList<>()).add(name);
            }

            // Resolve exits first (no conflicts)
            for (String tn : exitNow) {
                if (!plan.containsKey(tn)) {
                    plan.put(tn, -1);
                    changed = true;
                }
            }

            // Resolve merge contests per target with junction-specific tie-breakers
            for (Map.Entry<Integer, List<String>> e : wantTarget.entrySet()) {
                int target = e.getKey();
                List<String> claimants = e.getValue();
                // If already allocated by a previous iteration, skip
                if (plan.values().contains(target)) continue;
                if (claimants.isEmpty()) continue;

                // Choose exactly one winner per target
                String winner = pickWinnerForTarget(target, claimants, basePrio);
                if (winner != null && !plan.containsKey(winner)) {
                    plan.put(winner, target);
                    changed = true;
                }
            }
        }

        // Execute in basePrio order (deterministic)
        int moved = 0;
        List<String> execOrder = moving.stream()
                .filter(plan::containsKey)
                .sorted(basePrio)
                .collect(Collectors.toList());

        for (String name : execOrder) {
            Integer cur = trainLoc.get(name);
            if (cur == null) continue;
            int dest = plan.get(name);
            if (dest == -1) {
                occ.put(cur, null);
                trainLoc.remove(name);
            } else {
                occ.put(cur, null);
                occ.put(dest, name);
                trainLoc.put(name, dest);
            }
            moved++;
        }
        return moved;
    }

    @Override
    public String getSection(int s) throws IllegalArgumentException {
        if (!valid.contains(s))
            throw new IllegalArgumentException("Invalid section " + s);
        return occ.get(s);
    }

    @Override
    public int getTrain(String n) throws IllegalArgumentException {
        if (!trains.containsKey(n))
            throw new IllegalArgumentException("Unknown train " + n);
        return trainLoc.getOrDefault(n, -1);
    }

    /* ---------------- Helpers ---------------- */

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

    private Map<Integer, List<Integer>> graph() {
        Map<Integer, List<Integer>> g = new HashMap<>();
        // Passenger corridor (bidirectional for pathfinding)
        add(g, 1, 5);
        add(g, 2, 5);
        add(g, 5, 6);
        add(g, 6, 10);
        add(g, 10, 8);
        add(g, 10, 9);
        // Freight branch
        add(g, 3, 4);
        add(g, 3, 7);
        add(g, 7, 11);
        return g;
    }

    private void add(Map<Integer, List<Integer>> g, int a, int b) {
        g.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        g.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }

    private int nextHop(int cur, List<Integer> path) {
        int i = path.indexOf(cur);
        if (i >= 0 && i + 1 < path.size())
            return path.get(i + 1);
        return -1;
    }

    private boolean isFreight(String name) {
        Train t = trains.get(name);
        if (t == null || t.path.isEmpty()) return false;
        int f = t.path.get(0);
        return f == 3 || f == 4 || f == 7 || f == 11;
    }

    // Junction-specific winner selection for contested targets (5, 6, 10)
    private String pickWinnerForTarget(int target, List<String> claimants, Comparator<String> basePrio) {
        // Build comparator that adds a physical source-preference on top of passenger-first
        Comparator<String> cmp = basePrio.thenComparing((String n) -> {
            int src = trainLoc.get(n);
            return sourceRankForTarget(target, src);
        }).thenComparing(n -> n);

        return claimants.stream().min(cmp).orElse(null);
    }

    // Smaller rank = higher priority
    private int sourceRankForTarget(int target, int src) {
        // Tuned to typical junction expectations:
        //  - Into 5: prefer 1, then 2, then 6 (spur and mainline converge)
        //  - Into 6: prefer 5, then 10 (keep mainline flowing)
        //  - Into 10: prefer 6, then 8, then 9 (exit fan)
        if (target == 5) {
            if (src == 1) return 0;
            if (src == 2) return 1;
            if (src == 6) return 2;
            return 3;
        } else if (target == 6) {
            if (src == 5) return 0;
            if (src == 10) return 1;
            return 2;
        } else if (target == 10) {
            if (src == 6) return 0;
            if (src == 8) return 1;
            if (src == 9) return 2;
            return 3;
        }
        return 0; // for non-merge targets, no extra preference
    }
}
