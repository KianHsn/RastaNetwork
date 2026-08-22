package routing_game.Model;

import java.awt.Point;

public final class NetworkNode {
    private final int id;
    private final String label;
    private final Point position;

    public NetworkNode(int id, String label, Point position) {
        this.id = id;
        this.label = label;
        this.position = position;
    }

    public int id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Point position() {
        return position;
    }

    @Override
    public String toString() {
        return label;
    }
}