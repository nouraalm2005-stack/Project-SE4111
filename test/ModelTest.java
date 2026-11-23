import model.*;
import java.time.*;
import java.util.*;

/** Simple tests without JUnit (so they run anywhere). Run: java -cp out ModelTest */
public class ModelTest {
    static int failed = 0;
    static void check(boolean ok, String name) { System.out.println((ok ? "PASS  " : "FAIL  ") + name); if (!ok) failed++; }

    public static void main(String[] a) {
        // Alarm: RI
        try { new Alarm(0, LocalTime.of(7, 0), "x", Set.of()); check(false, "id 0 rejected"); }
        catch (IllegalArgumentException e) { check(true, "id 0 rejected"); }
        try { new Alarm(1, LocalTime.of(7, 0, 30), "x", Set.of()); check(false, "seconds rejected"); }
        catch (IllegalArgumentException e) { check(true, "seconds rejected"); }
        try { new Alarm(1, LocalTime.of(7, 0), "  ", Set.of()); check(false, "blank label rejected"); }
        catch (IllegalArgumentException e) { check(true, "blank label rejected"); }

        // Alarm: immutability + equality
        Alarm x = new Alarm(1, LocalTime.of(7, 0), "Wake", Set.of(DayOfWeek.MONDAY));
        Alarm y = new Alarm(1, LocalTime.of(7, 0), "Wake", EnumSet.of(DayOfWeek.MONDAY));
        check(x.equals(y) && x.hashCode() == y.hashCode(), "equal alarms have equal hashCode");
        Alarm z = x.withEnabled(false);
        check(x.isEnabled() && !z.isEnabled() && !x.equals(z), "producer returns a new alarm, old unchanged");
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.FRIDAY);
        Alarm w = new Alarm(2, LocalTime.of(8, 0), "W", days);
        days.add(DayOfWeek.SUNDAY);
        check(w.getRepeatDays().size() == 1, "defensive copy of repeatDays");
        try { w.getRepeatDays().add(DayOfWeek.SUNDAY); check(false, "repeatDays unmodifiable"); }
        catch (UnsupportedOperationException e) { check(true, "repeatDays unmodifiable"); }
        // Object contract (Lecture 9)
        Alarm x2 = new Alarm(1, LocalTime.of(7, 0), "Wake", Set.of(DayOfWeek.MONDAY));
        Alarm x3 = new Alarm(1, LocalTime.of(7, 0), "Wake", Set.of(DayOfWeek.MONDAY));
        check(x.equals(x), "equals reflexive");
        check(x2.equals(x3) && x3.equals(x2), "equals symmetric");
        check(x.equals(x2) && x2.equals(x3) && x.equals(x3), "equals transitive");
        check(!x.equals(null), "equals(null) is false");
        check(!x.equals("Wake"), "equals(other type) is false");
        check(new java.util.HashSet<>(java.util.List.of(x, x2)).size() == 1, "works in a HashSet");
        check(x.shouldRingAt(LocalTime.of(7, 0, 15), DayOfWeek.MONDAY), "rings on repeat day");
        check(!x.shouldRingAt(LocalTime.of(7, 0), DayOfWeek.TUESDAY), "silent on other day");
        check(!z.shouldRingAt(LocalTime.of(7, 0), DayOfWeek.MONDAY), "silent when disabled");

        // AlarmService: singleton + observer
        AlarmService s1 = AlarmService.getInstance();
        check(s1 == AlarmService.getInstance(), "singleton: same instance");
        final int[] changed = {0}; final List<Alarm> rang = new ArrayList<>();
        AlarmObserver obs = new AlarmObserver() {
            public void onAlarmsChanged(List<Alarm> l) { changed[0]++; }
            public void onAlarmRinging(Alarm al) { rang.add(al); }
        };
        s1.registerObserver(obs);
        Alarm once = s1.addAlarm(LocalTime.of(6, 30), "Once", Set.of());
        check(changed[0] == 1 && s1.getAlarms().size() == 1, "add notifies observer");
        s1.editAlarm(once.getId(), LocalTime.of(6, 45), "Once2", Set.of(DayOfWeek.MONDAY));
        check(s1.getAlarms().get(0).getTime().equals(LocalTime.of(6, 45)), "edit works");
        s1.setEnabled(once.getId(), false);
        check(!s1.getAlarms().get(0).isEnabled(), "disable works");
        s1.setEnabled(once.getId(), true);

        // ringing: once per minute
        for (int sec = 0; sec < 60; sec += 10) s1.checkAlarms(LocalTime.of(6, 45, sec), DayOfWeek.MONDAY);
        check(rang.size() == 1, "alarm rings only once in its minute");
        s1.checkAlarms(LocalTime.of(6, 45), DayOfWeek.TUESDAY);
        check(rang.size() == 1, "not rung on other day");

        Alarm onceOnly = s1.addAlarm(LocalTime.of(9, 0), "Single", Set.of());
        s1.checkAlarms(LocalTime.of(9, 0), DayOfWeek.WEDNESDAY);
        check(rang.size() == 2 && !s1.getAlarms().get(1).isEnabled(), "once-only alarm switches itself off");

        Alarm sn = s1.snooze(onceOnly, LocalTime.of(9, 0, 20), 5);
        check(sn.getTime().equals(LocalTime.of(9, 6)) && sn.getRepeatDays().isEmpty(),
                "snooze rounds up to at least five full minutes");
        check(s1.snooze(onceOnly, LocalTime.of(9, 0), 5).getTime().equals(LocalTime.of(9, 5)),
                "snooze at exact minute stays at +5 minutes");
        check(s1.snooze(onceOnly, LocalTime.of(23, 57, 55), 5).getTime().equals(LocalTime.of(0, 3)),
                "snooze rounds up correctly across midnight");

        s1.deleteAlarm(once.getId());
        check(s1.getAlarms().stream().noneMatch(al -> al.getId() == once.getId()), "delete works");
        // Regression: editing an already-rung recurring alarm must not ring it twice.
        Alarm repeated = s1.addAlarm(LocalTime.of(12, 0), "Before edit", Set.of(DayOfWeek.FRIDAY));
        int ringCountBefore = rang.size();
        s1.checkAlarms(LocalTime.of(12, 0, 1), DayOfWeek.FRIDAY);
        s1.editAlarm(repeated.getId(), LocalTime.of(12, 0), "After edit", Set.of(DayOfWeek.FRIDAY));
        s1.checkAlarms(LocalTime.of(12, 0, 2), DayOfWeek.FRIDAY);
        check(rang.size() == ringCountBefore + 1, "editing after ringing does not ring twice in one minute");
        s1.deleteAlarm(repeated.getId());

        s1.unregisterObserver(obs);
        int before = changed[0];
        s1.deleteAlarm(onceOnly.getId());
        check(changed[0] == before, "unregistered observer is not notified");

        System.out.println(failed == 0 ? "\nALL TESTS PASSED" : "\n" + failed + " TEST(S) FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
