import java.util.List;

public interface Interlocking {
    void newTrain(int trainId, boolean isPassenger, List<Integer> path);
    boolean waitToEnter(int trainId, int sectionId) throws InterruptedException;
    void leave(int trainId, int sectionId);
}