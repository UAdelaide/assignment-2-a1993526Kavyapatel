import java.util.List;

/**
 * Defines the operations for a railway interlocking system.
 * This interface provides the mechanism for trains to request entry into track sections
 * and notify the system when they leave, ensuring safe, collision-free, and deadlock-free
 * operation.
 */
public interface Interlocking {

    /**
     * Registers a new train with the interlocking system.
     * This method must be called for each train before it can request to enter any track section.
     *
     * @param trainId A unique identifier for the train.
     * @param isPassenger A boolean indicating if the train is a passenger train (true) or a freight train (false).
     * @param path A list of integer section IDs representing the train's intended route through the corridor.
     */
    void newTrain(int trainId, boolean isPassenger, List<Integer> path);

    /**
     * A train calls this method to request permission to enter a specific track section.
     * This method will block the calling thread until it is safe for the train to enter the
     * requested section. Safety is determined by section occupancy, junction states, and
     * priority rules.
     *
     * @param trainId The ID of the train making the request.
     * @param sectionId The ID of the section the train wishes to enter.
     * @return true when the train is granted permission to enter; this implementation always returns true upon successful entry.
     * @throws InterruptedException if the waiting thread is interrupted.
     */
    boolean waitToEnter(int trainId, int sectionId) throws InterruptedException;

    /**
     * A train calls this method to signal that it has completely left a track section.
     * This frees up the section and any associated junction resources, potentially allowing
     * other waiting trains to proceed.
     *
     * @param trainId The ID of the train leaving the section.
     * @param sectionId The ID of the section being vacated.
     */
    void leave(int trainId, int sectionId);
}