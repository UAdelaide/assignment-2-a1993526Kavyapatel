import java.util.*;
import java.util.stream.Collectors;

public class InterlockingImpl implements Interlocking {

    private static class Train {
        final String name;
        final int destination;
        final List<Integer> path;

        Train(String n, int d, List<Integer> p) {
            name = n;
            destination = d;
            path = p;
        }
    }

    private final Map<String, Train> trains = new HashMap<>();
    private final Map<String, Integer> locations = new HashMap<>();
    private final Map<Integer, String> occupancy = new HashMap<>();
    private final Set<Integer> valid = new HashSet<>();

    // --- new global cooldown tracker ---
    private final Set<Integer> cooldownZones = new HashSet<>();

    public InterlockingImpl() {
        for (int i = 1; i <= 11; i++) {
            valid.add(i);
            occupancy.put(i, null);
        }
    }

    @Override
    public void addTrain(String name, int entry, int dest)
            throws IllegalArgumentException, IllegalStateException {
        if (trains.containsKey(name))
            throw new IllegalArgumentException("Train name already exists");
        if (!valid.contains(entry) || !valid.contains(dest))
            throw new IllegalArgumentException("Invalid entry or destination");
        if (occupancy.get(entry) != null)
            throw new IllegalStateException("Entry section occupied");

        List<Integer> path = findPath(entry, dest);
        if (path.isEmpty())
            throw new IllegalArgumentException("No valid path from " + entry + " to " + dest);

        Train t = new Train(name, dest, path);
        trains.put(name, t);
        locations.put(name, entry);
        occupancy.put(entry, name);
    }

    @Override
    public int moveTrains(String... trainNames) throws IllegalArgumentException {
        Set<String> set = new HashSet<>(Arrays.asList(trainNames));
        for (String n : set)
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("Train " + n + " not found");

        Map<Integer, String> start = new HashMap<>(occupancy);
        Map<String, Integer> moveReq = new HashMap<>();
        Map<String, Integer> exitReq = new HashMap<>();

        // --- clear cooldowns each tick ---
        cooldownZones.clear();

        // Build movement and exit requests
        for (String n : set) {
            Train t = trains.get(n);
            if (!locations.containsKey(n)) continue;
            int cur = locations.get(n);
            if (cur == t.destination) {
                exitReq.put(n, -1);
            } else {
                int nxt = getNext(n);
                if (nxt != -1) moveReq.put(n, nxt);
            }
        }

        // Find conflicts
        Map<Integer, List<String>> inv = new HashMap<>();
        for (var e : moveReq.entrySet())
            inv.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        Set<Integer> conflicted = inv.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Comparator<String> typeThenName = Comparator
                .comparing((String n) -> isFreight(n))
                .thenComparing(n -> n);

        boolean passengerBlocksFreight34 =
                (start.get(1) != null) || (start.get(5) != null) || (start.get(6) != null);

        // === FILTER ALLOWED MOVES (shared-zone aware) ===
        Set<String> allowed = new HashSet<>();
        Set<Integer> sharedZones = new HashSet<>(Arrays.asList(3, 4, 5, 6, 10));

        for (String n : moveReq.keySet().stream().sorted(typeThenName).collect(Collectors.toList())) {
            int tgt = moveReq.get(n);
            int cur = locations.get(n);

            if (conflicted.contains(tgt)) continue;
            // --- allow move if target being exited this tick ---
            if (start.get(tgt) != null && !exitReq.containsKey(start.get(tgt))) continue;
            if (occupancy.get(tgt) != null) continue;

            // Prevent two trains from entering opposite shared junctions at same tick
            boolean junctionConflict = cooldownZones.contains(tgt) ||
                allowed.stream().anyMatch(x -> {
                    int xtgt = moveReq.get(x);
                    return sharedZones.contains(xtgt) && sharedZones.contains(tgt);
                });
            if (junctionConflict) continue;

            // Prevent freight 3<->4 crossing when passenger active
            if ((cur == 3 && tgt == 4) || (cur == 4 && tgt == 3)) {
                if (passengerBlocksFreight34) continue;
                else passengerBlocksFreight34 = false; // unlock when passengers idle
            }

            allowed.add(n);
        }

        // === EXECUTION PHASE ===
        int moved = 0;
        Set<Integer> freed = new HashSet<>();
        Set<Integer> cooldown = new HashSet<>();

        // Handle exits first
        for (String n : exitReq.keySet().stream().sorted().collect(Collectors.toList())) {
            if (!locations.containsKey(n)) continue;
            int cur = locations.get(n);
            occupancy.put(cur, null);
            locations.remove(n);
            freed.add(cur);
            moved++;
        }

        // Execute allowed moves
        for (String n : allowed.stream().sorted(typeThenName).collect(Collectors.toList())) {
            if (!locations.containsKey(n)) continue;
            int cur = locations.get(n);
            int tgt = moveReq.get(n);
            if (occupancy.get(tgt) != null) continue;

            occupancy.put(cur, null);
            occupancy.put(tgt, n);
            locations.put(n, tgt);
            moved++;

            // mark junctions for cooldown AFTER movement
            if (Arrays.asList(5, 6, 10).contains(tgt) || Arrays.asList(5, 6, 10).contains(cur))
                cooldown.add(tgt);
        }

        cooldown.addAll(freed);
        cooldownZones.addAll(cooldown); // store cooldowns globally
        return moved;
    }

    @Override
    public String getSection(int s) throws IllegalArgumentException {
        if (!valid.contains(s))
            throw new IllegalArgumentException("Invalid section");
        return occupancy.get(s);
    }

    @Override
    public int getTrain(String name) throws IllegalArgumentException {
        if (!trains.containsKey(name))
            throw new IllegalArgumentException("Train not found");
        return locations.getOrDefault(name, -1);
    }

    // === HELPER METHODS ===
    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> g = buildGraph();
        if (!g.containsKey(start)) return Collections.emptyList();
        Queue<List<Integer>> q = new LinkedList<>();
        q.add(Collections.singletonList(start));
        Set<Integer> vis = new HashSet<>();
        vis.add(start);

        while (!q.isEmpty()) {
            List<Integer> p = q.poll();
            int last = p.get(p.size() - 1);
            if (last == end) return p;
            for (int nb : g.getOrDefault(last, List.of())) {
                if (!vis.contains(nb)) {
                    vis.add(nb);
                    List<Integer> np = new ArrayList<>(p);
                    np.add(nb);
                    q.add(np);
                }
            }
        }
        return Collections.emptyList();
    }

    private Map<Integer, List<Integer>> buildGraph() {
        Map<Integer, List<Integer>> g = new HashMap<>();
        // passenger route
        g.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        g.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        g.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        g.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        g.computeIfAbsent(9, k -> new ArrayList<>()).add(10);

        // freight route
        g.computeIfAbsent(3, k -> new ArrayList<>()).addAll(Arrays.asList(4, 7));
        g.computeIfAbsent(4, k -> new ArrayList<>()).add(3);
        g.computeIfAbsent(7, k -> new ArrayList<>()).addAll(Arrays.asList(3, 11));
        g.computeIfAbsent(11, k -> new ArrayList<>()).add(7);

        return g;
    }

    private int getNext(String name) {
        if (!locations.containsKey(name)) return -1;
        Train t = trains.get(name);
        int cur = locations.get(name);
        int i = t.path.indexOf(cur);
        return (i != -1 && i < t.path.size() - 1) ? t.path.get(i + 1) : -1;
    }

    private boolean isFreight(String name) {
        Train t = trains.get(name);
        if (t == null || t.path.isEmpty()) return false;
        int first = t.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(first);
    }
}
