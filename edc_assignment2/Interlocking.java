/**
 * Defines the public interface for a railway interlocking system.
 * This system manages the movement of trains through a corridor of track sections,
 * ensuring safety by preventing collisions and deadlocks.
 */
public interface Interlocking {

    /**
     * Adds a train to the rail corridor.
     *
     * @param trainName               A String that identifies a given train. Cannot be the
     * same as any other train present.
     * @param entryTrackSection       The id number of the track section that the train
     * is entering into.
     * @param destinationTrackSection The id number of the track section that the
     * train should exit from.
     * @throws IllegalArgumentException if the train name is already in use, the track
     * section does not exist, or there is no valid
     * path from the entry to the destination.
     * @throws IllegalStateException    if the entry track is already occupied.
     */
    void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException;

    /**
     * Moves the specified trains to their next track section according to their
     * pre-determined paths.
     * <p>
     * Trains only move if their next section is clear and not reserved, and all
     * safety conditions (e.g., junction priorities) are met. When a train
     * reaches its destination, it will exit the corridor on the next move attempt.
     *
     * @param trainNames The names of the trains to move.
     * @return The number of trains that have successfully moved one section.
     * @throws IllegalArgumentException if a train name does not exist or is no longer
     * in the rail corridor.
     */
    int moveTrains(String... trainNames) throws IllegalArgumentException;

    /**
     * Returns the name of the Train currently occupying a given track section.
     *
     * @param trackSection The id number of the section of track.
     * @return The name of the train currently in that section, or null if the
     * section is empty/unoccupied.
     * @throws IllegalArgumentException if the track section does not exist.
     */
    String getSection(int trackSection) throws IllegalArgumentException;

    /**
     * Returns the track section that a given train is occupying.
     *
     * @param trainName The name of the train.
     * @return The id number of the section of track the train is occupying, or -1
     * if the train is no longer in the rail corridor.
     * @throws IllegalArgumentException if the train name does not exist.
     */
    int getTrain(String trainName) throws IllegalArgumentException;
}