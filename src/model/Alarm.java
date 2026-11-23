package model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Alarm is an IMMUTABLE abstract data type (ADT). It represents one alarm of the alarm clock.
 * Editing an alarm means producing a new Alarm with a producer (withXxx); no operation mutates this object.
 *
 * <p>Operations by type (Lecture 6): creator: the constructor; producers: withTime, withLabel,
 * withRepeatDays, withEnabled; observers: getId, getTime, getLabel, getRepeatDays, isEnabled, shouldRingAt;
 * mutators: none, because the type is immutable (Lecture 5). Only immutable java.time types are used for time,
 * and the repeat days are exposed through Collections.unmodifiableSet.
 *
 * <p>Immutable type (Lecture 9): equals() compares abstract values and hashCode() is consistent with it.
 * Null is never allowed in a parameter or a return value (Lecture 10).
 */
public final class Alarm {

    public static final int MAX_LABEL_LENGTH = 30;

    private final int id;
    private final LocalTime time;            // hour and minute only
    private final String label;
    private final Set<DayOfWeek> repeatDays; // empty = once only
    private final boolean enabled;

    // RI (representation invariant):
    //   id > 0
    //   time != null and time.getSecond() == 0 and time.getNano() == 0
    //   label != null, not blank, label.length() <= MAX_LABEL_LENGTH
    //   repeatDays != null and contains no null (it is built from an EnumSet)
    // AF (abstraction function): AF(id, time, label, repeatDays, enabled) =
    //   an alarm named label, identified by id, that rings at time.
    //   If repeatDays is empty it rings only once (the next time the clock reaches time),
    //   otherwise it rings at time on every day in repeatDays.
    //   It can ring only if enabled is true; if false it is stored but silent.
    // Rep exposure safety:
    //   All fields are private final. int, boolean, String and LocalTime are immutable,
    //   so returning them from getters leaks nothing.
    //   repeatDays is the only mutable-looking part: the constructor makes a defensive copy of the
    //   parameter (so the caller keeps no alias), and getRepeatDays() returns an unmodifiable view.
    //   Every producer returns a new Alarm instead of changing this one.

    // ---------------- creator ----------------

    /**
     * Creator: makes an enabled alarm.
     *
     * @param id         requires id &gt; 0 (uniqueness is guaranteed by AlarmManager)
     * @param time       requires non-null, hour and minute only (no seconds)
     * @param label      requires non-blank and at most MAX_LABEL_LENGTH characters
     * @param repeatDays days to repeat; empty means once only; the collection is copied
     * @return (constructor) a new enabled Alarm with these values
     * @throws IllegalArgumentException if a requirement on id, time or label is violated
     */
    public Alarm(int id, LocalTime time, String label, Collection<DayOfWeek> repeatDays) {
        this(id, time, label, repeatDays, true);
    }

    private Alarm(int id, LocalTime time, String label, Collection<DayOfWeek> repeatDays, boolean enabled) {
        this.id = id;
        this.time = time;
        this.label = label;
        EnumSet<DayOfWeek> copy = EnumSet.noneOf(DayOfWeek.class);
        if (repeatDays != null) copy.addAll(repeatDays);   // defensive copy
        this.repeatDays = Collections.unmodifiableSet(copy);
        this.enabled = enabled;
        checkRep();
    }

    /** Checks the RI; called at the end of every constructor, so creators and producers establish it. */
    private void checkRep() {
        if (repeatDays == null) throw new IllegalStateException("repeatDays must not be null");
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        if (time == null || time.getSecond() != 0 || time.getNano() != 0)
            throw new IllegalArgumentException("time must be non-null with no seconds");
        if (label == null || label.isBlank() || label.length() > MAX_LABEL_LENGTH)
            throw new IllegalArgumentException("label must be 1-" + MAX_LABEL_LENGTH + " characters");
    }

    // ---------------- producers ----------------

    /** Producer. @param newTime same requirement as the constructor. @return a copy with the new time. */
    public Alarm withTime(LocalTime newTime)               { return new Alarm(id, newTime, label, repeatDays, enabled); }
    /** Producer. @param newLabel same requirement as the constructor. @return a copy with the new label. */
    public Alarm withLabel(String newLabel)                { return new Alarm(id, time, newLabel, repeatDays, enabled); }
    /** Producer. @param d the new repeat days (copied). @return a copy with the new repeat days. */
    public Alarm withRepeatDays(Collection<DayOfWeek> d)   { return new Alarm(id, time, label, d, enabled); }
    /** Producer. @param on the new on/off state. @return a copy with that state. */
    public Alarm withEnabled(boolean on)                   { return new Alarm(id, time, label, repeatDays, on); }

    // ---------------- observers ----------------

    public int getId()                    { return id; }
    public LocalTime getTime()            { return time; }
    public String getLabel()              { return label; }
    public Set<DayOfWeek> getRepeatDays() { return repeatDays; }   // already unmodifiable
    public boolean isEnabled()            { return enabled; }

    /**
     * Observer.
     *
     * @param now   requires non-null; seconds are ignored
     * @param today requires non-null
     * @return true if and only if this alarm is enabled, its time equals now (to the minute),
     *         and repeatDays is empty or contains today
     */
    public boolean shouldRingAt(LocalTime now, DayOfWeek today) {
        if (!enabled) return false;
        if (!time.equals(now.withSecond(0).withNano(0))) return false;
        return repeatDays.isEmpty() || repeatDays.contains(today);
    }

    // ---------------- equality (abstract value) ----------------
    // equals is an equivalence relation (reflexive, symmetric, transitive), consistent, and false for null.
    // Two alarms are equal iff AF gives the same abstract value, so equal alarms have equal hash codes.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alarm)) return false;
        Alarm a = (Alarm) o;
        return id == a.id && enabled == a.enabled
                && time.equals(a.time) && label.equals(a.label)
                && repeatDays.equals(a.repeatDays);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, time, label, repeatDays, enabled);
    }

    @Override
    public String toString() {
        return label + " " + time + " " + (repeatDays.isEmpty() ? "once" : repeatDays) + (enabled ? " ON" : " OFF");
    }
}
