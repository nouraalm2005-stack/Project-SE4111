package model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AlarmService implements the AlarmManager interface; it is the heart of the model.
 *  - SINGLETON: only one instance, so the whole app works on one list of alarms.
 *  - SUBJECT (Observer pattern): notifies registered AlarmObservers when something changes.
 * It has no Swing code at all.
 *
 * Mutable type: following Lecture 9, it does not override equals() or hashCode(),
 * so equality is reference equality (==).
 */
public class AlarmService implements AlarmManager {

    // ----- Singleton -----
    private static AlarmService instance;               // step 1: private static instance

    private AlarmService() { }                          // step 2: private constructor

    public static AlarmService getInstance() {          // step 3 + 4: public accessor, lazy creation
        if (instance == null) {
            instance = new AlarmService();
        }
        return instance;
    }

    // ----- state -----
    private final Map<Integer, Alarm> alarms = new LinkedHashMap<>();
    private final List<AlarmObserver> observers = new ArrayList<>();
    private final Set<Integer> alreadyRung = new HashSet<>(); // track alarm IDs so edits cannot cause a second ring
    private LocalTime lastMinute;
    private int nextId = 1;                                   // guarantees unique ids

    // RI:
    //   alarms != null and observers != null and alreadyRung != null, none containing null
    //   for every entry (k, a) in alarms: a.getId() == k
    //   nextId > every key in alarms (so a new id is always unique)
    // AF(alarms, observers, ...) =
    //   the ordered collection of the values of alarms (in insertion order),
    //   watched by the observers.
    // Rep exposure safety:
    //   All fields are private and final (except two primitives/immutable values).
    //   getAlarms() returns an unmodifiable copy, and Alarm is immutable,
    //   so no client can reach or change the rep.

    // ----- Subject (Observer pattern) -----
    @Override
    public void registerObserver(AlarmObserver o) {
        if (!observers.contains(o)) observers.add(o);
    }

    @Override
    public void unregisterObserver(AlarmObserver o) {
        observers.remove(o);
    }

    private void notifyChanged() {
        List<Alarm> snapshot = getAlarms();
        for (AlarmObserver o : new ArrayList<>(observers)) o.onAlarmsChanged(snapshot);
    }

    private void notifyRinging(Alarm a) {
        for (AlarmObserver o : new ArrayList<>(observers)) o.onAlarmRinging(a);
    }

    // ----- requirements 1-6: manage alarms -----
    @Override
    public Alarm addAlarm(LocalTime time, String label, Collection<DayOfWeek> days) {
        Alarm a = new Alarm(nextId++, time, label, days);
        alarms.put(a.getId(), a);
        checkRep();
        notifyChanged();
        return a;
    }

    @Override
    public void editAlarm(int id, LocalTime time, String label, Collection<DayOfWeek> days) {
        Alarm old = require(id);
        alarms.put(id, old.withTime(time).withLabel(label).withRepeatDays(days));
        checkRep();
        notifyChanged();
    }

    @Override
    public void deleteAlarm(int id) {
        require(id);
        alarms.remove(id);
        checkRep();
        notifyChanged();
    }

    @Override
    public void setEnabled(int id, boolean on) {
        alarms.put(id, require(id).withEnabled(on));
        checkRep();
        notifyChanged();
    }

    /** Snooze: schedules a one-time alarm at the first whole minute at least 'minutes' minutes later. */
    @Override
    public Alarm snooze(Alarm rung, LocalTime now, int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("snooze minutes must be positive");
        LocalTime t = now.plusMinutes(minutes);
        if (t.getSecond() != 0 || t.getNano() != 0) {
            t = t.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        }
        String label = rung.getLabel().startsWith("Snooze: ") ? rung.getLabel() : "Snooze: " + rung.getLabel();
        if (label.length() > Alarm.MAX_LABEL_LENGTH) label = label.substring(0, Alarm.MAX_LABEL_LENGTH);
        return addAlarm(t, label, Collections.emptySet());
    }

    /** Returns a copy of all alarms (Alarm is immutable, so sharing the elements is safe). */
    @Override
    public List<Alarm> getAlarms() {
        return Collections.unmodifiableList(new ArrayList<>(alarms.values()));
    }

    // ----- requirement 9: ringing -----
    /** Called every second by the controller. Rings each matching alarm once per minute. */
    @Override
    public void checkAlarms(LocalTime now, DayOfWeek today) {
        LocalTime minute = now.truncatedTo(ChronoUnit.MINUTES);
        if (!minute.equals(lastMinute)) {
            alreadyRung.clear();
            lastMinute = minute;
        }
        List<Alarm> toRing = new ArrayList<>();
        for (Alarm a : alarms.values()) {
            if (a.shouldRingAt(now, today) && alreadyRung.add(a.getId())) toRing.add(a);
        }
        for (Alarm a : toRing) {
            if (a.getRepeatDays().isEmpty()) {          // once-only alarms switch themselves off
                alarms.put(a.getId(), a.withEnabled(false));
            }
            notifyRinging(a);
        }
        if (!toRing.isEmpty()) notifyChanged();
        checkRep();
    }

    private void checkRep() {
        for (Map.Entry<Integer, Alarm> e : alarms.entrySet()) {
            if (e.getValue() == null || e.getValue().getId() != e.getKey())
                throw new IllegalStateException("RI violated: alarm stored under the wrong id");
            if (e.getKey() >= nextId) throw new IllegalStateException("RI violated: nextId is not fresh");
        }
    }

    private Alarm require(int id) {
        Alarm a = alarms.get(id);
        if (a == null) throw new IllegalArgumentException("no alarm with id " + id);
        return a;
    }
}
