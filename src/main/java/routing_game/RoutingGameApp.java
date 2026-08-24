package routing_game;

import routing_game.View.RoutingGameFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class RoutingGameApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            RoutingGameFrame frame = new RoutingGameFrame();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}