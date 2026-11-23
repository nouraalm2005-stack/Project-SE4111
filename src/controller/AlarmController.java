package controller;

import model.Alarm;
import model.AlarmManager;
import view.AlarmView;

import javax.swing.Timer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The Controller: handles the user's actions, validates the input, and calls the model.
 * It also owns the one-second timer that updates the clock and checks the alarms.
 */
public class AlarmController {

    private static final int SNOOZE_MINUTES = 5;

    private final AlarmManager service;
    private final AlarmView view;
    private final Timer timer;

    public AlarmController(AlarmManager service, AlarmView view) {
        this.service = service;
        this.view = view;

        service.registerObserver(view);                 // Observer pattern: view subscribes to the model

        view.setAddAction(this::onAddClicked);
        view.setEditAction(this::onEditClicked);
        view.setDeleteAction(this::onDeleteClicked);
        view.setToggleAction(this::onToggleClicked);
        view.setSnoozeHandler(this::onSnoozeClicked);

        this.timer = new Timer(1000, e -> onTick());
    }

    public void start() {
        view.onAlarmsChanged(service.getAlarms());
        onTick();
        timer.start();
        view.setVisible(true);
    }

    // ---------- requirement 7 + 9: every second ----------
    private void onTick() {
        LocalTime now = LocalTime.now();
        view.showTime(now);
        service.checkAlarms(now, LocalDate.now().getDayOfWeek());
    }

    // ---------- requirements 1, 2: add ----------
    private void onAddClicked() {
        AlarmView.AlarmInput in = view.askAlarmInput(null);
        if (in == null) return;
        String label = cleanLabel(in.label());
        if (label == null) return;
        service.addAlarm(in.time(), label, in.days());
    }

    // ---------- requirement 3, 6: edit ----------
    private void onEditClicked() {
        Alarm selected = view.getSelectedAlarm();
        if (selected == null) { view.showError("Please select an alarm first."); return; }
        AlarmView.AlarmInput in = view.askAlarmInput(selected);
        if (in == null) return;
        String label = cleanLabel(in.label());
        if (label == null) return;
        service.editAlarm(selected.getId(), in.time(), label, in.days());
    }

    // ---------- requirement 4: delete ----------
    private void onDeleteClicked() {
        Alarm selected = view.getSelectedAlarm();
        if (selected == null) { view.showError("Please select an alarm first."); return; }
        service.deleteAlarm(selected.getId());
    }

    // ---------- requirement 5: on / off ----------
    private void onToggleClicked() {
        Alarm selected = view.getSelectedAlarm();
        if (selected == null) { view.showError("Please select an alarm first."); return; }
        service.setEnabled(selected.getId(), !selected.isEnabled());
    }

    // ---------- requirement 9: snooze ----------
    private void onSnoozeClicked(Alarm rung) {
        service.snooze(rung, LocalTime.now(), SNOOZE_MINUTES);
    }

    /** Blank label becomes "Alarm"; too long label shows an error and returns null. */
    private String cleanLabel(String raw) {
        String label = raw == null ? "" : raw.trim();
        if (label.isEmpty()) return "Alarm";
        if (label.length() > Alarm.MAX_LABEL_LENGTH) {
            view.showError("Label must be at most " + Alarm.MAX_LABEL_LENGTH + " characters.");
            return null;
        }
        return label;
    }
}
