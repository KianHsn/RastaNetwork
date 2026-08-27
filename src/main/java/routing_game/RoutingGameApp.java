package routing_game;

import routing_game.View.GameTheme;
import routing_game.View.RoutingGameFrame;

import javax.swing.SwingUtilities;

public final class RoutingGameApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameTheme.apply();
            RoutingGameFrame frame = new RoutingGameFrame();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
