# Alarm Clock (SE 4111 Course Project)

Java + Swing desktop app. MVC + Observer + Singleton, with an immutable ADT (Alarm).

## Structure
- src/model/Alarm.java            immutable ADT (RI, AF, checkRep, equals/hashCode)
- src/model/AlarmObserver.java    Observer interface
- src/model/AlarmService.java     Singleton + Subject (all alarm logic)
- src/view/AlarmView.java         Swing window (concrete Observer)
- src/view/Chime.java                  soft bell melody for the ringing alarm (synthesized, no audio files)
- src/controller/AlarmController.java  user actions + 1-second timer
- src/Main.java                   starts the app
- test/ModelTest.java             simple tests (no JUnit needed)

## Run (Java 17 or newer)
    javac -d out $(find src -name "*.java")      # Windows: javac -d out src\*.java src\model\*.java src\view\*.java src\controller\*.java
    java -cp out Main

## Run the tests
    javac -d out $(find src test -name "*.java")
    java -cp out ModelTest

## Interface (animated edition)
All presentation code is inside `src/view/AlarmView.java` (plain Swing + Java2D, no external
libraries, no downloaded fonts). The model, controller and their public interfaces are unchanged.

- Sky panel whose colours follow the real time of day (night, dawn, day, dusk) with a travelling sun/moon and twinkling stars.
- Analog clock with a smooth sweeping second hand; enabled alarms appear as markers on the dial and the next one pulses.
- "Next alarm in ..." countdown computed in the view from the alarms it receives.
- Alarm cards that slide in, glide when another card is deleted, lift on hover, and have animated ON/OFF switches.
- Add/Edit dialog with number steppers, live "Rings in ..." preview, animated day chips and quick presets (Sun - Thu, Weekend ...).
- Ringing dialog with pulsing rings, a shaking bell, and a repeating beep until you Dismiss or Snooze.
- Keyboard: Up/Down select a card, Enter edits, Delete removes.
