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
        // Validate
        final Set<String> moveSet = new HashSet<>(Arrays.asList(trainNames));
        for (String n : moveSet) {
            if (!trains.containsKey(n)) throw new IllegalArgumentException("Unknown train: " + n);
        }

        // Deterministic priority: passenger first, then name
        Comparator<String> prio = Comparator
                .comparing((String n) -> isFreightTrain(n))     // passenger(false) before freight(true)
                .thenComparing(n -> n);

        final List<String> ordered = moveSet.stream()
                .filter(trainLocations::containsKey)            // still on map
                .sorted(prio)
                .collect(Collectors.toList());

        // Pre-compute passenger activity on main corridor for this tick
        final Set<Integer> mainCorridor = new HashSet<>(Arrays.asList(1, 2, 5, 6));
        boolean passengerUsingCorridor = false;
        for (String n : ordered) {
            if (!isPassengerTrain(n)) continue;
            int cur = trainLocations.get(n);
            int nxt = getNextSectionForTrain(n);
            if (mainCorridor.contains(cur) || mainCorridor.contains(nxt)) {
                passengerUsingCorridor = true;
                break;
            }
        }

        // Plan moves
        Map<String, Integer> plan = new HashMap<>();

        // First pass: propose moves (with rules)
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

            // Freight special crossing (3 <-> 4) and (3 <-> 7)
            boolean isFreightCross =
                    (cur == 3 && (next == 4 || next == 7)) ||
                    (cur == 4 && next == 3) ||
                    (cur == 7 && next == 3);

            if (isFreightCross) {
                // Block only if passenger actively uses main corridor this tick
                if (passengerUsingCorridor) {
                    continue;
                }
                // Avoid head-on with opposite freight moving into us
                if (occ != null && moveSet.contains(occ)) {
                    int occNext = getNextSectionForTrain(occ);
                    if (occNext == cur) {
                        // resolve by priority
                        if (prio.compare(n, occ) > 0) continue; // we lose; wait
                    }
                }
            }

            // Merge arbitration at sections 5 and 10:
            // If multiple trains aim for same target, keep highest priority (passenger > freight > name).
            if (next == 5 || next == 10) {
                // If someone else already planned to take it, compare priorities
                String currentWinner = null;
                for (Map.Entry<String, Integer> e : plan.entrySet()) {
                    if (e.getValue() != null && e.getValue() == next) {
                        currentWinner = e.getKey();
                        break;
                    }
                }
                if (currentWinner != null) {
                    // compare this train vs winner
                    if (prio.compare(n, currentWinner) < 0) {
                        // we win, replace winner
                        plan.remove(currentWinner);
                        plan.put(n, next);
                    } else {
                        // we lose, skip planning
                        continue;
                    }
                } else {
                    // no winner yet, propose us for this target
                    plan.put(n, next);
                    continue;
                }
            } else {
                // Standard availability / chain reaction allowance
                boolean willFree = (occ != null && moveSet.contains(occ));
                if (willFree) {
                    // occupant plans to move elsewhere? (not into same 'next')
                    Integer occPlan = plan.get(occ);
                    if (occPlan == null) {
                        // occupant hasn't planned yet; can't assume it moves
                        continue;
                    }
                    if (occPlan == next) {
                        // occupant also claims same target (shouldn’t normally happen here), break tie by priority
                        if (prio.compare(n, occ) > 0) continue; // we lose
                    }
                } else if (occ != null) {
                    // occupied by a stationary/non-moving train -> blocked
                    continue;
                }

                // Also prevent head-on swaps even if target appears free via plan
                if (occ != null && moveSet.contains(occ)) {
                    int occNext = plan.getOrDefault(occ, getNextSectionForTrain(occ));
                    if (occNext == cur) {
                        if (prio.compare(n, occ) > 0) continue; // we lose
                    }
                }

                plan.put(n, next);
            }
        }

        // Chain reaction pass: if after initial winners move out, we can fill behind them
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String n : ordered) {
                if (plan.containsKey(n)) continue; // already planned
                int cur = trainLocations.get(n);
                Train t = trains.get(n);
                if (cur == t.destination) {
                    plan.put(n, -1);
                    changed = true;
                    continue;
                }
                int next = getNextSectionForTrain(n);
                if (next == -1) continue;

                // If target is currently occupied but that occupant plans to leave to SOMEWHERE ELSE,
                // we may take it.
                String occ = sectionOccupancy.get(next);
                if (occ != null) {
                    Integer occPlan = plan.get(occ);
                    if (occPlan != null && occPlan != next) {
                        // prevent head-on swap
                        if (occPlan == cur) {
                            // tie-break by priority
                            Comparator<String> prio = Comparator
                                    .comparing((String x) -> isFreightTrain(x))
                                    .thenComparing(x -> x);
                            if (prio.compare(n, occ) > 0) {
                                continue; // we lose
                            } else {
                                // we win; evict occ plan
                                plan.remove(occ);
                            }
                        }
                        plan.put(n, next);
                        changed = true;
                    }
                } else {
                    // target empty but maybe already claimed by someone else of higher priority
                    boolean alreadyClaimed = false;
                    for (Map.Entry<String, Integer> e : plan.entrySet()) {
                        if (Objects.equals(e.getValue(), next)) {
                            alreadyClaimed = true;
                            // tie-break
                            String other = e.getKey();
                            Comparator<String> pr = Comparator
                                    .comparing((String x) -> isFreightTrain(x))
                                    .thenComparing(x -> x);
                            if (pr.compare(n, other) < 0) {
                                // replace
                                plan.remove(other);
                                plan.put(n, next);
                                changed = true;
                            }
                            break;
                    }
                    }
                    if (!alreadyClaimed) {
                        plan.put(n, next);
                        changed = true;
                    }
                }
            }
        }

        // Normalize: at most one train per target (except -1)
        Map<Integer, String> targetWinner = new HashMap<>();
        for (String n : ordered) {
            if (!plan.containsKey(n)) continue;
            int target = plan.get(n);
            if (target == -1) continue;
            String prev = targetWinner.get(target);
            if (prev == null) {
                targetWinner.put(target, n);
            } else {
                // choose winner by priority
                String win = (Comparator
                        .comparing((String x) -> isFreightTrain(x))
                        .thenComparing(x -> x)
                        .compare(n, prev) < 0) ? n : prev;
                targetWinner.put(target, win);
            }
        }
        // Remove losers for shared targets
        for (Map.Entry<String, Integer> e : new ArrayList<>(plan.entrySet())) {
            int target = e.getValue();
            if (target != -1 && !e.getKey().equals(targetWinner.get(target))) {
                plan.remove(e.getKey());
            }
        }

        // Execute
        int moved = 0;
        for (String n : ordered) {
            if (!plan.containsKey(n)) continue;
            int cur = trainLocations.get(n);
            int target = plan.get(n);
            sectionOccupancy.put(cur, null);
            if (target == -1) {
                trainLocations.remove(n);
            } else {
                sectionOccupancy.put(target, n);
                trainLocations.put(n, target);
            }
            moved++;
        }

        return moved;
    }

    @Override
    public String getSection(int trackSection) {
        if (!validSections.contains(trackSection)) {
            throw new IllegalArgumentException("Invalid section");
        }
        return sectionOccupancy.get(trackSection);
    }

    @Override
    public int getTrain(String trainName) {
        if (!trains.containsKey(trainName)) {
            throw new IllegalArgumentException("Unknown train: " + trainName);
        }
        return trainLocations.getOrDefault(trainName, -1);
    }

    // ===== Helpers =====

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
                if (!seen.contains(nb)) {
                    seen.add(nb);
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
        // Passenger line (bidirectional for pathfinding)
        g.computeIfAbsent(1, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(2, k -> new ArrayList<>()).add(5);
        g.computeIfAbsent(5, k -> new ArrayList<>()).addAll(Arrays.asList(1, 2, 6));
        g.computeIfAbsent(6, k -> new ArrayList<>()).addAll(Arrays.asList(5, 10));
        g.computeIfAbsent(10, k -> new ArrayList<>()).addAll(Arrays.asList(6, 8, 9));
        g.computeIfAbsent(8, k -> new ArrayList<>()).add(10);
        g.computeIfAbsent(9, k -> new ArrayList<>()).add(10);
        // Freight line (bidirectional for pathfinding)
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
        if (idx != -1 && idx < tr.path.size() - 1) {
            return tr.path.get(idx + 1);
        }
        return -1;
    }

    private boolean isPassengerTrain(String trainName) {
        Train tr = trains.get(trainName);
        if (tr == null || tr.path.isEmpty()) return false;
        int first = tr.path.get(0);
        return Arrays.asList(1, 2, 5, 6, 8, 9, 10).contains(first);
    }

    private boolean isFreightTrain(String trainName) {
        Train tr = trains.get(trainName);
        if (tr == null || tr.path.isEmpty()) return false;
        int first = tr.path.get(0);
        return Arrays.asList(3, 4, 7, 11).contains(first);
    }
}
