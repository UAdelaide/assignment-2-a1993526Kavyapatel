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
    public void addTrain(String name, int entry, int dest)
            throws IllegalArgumentException, IllegalStateException {
        if (name == null) throw new IllegalArgumentException("Null name");
        if (trains.containsKey(name))
            throw new IllegalArgumentException("Train " + name + " exists");
        if (!validSections.contains(entry) || !validSections.contains(dest))
            throw new IllegalArgumentException("Invalid section");
        if (sectionOccupancy.get(entry) != null)
            throw new IllegalStateException("Section " + entry + " occupied");

        List<Integer> path = findPath(entry, dest);
        if (path.isEmpty())
            throw new IllegalArgumentException("No valid path " + entry + "→" + dest);
        trains.put(name, new Train(name, dest, path));
        trainLocations.put(name, entry);
        sectionOccupancy.put(entry, name);
    }

    @Override
    public int moveTrains(String... names) throws IllegalArgumentException {
        if (names == null) names = new String[0];
        Set<String> set = new LinkedHashSet<>(Arrays.asList(names));
        for (String n : set)
            if (!trains.containsKey(n)) throw new IllegalArgumentException("Unknown " + n);

        Comparator<String> prio = Comparator
                .comparing((String n) -> isFreight(n))
                .thenComparing(n -> n);

        List<String> ordered = set.stream()
                .filter(trainLocations::containsKey)
                .sorted(prio)
                .collect(Collectors.toList());

        class Req {
            String t; int src, tgt;
            Req(String t, int s, int g){this.t=t; this.src=s; this.tgt=g;}
        }

        Map<String, Req> reqs = new HashMap<>();
        for (String n : ordered) {
            int src = trainLocations.get(n);
            Train tr = trains.get(n);
            if (src == tr.destination) reqs.put(n, new Req(n, src, -1));
            else {
                int nxt = next(n);
                if (nxt != -1) reqs.put(n, new Req(n, src, nxt));
            }
        }

        Map<String,Integer> moves = new HashMap<>();
        Set<Integer> taken = new HashSet<>();
        int size=-1;
        while(moves.size()>size){
            size=moves.size();
            Map<Integer,List<Req>> groups=new HashMap<>();
            List<Req> exits=new ArrayList<>();

            for(Req r:reqs.values()){
                if(moves.containsKey(r.t)) continue;
                if(r.tgt==-1){exits.add(r);continue;}
                if(isFreightCross(r.src,r.tgt) &&
                   (sectionOccupancy.get(1)!=null||
                    sectionOccupancy.get(5)!=null||
                    sectionOccupancy.get(6)!=null)) continue;
                groups.computeIfAbsent(r.tgt,k->new ArrayList<>()).add(r);
            }

            for(Req r:exits) moves.put(r.t,-1);

            for(var e:groups.entrySet()){
                int tgt=e.getKey();
                if(taken.contains(tgt)) continue;
                String occ=sectionOccupancy.get(tgt);
                boolean free=(occ==null)||(moves.containsKey(occ));
                if(!free) continue;

                Comparator<Req> cmp=Comparator
                        .comparing((Req r)->isFreight(r.t))
                        .thenComparing((Req r)->junctionRank(tgt,r.src))
                        .thenComparing(r->r.t);
                Req win=e.getValue().stream().min(cmp).orElse(null);
                if(win!=null){moves.put(win.t,tgt);taken.add(tgt);}
            }
        }

        // ✅ Second pass: trains waiting can fill newly freed sections same tick
        boolean changed=true;
        while(changed){
            changed=false;
            for(Req r:reqs.values()){
                if(moves.containsKey(r.t)) continue;
                if(r.tgt==-1) continue;
                String occ=sectionOccupancy.get(r.tgt);
                boolean free=(occ==null)||(moves.containsKey(occ));
                if(free && !taken.contains(r.tgt)){
                    moves.put(r.t,r.tgt); taken.add(r.tgt); changed=true;
                }
            }
        }

        int moved=0;
        for(String n:ordered){
            if(!moves.containsKey(n)) continue;
            int np=moves.get(n);
            int op=trainLocations.get(n);
            if(np==-1){
                sectionOccupancy.put(op,null);
                trainLocations.remove(n);
            }else{
                sectionOccupancy.put(op,null);
                sectionOccupancy.put(np,n);
                trainLocations.put(n,np);
            }
            moved++;
        }
        return moved;
    }

    @Override
    public String getSection(int s){
        if(!validSections.contains(s)) throw new IllegalArgumentException();
        return sectionOccupancy.get(s);
    }

    @Override
    public int getTrain(String n){
        if(!trains.containsKey(n)) throw new IllegalArgumentException();
        return trainLocations.getOrDefault(n,-1);
    }

    // ---------- Helpers ----------
    private List<Integer> findPath(int s,int e){
        Map<Integer,List<Integer>> g=graph();
        Queue<List<Integer>> q=new ArrayDeque<>();
        q.add(List.of(s));
        Set<Integer> seen=new HashSet<>();
        seen.add(s);
        while(!q.isEmpty()){
            List<Integer> p=q.poll();
            int u=p.get(p.size()-1);
            if(u==e) return p;
            for(int v:g.getOrDefault(u,List.of()))
                if(seen.add(v)){
                    List<Integer> np=new ArrayList<>(p);
                    np.add(v); q.add(np);
                }
        }
        return List.of();
    }

    private Map<Integer,List<Integer>> graph(){
        Map<Integer,List<Integer>> g=new HashMap<>();
        g.put(1,new ArrayList<>(List.of(5)));
        g.put(2,new ArrayList<>(List.of(5)));
        g.put(5,new ArrayList<>(List.of(1,2,6)));
        g.put(6,new ArrayList<>(List.of(5,10)));
        g.put(10,new ArrayList<>(List.of(6,8,9)));
        g.put(8,new ArrayList<>(List.of(10)));
        g.put(9,new ArrayList<>(List.of(10)));
        g.put(3,new ArrayList<>(List.of(4,7)));
        g.put(4,new ArrayList<>(List.of(3)));
        g.put(7,new ArrayList<>(List.of(3,11)));
        g.put(11,new ArrayList<>(List.of(7)));
        return g;
    }

    private int next(String n){
        Train t=trains.get(n);
        int cur=trainLocations.get(n);
        int i=t.path.indexOf(cur);
        if(i>=0 && i<t.path.size()-1) return t.path.get(i+1);
        return -1;
    }

    private boolean isPassenger(String n){
        Train t=trains.get(n);
        int st=t.path.get(0);
        return List.of(1,2,5,6,8,9,10).contains(st);
    }
    private boolean isFreight(String n){return !isPassenger(n);}
    private boolean isFreightCross(int s,int t){return (s==3&&t==4)||(s==4&&t==3);}

    // ✅ Adjusted junction priorities
    private int junctionRank(int tgt,int src){
        switch(tgt){
            case 5: // prefer 6 > 2 > 1
                if(src==6)return 0;
                if(src==2)return 1;
                if(src==1)return 2;
                return 3;
            case 6: // prefer 5 > 10
                if(src==5)return 0;
                if(src==10)return 1;
                return 2;
            case 10: // prefer 6 > 8 > 9
                if(src==6)return 0;
                if(src==8)return 1;
                if(src==9)return 2;
                return 3;
            default:return 0;
        }
    }
}
