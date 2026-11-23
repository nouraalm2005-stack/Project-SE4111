import controller.AlarmController;
import model.AlarmService;
import view.AlarmView;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AlarmController controller = new AlarmController(AlarmService.getInstance(), new AlarmView());
            controller.start();
        });
    }
}
