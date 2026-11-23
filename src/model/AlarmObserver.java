package model;

import java.util.List;

/**
 * Observer interface (Observer pattern, Lecture 8): anything that wants to hear about changes in the alarms.
 * AlarmView is one implementation; others can be added without changing AlarmService.
 */
public interface AlarmObserver {

    /**
     * Called whenever the list of alarms changes (add, edit, delete, on/off, once-alarm switched off).
     *
     * @param alarms an unmodifiable snapshot of all alarms; never null
     */
    void onAlarmsChanged(List<Alarm> alarms);

    /**
     * Called when an enabled alarm's time arrives.
     *
     * @param alarm the alarm that is ringing; never null
     */
    void onAlarmRinging(Alarm alarm);
}
