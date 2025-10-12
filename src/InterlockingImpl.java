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
        if (names == null)
            names = new String[0];
        Set<String> moving = new HashSet<>(Arrays.asList(names));
        for (String n : moving)
            if (!trains.containsKey(n))
                throw new IllegalArgumentException("Unknown train: " + n);

        Comparator<String> priority = Comparator
                .comparing((String n) -> isFreight(n)) // passengers first
                .thenComparing(n -> n);

        List<String> order = moving.stream()
                .filter(trainLoc::containsKey)
                .sorted(priority)
                .collect(Collectors.toList());

        Map<String, Integer> plan = new HashMap<>();

        boolean changed = true;
        while (changed) {
            changed = false;

            Set<Integer> willVacate = new HashSet<>();
            for (Map.Entry<String, Integer> e : plan.entrySet()) {
                String t = e.getKey();
                Integer cur = trainLoc.get(t);
                if (cur != null)
                    willVacate.add(cur);
            }

            for (String name : order) {
                if (plan.containsKey(name))
                    continue;
                Integer cur = trainLoc.get(name);
                if (cur == null)
                    continue;
                Train t = trains.get(name);
                if (cur == t.destination) {
                    plan.put(name, -1);
                    changed = true;
                    continue;
                }

                int next = nextHop(cur, t.path);
                if (next == -1)
                    continue;

                // Passenger priority lock: 3↔4 blocked if passengers on corridor
                if ((cur == 3 && next == 4) || (cur == 4 && next == 3)) {
                    if (occ.get(1) != null || occ.get(5) != null || occ.get(6) != null)
                        continue;
                }

                String occu = occ.get(next);
                boolean vacating = occu != null && moving.contains(occu) && plan.containsKey(occu);
                boolean free = (occu == null) || vacating || willVacate.contains(next);

                // Merge fairness at 5, 6, 10: only one entrant per tick, passenger priority
                boolean merge = next == 5 || next == 6 || next == 10;
                boolean reserved = plan.values().contains(next);
                if (merge && reserved) continue;

                if (free) {
                    plan.put(name, next);
                    changed = true;
                }
            }
        }

        int moved = 0;
        for (String name : order) {
            Integer next = plan.get(name);
            if (next == null)
                continue;
            Integer cur = trainLoc.get(name);
            if (cur == null)
                continue;
            if (next == -1) {
                occ.put(cur, null);
                trainLoc.remove(name);
            } else {
                occ.put(cur, null);
                occ.put(next, name);
                trainLoc.put(name, next);
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

    /* ---------- Helpers ---------- */

    private List<Integer> findPath(int start, int end) {
        Map<Integer, List<Integer>> g = graph();
        if (!g.containsKey(start))
            return Collections.emptyList();
        Queue<List<Integer>> q = new ArrayDeque<>();
        q.add(Collections.singletonList(start));
        Set<Integer> seen = new HashSet<>();
        seen.add(start);
        while (!q.isEmpty()) {
            List<Integer> p = q.poll();
            int last = p.get(p.size() - 1);
            if (last == end)
                return p;
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
        add(g, 1, 5);
        add(g, 2, 5);
        add(g, 5, 6);
        add(g, 6, 10);
        add(g, 10, 8);
        add(g, 10, 9);
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
}
