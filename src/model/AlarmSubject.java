package model;

/**
 * Subject interface of the Observer pattern (Lecture 2): objects register to be told when the subject changes.
 * The subject keeps a list of observers and notifies them; it does not know what the observers do.
 */
public interface AlarmSubject {

    /**
     * Subscribes an observer. Registering the same observer twice has no effect.
     *
     * @param o requires non-null
     */
    void registerObserver(AlarmObserver o);

    /**
     * Unsubscribes an observer. Has no effect if o is not registered.
     *
     * @param o requires non-null
     */
    void unregisterObserver(AlarmObserver o);
}
