package model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

/**
 * AlarmManager is the abstract data type (ADT) that the controller depends on (Lecture 8: Interfaces).
 * It states the contract only; it has no fields and no implementation. AlarmService is its implementation.
 *
 * <p>Abstract value: an ordered collection of Alarm objects with unique ids, plus the observers
 * that are notified when the collection changes.
 *
 * <p>Operations by type (Lecture 6): creators/producers: addAlarm, snooze; mutators: editAlarm, deleteAlarm,
 * setEnabled, checkAlarms; observers: getAlarms; observer-pattern operations: registerObserver and
 * unregisterObserver (inherited from AlarmSubject).
 *
 * <p>Null is never allowed in a parameter or a return value (Lecture 10).
 */
public interface AlarmManager extends AlarmSubject {

    /**
     * Creator of a new alarm inside the manager.
     *
     * @param time  the time to ring; requires non-null with no seconds
     * @param label requires non-blank and at most Alarm.MAX_LABEL_LENGTH characters
     * @param days  requires non-null; the days to repeat (empty means once only)
     * @return the new enabled alarm, with an id different from every other alarm's id
     * @throws IllegalArgumentException if a requirement on time or label is violated
     */
    Alarm addAlarm(LocalTime time, String label, Collection<DayOfWeek> days);

    /**
     * Replaces the time, label and days of an existing alarm (its id and on/off state are kept).
     *
     * @param id    requires the id of an alarm in this manager
     * @param time  same requirement as addAlarm
     * @param label same requirement as addAlarm
     * @param days  same requirement as addAlarm
     * @throws IllegalArgumentException if no alarm has this id, or time or label are invalid
     */
    void editAlarm(int id, LocalTime time, String label, Collection<DayOfWeek> days);

    /**
     * Removes an alarm.
     *
     * @param id requires the id of an alarm in this manager
     * @throws IllegalArgumentException if no alarm has this id
     */
    void deleteAlarm(int id);

    /**
     * Turns an alarm on or off.
     *
     * @param id requires the id of an alarm in this manager
     * @param on true to enable, false to disable
     * @throws IllegalArgumentException if no alarm has this id
     */
    void setEnabled(int id, boolean on);

    /**
     * Schedules a one-time "Snooze: ..." alarm for the first whole minute at least {@code minutes} minutes after now.
     *
     * @param rung    the alarm that rang; requires non-null
     * @param now     the current time; requires non-null
     * @param minutes requires minutes &gt; 0
     * @return the new snooze alarm
     * @throws IllegalArgumentException if minutes &lt;= 0
     */
    Alarm snooze(Alarm rung, LocalTime now, int minutes);

    /**
     * Observer operation.
     *
     * @return an unmodifiable snapshot of all alarms, in the order they were added; never null
     */
    List<Alarm> getAlarms();

    /**
     * Rings every enabled alarm that matches the given time and day, at most once per minute.
     * Effects: a once-only alarm that rings is switched off; observers are notified of the ringing
     * and of the change.
     *
     * @param now   the current time; requires non-null
     * @param today the current day of the week; requires non-null
     */
    void checkAlarms(LocalTime now, DayOfWeek today);
}
